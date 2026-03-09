package ai.fixitbuddy.app.core.websocket

import android.util.Base64
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

sealed class AgentMessage {
    data class Transcript(val text: String, val isFinal: Boolean) : AgentMessage()
    data class Audio(val data: ByteArray) : AgentMessage() {
        override fun equals(other: Any?) = other is Audio && data.contentEquals(other.data)
        override fun hashCode() = data.contentHashCode()
    }
    data class Status(val state: String) : AgentMessage()
    data class ToolCall(val toolName: String, val args: String) : AgentMessage()
}

@Serializable
private data class TextMessage(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val isFinal: Boolean? = null,
    val state: String? = null,
    val toolName: String? = null,
    val args: String? = null
)

@Serializable
private data class AgentInput(
    val type: String,
    val data: String
)

private val json = Json { ignoreUnknownKeys = true }

@Singleton
class AgentWebSocket @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null

    private val _incomingMessages = MutableSharedFlow<AgentMessage>(extraBufferCapacity = 10)
    val incomingMessages: SharedFlow<AgentMessage> = _incomingMessages

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    fun connect(url: String) {
        disconnect()  // Clean up any existing connection

        val request = Request.Builder().url(url).build()
        _connectionState.value = ConnectionState.CONNECTING

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = json.decodeFromString<TextMessage>(text)
                    when (msg.type) {
                        "transcript" -> {
                            _incomingMessages.tryEmit(
                                AgentMessage.Transcript(
                                    text = msg.text ?: "",
                                    isFinal = msg.isFinal ?: false
                                )
                            )
                        }
                        "status" -> {
                            _incomingMessages.tryEmit(
                                AgentMessage.Status(state = msg.state ?: "unknown")
                            )
                        }
                        "tool_call" -> {
                            _incomingMessages.tryEmit(
                                AgentMessage.ToolCall(
                                    toolName = msg.toolName ?: "",
                                    args = msg.args ?: ""
                                )
                            )
                        }
                        "audio" -> {
                            // Base64-encoded audio in JSON
                            msg.data?.let { b64 ->
                                val audioBytes = Base64.decode(b64, Base64.NO_WRAP)
                                _incomingMessages.tryEmit(AgentMessage.Audio(audioBytes))
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Malformed message — skip
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Binary audio data from agent
                _incomingMessages.tryEmit(AgentMessage.Audio(bytes.toByteArray()))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.ERROR
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }

    fun sendVideoFrame(frameData: ByteArray) {
        try {
            val b64 = Base64.encodeToString(frameData, Base64.NO_WRAP)
            val message = json.encodeToString(
                AgentInput.serializer(),
                AgentInput(type = "video", data = b64)
            )
            webSocket?.send(message)
        } catch (_: Exception) {
            // Send error — skip frame
        }
    }

    fun sendAudioChunk(audioData: ByteArray) {
        try {
            webSocket?.send(ByteString.of(*audioData))
        } catch (_: Exception) {
            // Send error — skip chunk
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {
            // Already closed
        }
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    val isConnected: Boolean
        get() = _connectionState.value == ConnectionState.CONNECTED
}
