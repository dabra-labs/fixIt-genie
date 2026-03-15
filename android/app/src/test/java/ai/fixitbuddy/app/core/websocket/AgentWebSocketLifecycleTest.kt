package ai.fixitbuddy.app.core.websocket

import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentWebSocketLifecycleTest {

    private fun response(): Response = Response.Builder()
        .request(Request.Builder().url("wss://example.com/run_live").build())
        .protocol(Protocol.HTTP_1_1)
        .code(101)
        .message("Switching Protocols")
        .build()

    @Test
    fun `stale failure from previous socket does not break current connection`() {
        val okHttpClient = mockk<OkHttpClient>()
        val firstSocket = mockk<WebSocket>(relaxed = true)
        val secondSocket = mockk<WebSocket>(relaxed = true)
        val listeners = mutableListOf<WebSocketListener>()

        every { okHttpClient.newWebSocket(any(), any()) } answers {
            listeners += secondArg<WebSocketListener>()
            if (listeners.size == 1) firstSocket else secondSocket
        }

        val socket = AgentWebSocket(okHttpClient)

        socket.connect("wss://example.com/run_live?session=one")
        listeners[0].onOpen(firstSocket, response())
        assertEquals(ConnectionState.CONNECTED, socket.connectionState.value)

        socket.connect("wss://example.com/run_live?session=two")
        listeners[1].onOpen(secondSocket, response())
        assertEquals(ConnectionState.CONNECTED, socket.connectionState.value)

        listeners[0].onFailure(firstSocket, RuntimeException("stale"), null)

        assertEquals(ConnectionState.CONNECTED, socket.connectionState.value)
    }

    @Test
    fun `stale close callbacks from previous socket are ignored`() {
        val okHttpClient = mockk<OkHttpClient>()
        val firstSocket = mockk<WebSocket>(relaxed = true)
        val secondSocket = mockk<WebSocket>(relaxed = true)
        val listeners = mutableListOf<WebSocketListener>()

        every { okHttpClient.newWebSocket(any(), any()) } answers {
            listeners += secondArg<WebSocketListener>()
            if (listeners.size == 1) firstSocket else secondSocket
        }

        val socket = AgentWebSocket(okHttpClient)

        socket.connect("wss://example.com/run_live?session=one")
        listeners[0].onOpen(firstSocket, response())

        socket.connect("wss://example.com/run_live?session=two")
        listeners[1].onOpen(secondSocket, response())
        assertEquals(ConnectionState.CONNECTED, socket.connectionState.value)

        listeners[0].onClosing(firstSocket, 1000, "old")
        listeners[0].onClosed(firstSocket, 1000, "old")

        assertEquals(ConnectionState.CONNECTED, socket.connectionState.value)
    }
}
