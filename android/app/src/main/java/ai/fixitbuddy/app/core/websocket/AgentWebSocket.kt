package ai.fixitbuddy.app.core.websocket

import android.util.Base64
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
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

// ADK LiveRequest format — sent from client to server
@Serializable
private data class LiveBlob(
    @SerialName("mime_type") val mimeType: String,
    val data: String  // base64 encoded
)

@Serializable
private data class LiveRequest(
    val blob: LiveBlob? = null
)

private val json = Json { ignoreUnknownKeys = true }

@Singleton
class AgentWebSocket @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    val sessionId: String = UUID.randomUUID().toString()
    val userId: String = "fixitbuddy_user"

    private val _incomingMessages = MutableSharedFlow<AgentMessage>(extraBufferCapacity = 10)
    val incomingMessages: SharedFlow<AgentMessage> = _incomingMessages

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    /**
     * Connects to the ADK /run_live WebSocket endpoint.
     *
     * URL format:
     *   ws(s)://host/run_live?app_name=fixitbuddy&user_id={userId}&session_id={sessionId}&modalities=AUDIO
     *
     * SessionViewModel is responsible for creating the session via REST before calling connect().
     */
    fun connect(url: String) {
        disconnect()  // Clean up any existing connection

        val request = Request.Builder().url(url).build()
        _connectionState.value = ConnectionState.CONNECTING

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseAdkEvent(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // ADK sends JSON text frames only
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

    /**
     * Parses incoming ADK Event JSON (camelCase fields, ser_json_bytes='base64').
     *
     * Structure:
     * {
     *   "content": {
     *     "role": "model",
     *     "parts": [
     *       {"text": "..."},
     *       {"inlineData": {"mimeType": "audio/pcm", "data": "<base64>"}},
     *       {"functionCall": {"name": "...", "args": {}}}
     *     ]
     *   },
     *   "partial": true,
     *   "author": "fixitbuddy"
     * }
     */
    private fun parseAdkEvent(text: String) {
        try {
            val obj = json.parseToJsonElement(text).jsonObject
            val content = obj["content"]?.jsonObject ?: return
            val parts = content["parts"]?.jsonArray ?: return
            val isPartial = obj["partial"]?.jsonPrimitive?.content == "true"

            for (part in parts) {
                val partObj = part.jsonObject

                // Text response
                partObj["text"]?.jsonPrimitive?.content?.let { txt ->
                    if (txt.isNotBlank()) {
                        _incomingMessages.tryEmit(
                            AgentMessage.Transcript(txt, isFinal = !isPartial)
                        )
                    }
                }

                // Audio response (base64 PCM in inlineData)
                partObj["inlineData"]?.jsonObject?.let { inlineData ->
                    val mimeType = inlineData["mimeType"]?.jsonPrimitive?.content ?: ""
                    val data = inlineData["data"]?.jsonPrimitive?.content ?: ""
                    if (mimeType.startsWith("audio/") && data.isNotEmpty()) {
                        val audioBytes = Base64.decode(data, Base64.NO_WRAP)
                        _incomingMessages.tryEmit(AgentMessage.Audio(audioBytes))
                    }
                }

                // Tool call
                partObj["functionCall"]?.jsonObject?.let { fnCall ->
                    val name = fnCall["name"]?.jsonPrimitive?.content ?: ""
                    val args = fnCall["args"]?.toString() ?: "{}"
                    if (name.isNotEmpty()) {
                        _incomingMessages.tryEmit(AgentMessage.ToolCall(name, args))
                    }
                }
            }

            // Emit speaking/listening status based on partial flag
            val author = obj["author"]?.jsonPrimitive?.content ?: ""
            if (author == "fixitbuddy") {
                _incomingMessages.tryEmit(
                    AgentMessage.Status(if (isPartial) "speaking" else "listening")
                )
            }
        } catch (_: Exception) {
            // Malformed event — skip
        }
    }

    /**
     * Sends a JPEG video frame using ADK LiveRequest blob format.
     * {"blob": {"mime_type": "image/jpeg", "data": "<base64>"}}
     */
    fun sendVideoFrame(frameData: ByteArray) {
        try {
            val b64 = Base64.encodeToString(frameData, Base64.NO_WRAP)
            val request = LiveRequest(blob = LiveBlob(mimeType = "image/jpeg", data = b64))
            webSocket?.send(json.encodeToString(LiveRequest.serializer(), request))
        } catch (_: Exception) {
            // Skip frame on error
        }
    }

    /**
     * Sends a PCM audio chunk using ADK LiveRequest blob format.
     * {"blob": {"mime_type": "audio/pcm;rate=16000", "data": "<base64>"}}
     */
    fun sendAudioChunk(audioData: ByteArray) {
        try {
            val b64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
            val request = LiveRequest(blob = LiveBlob(mimeType = "audio/pcm;rate=16000", data = b64))
            webSocket?.send(json.encodeToString(LiveRequest.serializer(), request))
        } catch (_: Exception) {
            // Skip chunk on error
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
