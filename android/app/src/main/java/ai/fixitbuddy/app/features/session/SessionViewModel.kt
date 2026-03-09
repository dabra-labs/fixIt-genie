package ai.fixitbuddy.app.features.session

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.fixitbuddy.app.core.audio.AudioStreamManager
import ai.fixitbuddy.app.core.camera.CameraManager
import ai.fixitbuddy.app.core.config.AppConfig
import ai.fixitbuddy.app.core.websocket.AgentMessage
import ai.fixitbuddy.app.core.websocket.AgentWebSocket
import ai.fixitbuddy.app.core.websocket.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionUiState(
    val sessionState: SessionState = SessionState.Idle,
    val transcript: String = "",
    val agentState: String = "idle",
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
    private val webSocket: AgentWebSocket
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
                        // Tool calls are handled server-side; we just display them
                        _uiState.update {
                            it.copy(agentState = "using ${message.toolName}")
                        }
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startSession() {
        val wsUrl = AppConfig.WS_URL
        webSocket.connect(wsUrl)
        audioManager.initPlayback()
        audioManager.startRecording()

        // Forward camera frames to backend
        viewModelScope.launch(Dispatchers.IO) {
            cameraManager.frames.collect { frame ->
                webSocket.sendVideoFrame(frame)
            }
        }

        // Forward audio chunks to backend
        viewModelScope.launch(Dispatchers.IO) {
            audioManager.audioChunks.collect { chunk ->
                webSocket.sendAudioChunk(chunk)
            }
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
