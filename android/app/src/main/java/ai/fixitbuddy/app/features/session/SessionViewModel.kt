package ai.fixitbuddy.app.features.session

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.fixitbuddy.app.core.audio.AudioStreamManager
import ai.fixitbuddy.app.core.camera.CameraManager
import ai.fixitbuddy.app.core.config.AppConfig
import ai.fixitbuddy.app.core.websocket.AgentMessage
import ai.fixitbuddy.app.core.websocket.AgentWebSocket
import ai.fixitbuddy.app.core.websocket.ConnectionState
import ai.fixitbuddy.app.features.settings.SettingsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

data class SessionUiState(
    val sessionState: SessionState = SessionState.Idle,
    val transcript: String = "",
    val agentState: String = "idle",
    val lastToolCall: String? = null,
    val toolCallCount: Int = 0,
    val isTorchOn: Boolean = false,
    val hasTorch: Boolean = false,
    val errorMessage: String? = null
)

enum class SessionState {
    Idle, Connecting, Active, Error
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    val cameraManager: CameraManager,
    private val audioManager: AudioStreamManager,
    private val webSocket: AgentWebSocket,
    private val dataStore: DataStore<Preferences>,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState

    init {
        observeConnectionState()
        observeIncomingMessages()
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
                            errorMessage = null
                        )
                        ConnectionState.ERROR -> current.copy(
                            sessionState = SessionState.Error,
                            errorMessage = "Connection failed. Check your network and backend URL."
                        )
                        ConnectionState.DISCONNECTED -> {
                            if (current.sessionState != SessionState.Idle) {
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
                        audioManager.playAudioChunk(message.data)
                    }
                    is AgentMessage.Transcript -> {
                        _uiState.update { it.copy(transcript = message.text) }
                    }
                    is AgentMessage.Status -> {
                        _uiState.update { it.copy(agentState = message.state) }
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

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startSession() {
        viewModelScope.launch(Dispatchers.IO) {
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

            // Step 4: Forward camera frames
            launch {
                cameraManager.frames.collect { frame ->
                    webSocket.sendVideoFrame(frame)
                }
            }

            // Step 5: Forward audio chunks
            launch {
                audioManager.audioChunks.collect { chunk ->
                    webSocket.sendAudioChunk(chunk)
                }
            }
        }
        audioManager.initPlayback()
        audioManager.startRecording()
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
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                // Parse {"id":"..."} from response
                val match = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)
                match?.groupValues?.get(1)
            } catch (_: Exception) {
                null
            }
        }

    fun stopSession() {
        audioManager.stopRecording()
        audioManager.stopPlayback()
        webSocket.disconnect()
        _uiState.update {
            it.copy(
                sessionState = SessionState.Idle,
                transcript = "",
                agentState = "idle",
                lastToolCall = null,
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
}
