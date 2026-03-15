package ai.fixitbuddy.app.features.session

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.fixitbuddy.app.core.audio.AudioStreamManager
import ai.fixitbuddy.app.core.camera.CameraManager
import ai.fixitbuddy.app.core.camera.GlassesCameraManager
import ai.fixitbuddy.app.core.camera.GlassesState
import ai.fixitbuddy.app.core.config.AppConfig
import ai.fixitbuddy.app.core.websocket.AgentMessage
import ai.fixitbuddy.app.core.websocket.AgentWebSocket
import ai.fixitbuddy.app.core.websocket.ConnectionState
import ai.fixitbuddy.app.core.websocket.TranscriptSpeaker
import ai.fixitbuddy.app.features.history.SessionHistoryStore
import ai.fixitbuddy.app.features.history.SessionRecord
import ai.fixitbuddy.app.features.settings.SettingsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** A single spoken exchange in the session conversation. */
data class ChatTurn(val role: ChatRole, val text: String)
enum class ChatRole { USER, GENIE }

/** Immutable snapshot of session UI state, consumed by [SessionScreen]. */
data class SessionUiState(
    val sessionState: SessionState = SessionState.Idle,
    val transcript: String = "",
    val chatTurns: List<ChatTurn> = emptyList(),
    val agentState: String = "idle",
    val lastToolCall: String? = null,
    val toolCallCount: Int = 0,
    val isTorchOn: Boolean = false,
    val hasTorch: Boolean = false,
    val errorMessage: String? = null,
    val cameraSource: CameraSource = CameraSource.PHONE,
    val glassesState: GlassesState = GlassesState.DISCONNECTED
)

/** High-level session lifecycle states. */
enum class SessionState {
    Idle, Connecting, Active, Error
}

/** Which camera feed is sent to the agent. */
enum class CameraSource { PHONE, GLASSES }

@HiltViewModel
class SessionViewModel @Inject constructor(
    val cameraManager: CameraManager,
    val glassesCameraManager: GlassesCameraManager,
    private val audioManager: AudioStreamManager,
    private val webSocket: AgentWebSocket,
    private val dataStore: DataStore<Preferences>,
    private val okHttpClient: OkHttpClient,
    private val historyStore: SessionHistoryStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState

    /** Normalised mic level 0f..1f — exposed separately to avoid recomposing entire UI. */
    val audioLevel: StateFlow<Float> = audioManager.audioLevel

    /** Track session start time for duration calculation */
    private var sessionStartMs: Long = 0L

    /** True while accumulating the current genie turn (speaking start → next listening). */
    @Volatile private var genieIsSpeaking = false

    /**
     * True while the agent is speaking (playing audio chunks).
     * Mic audio is suppressed during this window + 400ms after the last chunk
     * to prevent AEC tail-end leakage from triggering a second agent response.
     */
    @Volatile private var agentSpeaking = false

    /**
     * Fallback timer: if no audio chunk arrives for 1200ms, assume turn is complete
     * and open the mic gate. Under normal operation the server always sends turnComplete,
     * which triggers Status("listening") and cancels this timer before it fires.
     */
    private var turnEndJob: Job? = null

    /** Jobs for streaming forwarding — cancelled on stopSession() */
    private var sessionJob: Job? = null
    private var frameForwardingJob: Job? = null

    init {
        observeConnectionState()
        observeIncomingMessages()
        observeGlassesState()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            webSocket.connectionState.collect { state ->
                _uiState.update { current ->
                    when (state) {
                        ConnectionState.CONNECTING -> current.copy(
                            sessionState = SessionState.Connecting,
                            errorMessage = null
                        )
                        ConnectionState.CONNECTED -> current.copy(
                            sessionState = SessionState.Active,
                            agentState = "listening",
                            errorMessage = null,
                            chatTurns = emptyList()
                        )
                        ConnectionState.ERROR -> {
                            genieIsSpeaking = false
                            current.copy(
                                sessionState = SessionState.Error,
                                errorMessage = "Connection failed. Check your network and backend URL."
                            )
                        }
                        ConnectionState.DISCONNECTED -> {
                            if (current.sessionState != SessionState.Idle) {
                                genieIsSpeaking = false
                                current.copy(
                                    sessionState = SessionState.Idle,
                                    errorMessage = null
                                )
                            } else {
                                current
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeIncomingMessages() {
        viewModelScope.launch {
            webSocket.incomingMessages.collect { message ->
                when (message) {
                    is AgentMessage.Audio -> {
                        agentSpeaking = true
                        audioManager.playAudioChunk(message.data)
                        // Reset turn-end timer: open mic 1200ms after the last audio chunk
                        turnEndJob?.cancel()
                        turnEndJob = launch {
                            delay(1200)
                            agentSpeaking = false
                        }
                    }
                    is AgentMessage.Transcript -> {
                        if (message.speaker == TranscriptSpeaker.GENIE) {
                            val text = message.text
                            _uiState.update { current ->
                                val turns = current.chatTurns.toMutableList()
                                if (!genieIsSpeaking || turns.isEmpty() || turns.last().role != ChatRole.GENIE) {
                                    genieIsSpeaking = true
                                    turns.add(ChatTurn(ChatRole.GENIE, text))
                                } else {
                                    turns[turns.lastIndex] = ChatTurn(ChatRole.GENIE, text)
                                }
                                current.copy(
                                    transcript = text,
                                    chatTurns = turns,
                                    agentState = if (current.agentState == "thinking") "speaking" else current.agentState
                                )
                            }
                        } else {
                            _uiState.update { current ->
                                val turns = current.chatTurns.toMutableList()
                                if (turns.isEmpty() || turns.last().role != ChatRole.USER) {
                                    turns.add(ChatTurn(ChatRole.USER, message.text))
                                } else {
                                    turns[turns.lastIndex] = ChatTurn(ChatRole.USER, message.text)
                                }
                                current.copy(
                                    chatTurns = turns,
                                    agentState = if (message.isFinal) "thinking" else current.agentState
                                )
                            }
                        }
                    }
                    is AgentMessage.Status -> {
                        val state = message.state
                        _uiState.update { current ->
                            if (state == "listening" && genieIsSpeaking) {
                                genieIsSpeaking = false
                            }
                            current.copy(agentState = state)
                        }
                        if (state == "listening") {
                            // Cancel the 1200ms fallback timer — we got the real turn-end signal.
                            // Hold mic mute for 400ms so AEC tail settles before unmuting.
                            turnEndJob?.cancel()
                            launch {
                                delay(400)
                                agentSpeaking = false
                            }
                        }
                    }
                    is AgentMessage.Interrupted -> {
                        turnEndJob?.cancel()
                        audioManager.interrupt()
                        agentSpeaking = false
                    }
                    is AgentMessage.ToolCall -> {
                        // Tool calls are handled server-side; we display them via chip + status
                        // Increment toolCallCount so the chip retriggers even for repeated tool names
                        _uiState.update {
                            it.copy(
                                lastToolCall = message.toolName,
                                toolCallCount = it.toolCallCount + 1,
                                agentState = "using ${message.toolName}"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observeGlassesState() {
        viewModelScope.launch {
            glassesCameraManager.connectionState.collect { state ->
                Log.d(TAG, "Glasses state → $state")
                _uiState.update { current ->
                    val errorMessage = if (state == GlassesState.ERROR)
                        "Glasses unavailable — SDK failed to initialize. Restart the app."
                    else
                        current.errorMessage
                    current.copy(glassesState = state, errorMessage = errorMessage)
                }
            }
        }
    }

    fun switchCameraSource(source: CameraSource) {
        val sessionActive = sessionJob != null
        Log.i(TAG, "Switching camera source → $source (sessionActive=$sessionActive)")
        _uiState.update { it.copy(cameraSource = source) }
        if (source == CameraSource.GLASSES) {
            glassesCameraManager.startStream()
        } else {
            glassesCameraManager.stopStream()
        }
        // If a session is running, redirect the frame collection coroutine immediately
        if (sessionActive) {
            Log.d(TAG, "Session active — restarting frame forwarding for $source")
            startFrameForwarding(source)
        }
    }

    private fun startFrameForwarding(source: CameraSource) {
        frameForwardingJob?.cancel()
        Log.d(TAG, "Starting frame forwarding — source=$source")
        frameForwardingJob = viewModelScope.launch(Dispatchers.IO) {
            val frameFlow = if (source == CameraSource.GLASSES)
                glassesCameraManager.frames
            else
                cameraManager.frames
            try {
                frameFlow.collect { frame -> webSocket.sendVideoFrame(frame) }
            } catch (e: CancellationException) {
                throw e  // normal job cancellation — propagate
            } catch (e: Exception) {
                Log.e(TAG, "Frame forwarding stopped unexpectedly (source=$source)", e)
                frameForwardingJob = null
                _uiState.update { it.copy(errorMessage = "Camera feed interrupted — agent can no longer see your camera.") }
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startSession() {
        sessionStartMs = System.currentTimeMillis()
        sessionJob = viewModelScope.launch(Dispatchers.IO) {
            val baseUrl = dataStore.data.map { prefs ->
                prefs[SettingsViewModel.BACKEND_URL_KEY] ?: AppConfig.BACKEND_URL
            }.first()

            // Step 1: Create ADK session via REST
            val sessionId = createAdkSession(baseUrl, webSocket.userId)
            if (sessionId == null) {
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.Error,
                        errorMessage = "Connection failed. Check your network and backend URL."
                    )
                }
                return@launch
            }

            // Step 2: Build WebSocket URL with required ADK query params
            val wsBase = baseUrl
                .replace("https://", "wss://")
                .replace("http://", "ws://")
            val wsUrl = "$wsBase/run_live" +
                "?app_name=fixitbuddy" +
                "&user_id=${webSocket.userId}" +
                "&session_id=$sessionId" +
                "&modalities=AUDIO"

            // Step 3: Connect WebSocket
            webSocket.connect(wsUrl)

            // Step 4: Init audio — done here (inside coroutine, after session is confirmed)
            // so the mic is never left running if createAdkSession() failed above.
            withContext(Dispatchers.Main) { audioManager.initPlayback() }
            audioManager.startRecording()

            // Step 5: Forward camera frames — restartable via switchCameraSource()
            startFrameForwarding(_uiState.value.cameraSource)

            // Step 6: Forward audio chunks continuously — Gemini's native audio model
            // has built-in VAD, but we still drop low-level mic leakage while the
            // agent is speaking so nearby user speech can interrupt without feeding
            // speaker echo straight back into the model.
            launch {
                audioManager.audioChunks.collect { chunk ->
                    if (shouldForwardMicChunk(chunk, agentSpeaking)) {
                        webSocket.sendAudioChunk(chunk)
                    }
                }
            }
        }
    }

    /**
     * Creates an ADK session via REST before connecting the WebSocket.
     * POST {baseUrl}/apps/fixitbuddy/users/{userId}/sessions
     * Returns the session ID on success, null on failure.
     */
    private suspend fun createAdkSession(baseUrl: String, userId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/apps/fixitbuddy/users/$userId/sessions"
                val request = Request.Builder()
                    .url(url)
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "ADK session creation failed: HTTP ${response.code}")
                        return@withContext null
                    }
                    val body = response.body?.string() ?: run {
                        Log.w(TAG, "ADK session creation failed: empty response body")
                        return@withContext null
                    }
                    // Parse {"id":"..."} from response
                    val match = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)
                    match?.groupValues?.get(1)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create ADK session", e)
                null
            }
        }

    fun stopSession() {
        // Cancel streaming forwarding jobs
        frameForwardingJob?.cancel()
        frameForwardingJob = null
        sessionJob?.cancel()
        sessionJob = null
        glassesCameraManager.stopStream()

        // Save session to history if it was active
        val current = _uiState.value
        if (current.sessionState == SessionState.Active && sessionStartMs > 0) {
            val durationSec = ((System.currentTimeMillis() - sessionStartMs) / 1000).toInt()
            val snippet = current.transcript.take(120).ifBlank { "Voice-only session" }
            viewModelScope.launch {
                historyStore.addSession(
                    SessionRecord(
                        timestampMs = sessionStartMs,
                        durationSec = durationSec,
                        transcriptSnippet = snippet,
                        toolCallCount = current.toolCallCount
                    )
                )
            }
        }
        sessionStartMs = 0L
        agentSpeaking = false
        genieIsSpeaking = false

        audioManager.stopRecording()
        audioManager.stopPlayback()
        webSocket.disconnect()
        _uiState.update {
            it.copy(
                sessionState = SessionState.Idle,
                transcript = "",
                chatTurns = emptyList(),
                agentState = "idle",
                lastToolCall = null,
                toolCallCount = 0,
                errorMessage = null
            )
        }
    }

    fun toggleFlashlight() {
        val isOn = cameraManager.toggleTorch()
        _uiState.update { it.copy(isTorchOn = isOn) }
    }

    fun updateTorchAvailability() {
        _uiState.update { it.copy(hasTorch = cameraManager.hasTorch) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        stopSession()
        cameraManager.stopCamera()
        super.onCleared()
    }

    companion object {
        private const val TAG = "SessionViewModel"
    }
}

internal fun shouldForwardMicChunk(chunk: ByteArray, agentSpeaking: Boolean): Boolean {
    if (!agentSpeaking) return true
    return normalizedPcmLevel(chunk) >= AppConfig.AUDIO_BARGE_IN_LEVEL
}

internal fun normalizedPcmLevel(chunk: ByteArray): Float {
    if (chunk.size < 2) return 0f
    val shortBuffer = ByteBuffer.wrap(chunk)
        .order(ByteOrder.LITTLE_ENDIAN)
        .asShortBuffer()
    val sampleCount = shortBuffer.remaining()
    if (sampleCount == 0) return 0f

    var sumSquares = 0.0
    for (i in 0 until sampleCount) {
        val sample = shortBuffer[i].toDouble()
        sumSquares += sample * sample
    }
    val rms = sqrt(sumSquares / sampleCount)
    val db = if (rms > 1.0) 20.0 * Math.log10(rms / 32768.0) else -96.0
    return ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
}
