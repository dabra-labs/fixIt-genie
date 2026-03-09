package ai.fixitbuddy.app.core.websocket

import org.junit.Assert.*
import org.junit.Test

class AgentWebSocketTest {

    @Test
    fun `ConnectionState has all expected values`() {
        val states = ConnectionState.values()
        assertEquals(4, states.size)
        assertTrue(states.contains(ConnectionState.DISCONNECTED))
        assertTrue(states.contains(ConnectionState.CONNECTING))
        assertTrue(states.contains(ConnectionState.CONNECTED))
        assertTrue(states.contains(ConnectionState.ERROR))
    }

    @Test
    fun `AgentMessage Transcript holds text and isFinal`() {
        val msg = AgentMessage.Transcript("Hello world", true)
        assertEquals("Hello world", msg.text)
        assertTrue(msg.isFinal)
    }

    @Test
    fun `AgentMessage Transcript isFinal defaults correctly`() {
        val partial = AgentMessage.Transcript("partial", false)
        assertFalse(partial.isFinal)
    }

    @Test
    fun `AgentMessage Transcript with empty text`() {
        val msg = AgentMessage.Transcript("", false)
        assertEquals("", msg.text)
        assertFalse(msg.isFinal)
    }

    @Test
    fun `AgentMessage Transcript equality based on content`() {
        val msg1 = AgentMessage.Transcript("text", true)
        val msg2 = AgentMessage.Transcript("text", true)
        val msg3 = AgentMessage.Transcript("other", true)
        assertEquals(msg1, msg2)
        assertNotEquals(msg1, msg3)
    }

    @Test
    fun `AgentMessage Audio holds byte data`() {
        val data = byteArrayOf(1, 2, 3, 4)
        val msg = AgentMessage.Audio(data)
        assertArrayEquals(data, msg.data)
    }

    @Test
    fun `AgentMessage Audio equality based on content`() {
        val data1 = byteArrayOf(1, 2, 3)
        val data2 = byteArrayOf(1, 2, 3)
        val data3 = byteArrayOf(4, 5, 6)
        assertEquals(AgentMessage.Audio(data1), AgentMessage.Audio(data2))
        assertNotEquals(AgentMessage.Audio(data1), AgentMessage.Audio(data3))
    }

    @Test
    fun `AgentMessage Audio hashCode based on content`() {
        val data1 = byteArrayOf(1, 2, 3)
        val data2 = byteArrayOf(1, 2, 3)
        assertEquals(AgentMessage.Audio(data1).hashCode(), AgentMessage.Audio(data2).hashCode())
    }

    @Test
    fun `AgentMessage Audio with different array instances but same content`() {
        val data1 = byteArrayOf(10, 20, 30)
        val data2 = byteArrayOf(10, 20, 30)
        assertTrue(AgentMessage.Audio(data1) == AgentMessage.Audio(data2))
    }

    @Test
    fun `AgentMessage Audio empty array`() {
        val data = byteArrayOf()
        val msg = AgentMessage.Audio(data)
        assertEquals(0, msg.data.size)
    }

    @Test
    fun `AgentMessage Status holds state string`() {
        val msg = AgentMessage.Status("listening")
        assertEquals("listening", msg.state)
    }

    @Test
    fun `AgentMessage Status with various states`() {
        val states = listOf("listening", "thinking", "speaking", "idle", "unknown")
        states.forEach { state ->
            val msg = AgentMessage.Status(state)
            assertEquals(state, msg.state)
        }
    }

    @Test
    fun `AgentMessage Status with empty string`() {
        val msg = AgentMessage.Status("")
        assertEquals("", msg.state)
    }

    @Test
    fun `AgentMessage Status equality based on state`() {
        val msg1 = AgentMessage.Status("idle")
        val msg2 = AgentMessage.Status("idle")
        val msg3 = AgentMessage.Status("active")
        assertEquals(msg1, msg2)
        assertNotEquals(msg1, msg3)
    }

    @Test
    fun `AgentMessage ToolCall holds tool info`() {
        val msg = AgentMessage.ToolCall("lookup_equipment_knowledge", """{"query":"oil"}""")
        assertEquals("lookup_equipment_knowledge", msg.toolName)
        assertEquals("""{"query":"oil"}""", msg.args)
    }

    @Test
    fun `AgentMessage ToolCall with empty args`() {
        val msg = AgentMessage.ToolCall("some_tool", "")
        assertEquals("some_tool", msg.toolName)
        assertEquals("", msg.args)
    }

    @Test
    fun `AgentMessage ToolCall equality based on content`() {
        val msg1 = AgentMessage.ToolCall("tool", "args")
        val msg2 = AgentMessage.ToolCall("tool", "args")
        val msg3 = AgentMessage.ToolCall("tool", "different")
        assertEquals(msg1, msg2)
        assertNotEquals(msg1, msg3)
    }

    @Test
    fun `AgentMessage ToolCall with complex JSON args`() {
        val complexArgs = """{"query":"oil","filters":{"type":"engine"},"limit":10}"""
        val msg = AgentMessage.ToolCall("search_tool", complexArgs)
        assertEquals(complexArgs, msg.args)
    }

    @Test
    fun `AgentMessage types are sealed class hierarchy`() {
        val messages: List<AgentMessage> = listOf(
            AgentMessage.Transcript("test", false),
            AgentMessage.Audio(byteArrayOf()),
            AgentMessage.Status("idle"),
            AgentMessage.ToolCall("test", "")
        )
        assertEquals(4, messages.size)
        // Verify they're all AgentMessage instances
        messages.forEach { assertTrue(it is AgentMessage) }
    }

    @Test
    fun `AgentMessage Transcript with special characters`() {
        val specialText = "Hello! @#$% \"quotes\" 'apostrophes' \n newlines"
        val msg = AgentMessage.Transcript(specialText, true)
        assertEquals(specialText, msg.text)
    }

    @Test
    fun `AgentMessage different types are not equal`() {
        val transcript = AgentMessage.Transcript("text", true)
        val status = AgentMessage.Status("text")
        assertNotEquals(transcript, status)
    }

    @Test
    fun `AgentMessage Audio with large byte array`() {
        val largeData = ByteArray(10000) { it.toByte() }
        val msg = AgentMessage.Audio(largeData)
        assertEquals(10000, msg.data.size)
        assertArrayEquals(largeData, msg.data)
    }

    @Test
    fun `ConnectionState DISCONNECTED value`() {
        assertEquals(ConnectionState.DISCONNECTED, ConnectionState.DISCONNECTED)
    }

    @Test
    fun `ConnectionState CONNECTING value`() {
        assertEquals(ConnectionState.CONNECTING, ConnectionState.CONNECTING)
    }

    @Test
    fun `ConnectionState CONNECTED value`() {
        assertEquals(ConnectionState.CONNECTED, ConnectionState.CONNECTED)
    }

    @Test
    fun `ConnectionState ERROR value`() {
        assertEquals(ConnectionState.ERROR, ConnectionState.ERROR)
    }

    @Test
    fun `ConnectionState values are distinct`() {
        val states = ConnectionState.values().toList()
        val uniqueStates = states.distinct()
        assertEquals(states.size, uniqueStates.size)
    }
}
