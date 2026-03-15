package ai.fixitbuddy.app.core.websocket

import android.util.Log
import java.util.Base64 as JvmBase64
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

/** WebSocket connection lifecycle states. */
enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

/** Sealed hierarchy of messages received from the ADK agent. */
sealed class AgentMessage {
    data class Transcript(val text: String, val isFinal: Boolean) : AgentMessage()
    data class Audio(val data: ByteArray) : AgentMessage() {
        override fun equals(other: Any?) = other is Audio && data.contentEquals(other.data)
        override fun hashCode() = data.contentHashCode()
    }
    data class Status(val state: String) : AgentMessage()
    data class ToolCall(val toolName: String, val args: String) : AgentMessage()
    /** Server interrupted the current response (VAD detected user speaking mid-playback). */
    data object Interrupted : AgentMessage()
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

/**
 * OkHttp WebSocket wrapper that speaks the Google ADK LiveRequest/Event protocol.
 *
 * Sends audio and video blobs to the agent and parses incoming events
 * (text transcripts, audio responses, tool calls) into [AgentMessage] instances
 * emitted via [incomingMessages].
 */
@Singleton
class AgentWebSocket @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    val sessionId: String = UUID.randomUUID().toString()
    val userId: String = "fixitbuddy_user"

    private val _incomingMessages = MutableSharedFlow<AgentMessage>(extraBufferCapacity = 512)
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
                Log.d(TAG, "WebSocket connected")
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseAdkEvent(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // ADK /run_live sends text (JSON) frames only; log unexpected binary frames.
                Log.d(TAG, "Unexpected binary frame (${bytes.size}B) — ignored")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _connectionState.value = ConnectionState.ERROR
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: code=$code reason=$reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: code=$code reason=$reason")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }

    /** Emits a message; logs a warning if the buffer is full and the message is dropped. */
    private fun emit(message: AgentMessage) {
        if (!_incomingMessages.tryEmit(message)) {
            Log.w(TAG, "Incoming message buffer full — dropped: $message")
        }
    }

    /**
     * Parses incoming ADK LiveEvent JSON (camelCase, URL-safe base64 audio data).
     *
     * Key event types (all fields Optional — excluded from JSON when null):
     *
     * Audio chunk:    {"author":"fixitbuddy","content":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"<base64url>"}}]},"turnComplete":false}
     * Turn complete:  {"author":"fixitbuddy","turnComplete":true}
     * Interrupted:    {"author":"fixitbuddy","interrupted":true}
     * Text partial:   {"author":"fixitbuddy","content":{"parts":[{"text":"..."}]},"partial":true}
     *
     * interrupted/turnComplete are JSON booleans; .jsonPrimitive.content == "true" handles both
     * boolean true and string "true" wire representations.
     */
    private fun parseAdkEvent(text: String) {
        try {
            val obj = json.parseToJsonElement(text).jsonObject

            // Interrupt: server cut off current response (VAD detected user speaking)
            if (obj["interrupted"]?.jsonPrimitive?.content == "true") {
                Log.d(TAG, "Server interrupted response — clearing audio queue")
                emit(AgentMessage.Interrupted)
                return
            }

            val author = obj["author"]?.jsonPrimitive?.content ?: ""

            // ADK turn-complete event: {"turnComplete": true} — no content field.
            // This is the definitive end-of-turn signal from the server; open mic gate.
            if (obj["turnComplete"]?.jsonPrimitive?.content == "true") {
                Log.d(TAG, "Turn complete")
                emit(AgentMessage.Status("listening"))
                return
            }

            val content = obj["content"]?.jsonObject ?: return
            val parts = content["parts"]?.jsonArray ?: return
            val isPartial = obj["partial"]?.jsonPrimitive?.content == "true"

            var hasAudio = false
            for (part in parts) {
                val partObj = part.jsonObject

                // Text response
                partObj["text"]?.jsonPrimitive?.content?.let { txt ->
                    if (txt.isNotBlank()) {
                        emit(AgentMessage.Transcript(txt, isFinal = !isPartial))
                    }
                }

                // Audio response (base64 PCM in inlineData)
                partObj["inlineData"]?.jsonObject?.let { inlineData ->
                    val mimeType = inlineData["mimeType"]?.jsonPrimitive?.content ?: ""
                    val data = inlineData["data"]?.jsonPrimitive?.content ?: ""
                    if (mimeType.startsWith("audio/") && data.isNotEmpty()) {
                        val audioBytes = JvmBase64.getUrlDecoder().decode(data)
                        emit(AgentMessage.Audio(audioBytes))
                        hasAudio = true
                    }
                }

                // Tool call
                partObj["functionCall"]?.jsonObject?.let { fnCall ->
                    val name = fnCall["name"]?.jsonPrimitive?.content ?: ""
                    val args = fnCall["args"]?.toString() ?: "{}"
                    if (name.isNotEmpty()) {
                        emit(AgentMessage.ToolCall(name, args))
                    }
                }
            }

            // Emit "speaking" status only for partial text/tool frames, never when audio
            // data is present. The !hasAudio guard is load-bearing: emitting on audio frames
            // would open the mic gate mid-response. isPartial is an additional heuristic to
            // avoid emitting on final non-audio echo frames at the end of a turn.
            if (author == "fixitbuddy" && isPartial && !hasAudio) {
                emit(AgentMessage.Status("speaking"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ADK event: ${text.take(200)}", e)
        }
    }

    /**
     * Sends a JPEG video frame using ADK LiveRequest blob format.
     * {"blob": {"mime_type": "image/jpeg", "data": "<base64>"}}
     */
    fun sendVideoFrame(frameData: ByteArray) {
        val ws = webSocket
        if (ws == null) {
            Log.d(TAG, "sendVideoFrame called but WebSocket is null — frame dropped")
            return
        }
        try {
            val b64 = JvmBase64.getEncoder().encodeToString(frameData)
            val request = LiveRequest(blob = LiveBlob(mimeType = "image/jpeg", data = b64))
            ws.send(json.encodeToString(LiveRequest.serializer(), request))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send video frame", e)
        }
    }

    /**
     * Sends a PCM audio chunk using ADK LiveRequest blob format.
     * {"blob": {"mime_type": "audio/pcm;rate=16000", "data": "<base64>"}}
     */
    fun sendAudioChunk(audioData: ByteArray) {
        val ws = webSocket
        if (ws == null) {
            Log.d(TAG, "sendAudioChunk called but WebSocket is null — chunk dropped")
            return
        }
        try {
            val b64 = JvmBase64.getEncoder().encodeToString(audioData)
            val request = LiveRequest(blob = LiveBlob(mimeType = "audio/pcm;rate=16000", data = b64))
            ws.send(json.encodeToString(LiveRequest.serializer(), request))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send audio chunk", e)
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing WebSocket", e)
        }
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    val isConnected: Boolean
        get() = _connectionState.value == ConnectionState.CONNECTED

    companion object {
        private const val TAG = "AgentWebSocket"
    }
}
