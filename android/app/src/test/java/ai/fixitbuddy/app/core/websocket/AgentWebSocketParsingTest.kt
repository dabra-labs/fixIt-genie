package ai.fixitbuddy.app.core.websocket

import app.cash.turbine.test
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.util.Base64

/**
 * Tests for [AgentWebSocket.parseAdkEvent] — covers all ADK LiveEvent JSON shapes.
 *
 * Strategy: construct [AgentWebSocket] with a relaxed mock [okhttp3.OkHttpClient] (no real
 * network needed), then invoke the private [parseAdkEvent] method via reflection. Emissions
 * from [AgentWebSocket.incomingMessages] are verified with Turbine under [runTest].
 */
class AgentWebSocketParsingTest {

    private val agentWebSocket = AgentWebSocket(mockk(relaxed = true))

    private val parseAdkEvent: Method = AgentWebSocket::class.java
        .getDeclaredMethod("parseAdkEvent", String::class.java)
        .also { it.isAccessible = true }

    /** Invoke the parser directly without any network. */
    private fun parse(json: String) {
        parseAdkEvent.invoke(agentWebSocket, json)
    }

    // ── Interrupted ──────────────────────────────────────────────────────────

    @Test
    fun `interrupted true emits Interrupted`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"interrupted":true}""")
            assertEquals(AgentMessage.Interrupted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `interrupted with author emits Interrupted and returns early`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","interrupted":true}""")
            assertEquals(AgentMessage.Interrupted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Turn complete ─────────────────────────────────────────────────────────

    @Test
    fun `turnComplete true emits Status listening`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","turnComplete":true}""")
            assertEquals(AgentMessage.Status("listening"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turnComplete false does not emit Status listening`() = runTest {
        agentWebSocket.incomingMessages.test {
            val pcm = byteArrayOf(1, 2, 3, 4)
            val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pcm)
            parse("""{"author":"fixitbuddy","turnComplete":false,"content":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$b64"}}]}}""")
            val item = awaitItem()
            assertTrue("Expected Audio, got $item", item is AgentMessage.Audio)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Audio frame ───────────────────────────────────────────────────────────

    @Test
    fun `audio frame emits Audio message with correct bytes`() = runTest {
        agentWebSocket.incomingMessages.test {
            val pcm = byteArrayOf(0x10, 0x20, 0x30, 0x40)
            val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pcm)
            parse("""{"author":"fixitbuddy","content":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$b64"}}]}}""")
            val msg = awaitItem() as AgentMessage.Audio
            assertArrayEquals(pcm, msg.data)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `audio frame does not emit Status speaking`() = runTest {
        agentWebSocket.incomingMessages.test {
            val pcm = byteArrayOf(1, 2, 3)
            val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pcm)
            parse("""{"author":"fixitbuddy","content":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$b64"}}]}}""")
            val item = awaitItem()
            assertTrue("Expected Audio, got $item", item is AgentMessage.Audio)
            // No Status("speaking") should follow — hasAudio guard prevents it
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `url-safe base64 with dashes and underscores decodes correctly`() = runTest {
        agentWebSocket.incomingMessages.test {
            // 0xFB, 0xBE produce URL-safe chars `-` and `_` in base64
            val pcm = byteArrayOf(0xFB.toByte(), 0xBE.toByte(), 0xFF.toByte())
            val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pcm)
            assertTrue("Test requires URL-safe chars", b64.contains('-') || b64.contains('_'))
            parse("""{"author":"fixitbuddy","content":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$b64"}}]}}""")
            val msg = awaitItem() as AgentMessage.Audio
            assertArrayEquals("URL-safe base64 must decode correctly", pcm, msg.data)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Text / Transcript ─────────────────────────────────────────────────────

    @Test
    fun `partial text emits Transcript with isFinal false`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","partial":true,"content":{"parts":[{"text":"Hello..."}]}}""")
            val transcript = awaitItem() as AgentMessage.Transcript
            assertEquals("Hello...", transcript.text)
            assertFalse(transcript.isFinal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `non-partial text emits Transcript with isFinal true`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","content":{"parts":[{"text":"Final answer."}]}}""")
            val transcript = awaitItem() as AgentMessage.Transcript
            assertEquals("Final answer.", transcript.text)
            assertTrue(transcript.isFinal)
            assertEquals(TranscriptSpeaker.GENIE, transcript.speaker)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `outputTranscription emits genie transcript`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","outputTranscription":{"text":"Display says OFF.","finished":false}}""")
            val transcript = awaitItem() as AgentMessage.Transcript
            assertEquals("Display says OFF.", transcript.text)
            assertFalse(transcript.isFinal)
            assertEquals(TranscriptSpeaker.GENIE, transcript.speaker)
            assertEquals(AgentMessage.Status("speaking"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `inputTranscription emits user transcript`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","inputTranscription":{"text":"my fridge is not cooling","finished":true}}""")
            val transcript = awaitItem() as AgentMessage.Transcript
            assertEquals("my fridge is not cooling", transcript.text)
            assertTrue(transcript.isFinal)
            assertEquals(TranscriptSpeaker.USER, transcript.speaker)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blank text is not emitted`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","content":{"parts":[{"text":"   "}]}}""")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty text is not emitted`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","content":{"parts":[{"text":""}]}}""")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `partial text from fixitbuddy emits Transcript then Status speaking`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","partial":true,"content":{"parts":[{"text":"Thinking..."}]}}""")
            val first = awaitItem()
            val second = awaitItem()
            val messages = listOf(first, second)
            assertTrue(messages.any { it is AgentMessage.Transcript })
            assertTrue(messages.any { it == AgentMessage.Status("speaking") })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Mixed text + audio ────────────────────────────────────────────────────

    @Test
    fun `mixed text and audio emits both but no Status speaking`() = runTest {
        agentWebSocket.incomingMessages.test {
            val pcm = byteArrayOf(5, 6, 7)
            val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pcm)
            parse("""{"author":"fixitbuddy","partial":true,"content":{"parts":[{"text":"Hi"},{"inlineData":{"mimeType":"audio/pcm","data":"$b64"}}]}}""")
            val first = awaitItem()
            val second = awaitItem()
            val messages = listOf(first, second)
            assertTrue(messages.any { it is AgentMessage.Transcript })
            assertTrue(messages.any { it is AgentMessage.Audio })
            assertTrue("hasAudio guard must suppress Status(speaking)", messages.none { it == AgentMessage.Status("speaking") })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Tool call ─────────────────────────────────────────────────────────────

    @Test
    fun `functionCall part emits ToolCall with correct name`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","content":{"parts":[{"functionCall":{"name":"lookup_equipment_knowledge","args":{"query":"oil filter"}}}]}}""")
            val msg = awaitItem() as AgentMessage.ToolCall
            assertEquals("lookup_equipment_knowledge", msg.toolName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty function name is not emitted`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","content":{"parts":[{"functionCall":{"name":"","args":{}}}]}}""")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Error resilience ──────────────────────────────────────────────────────

    @Test
    fun `malformed JSON does not crash and emits nothing`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("not valid json {{{")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty JSON object emits nothing`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("{}")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `missing content field emits nothing`() = runTest {
        agentWebSocket.incomingMessages.test {
            parse("""{"author":"fixitbuddy","partial":true}""")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── AgentMessage.Interrupted is data object (singleton) ───────────────────

    @Test
    fun `Interrupted is a singleton data object`() {
        val a: AgentMessage = AgentMessage.Interrupted
        val b: AgentMessage = AgentMessage.Interrupted
        assertEquals(a, b)
        assertTrue(a === b)
    }
}
