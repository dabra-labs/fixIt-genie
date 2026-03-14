package ai.fixitbuddy.app.features.session

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTurnTest {

    /** Simulates the AgentMessage.Transcript branch in observeIncomingMessages. */
    private fun applyTranscript(
        currentTurns: List<ChatTurn>,
        genieIsSpeaking: Boolean,
        text: String
    ): Pair<List<ChatTurn>, Boolean> {
        val turns = currentTurns.toMutableList()
        return if (!genieIsSpeaking || turns.isEmpty() || turns.last().role != ChatRole.GENIE) {
            turns.add(ChatTurn(ChatRole.GENIE, text))
            Pair(turns, true)
        } else {
            turns[turns.lastIndex] = ChatTurn(ChatRole.GENIE, text)
            Pair(turns, true)
        }
    }

    /** Simulates the Status("listening") branch in observeIncomingMessages. */
    private fun applyListening(
        currentTurns: List<ChatTurn>,
        genieIsSpeaking: Boolean
    ): Pair<List<ChatTurn>, Boolean> {
        val turns = currentTurns.toMutableList()
        return if (genieIsSpeaking) {
            turns.add(ChatTurn(ChatRole.USER, ""))
            Pair(turns, false)
        } else {
            Pair(turns, false)
        }
    }

    @Test
    fun `streaming transcript updates last genie turn`() {
        val (turns1, speaking1) = applyTranscript(emptyList(), false, "Hello")
        val (turns2, _) = applyTranscript(turns1, speaking1, "Hello world")
        assertEquals(1, turns2.size)
        assertEquals("Hello world", turns2[0].text)
        assertEquals(ChatRole.GENIE, turns2[0].role)
    }

    @Test
    fun `listening transition adds user placeholder`() {
        val (turns1, speaking1) = applyTranscript(emptyList(), false, "Hello")
        val (turns2, speaking2) = applyListening(turns1, speaking1)
        assertEquals(2, turns2.size)
        assertEquals(ChatRole.USER, turns2[1].role)
        assertEquals("", turns2[1].text)
        assertEquals(false, speaking2)
    }

    @Test
    fun `new genie turn starts after listening`() {
        val (t1, s1) = applyTranscript(emptyList(), false, "First")
        val (t2, s2) = applyListening(t1, s1)
        val (t3, _) = applyTranscript(t2, s2, "Second")
        assertEquals(3, t3.size)
        assertEquals("First",  t3[0].text)
        assertEquals("",       t3[1].text)  // user placeholder
        assertEquals("Second", t3[2].text)
    }

    @Test
    fun `no user placeholder if genie was not speaking`() {
        val (turns, _) = applyListening(emptyList(), false)
        assertEquals(0, turns.size)
    }
}
