package ai.fixitbuddy.app.features.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SessionAudioGateTest {

    @Test
    fun `quiet chunk is blocked while agent is speaking`() {
        assertFalse(shouldForwardMicChunk(pcmChunk(50, 480), agentSpeaking = true))
    }

    @Test
    fun `speech chunk is forwarded while agent is speaking`() {
        assertTrue(shouldForwardMicChunk(pcmChunk(150, 480), agentSpeaking = true))
    }

    @Test
    fun `all chunks are forwarded when agent is not speaking`() {
        assertTrue(shouldForwardMicChunk(pcmChunk(50, 480), agentSpeaking = false))
    }

    @Test
    fun `barge in gate keeps forwarding follow up chunks after trigger`() {
        val gate = BargeInAudioGate(triggerLevel = 0.10f, holdChunks = 3)

        assertTrue(gate.shouldForward(pcmChunk(150, 480), agentSpeaking = true))
        assertTrue(gate.shouldForward(pcmChunk(50, 480), agentSpeaking = true))
        assertTrue(gate.shouldForward(pcmChunk(50, 480), agentSpeaking = true))
        assertFalse(gate.shouldForward(pcmChunk(50, 480), agentSpeaking = true))
    }

    @Test
    fun `barge in gate resets when agent stops speaking`() {
        val gate = BargeInAudioGate(triggerLevel = 0.10f, holdChunks = 3)

        assertTrue(gate.shouldForward(pcmChunk(150, 480), agentSpeaking = true))
        assertTrue(gate.shouldForward(pcmChunk(50, 480), agentSpeaking = false))
        assertFalse(gate.shouldForward(pcmChunk(50, 480), agentSpeaking = true))
    }

    private fun pcmChunk(amplitude: Short, samples: Int): ByteArray {
        val chunk = ByteArray(samples * 2)
        val buffer = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        repeat(samples) { index ->
            buffer.put(index, if (index % 2 == 0) amplitude else (-amplitude).toShort())
        }
        return chunk
    }
}
