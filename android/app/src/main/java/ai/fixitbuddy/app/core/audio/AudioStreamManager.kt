package ai.fixitbuddy.app.core.audio

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.fixitbuddy.app.core.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Manages bidirectional audio streaming for a FixIt Buddy session.
 *
 * Recording: captures 16 kHz mono PCM from the device microphone and emits
 * chunks via [audioChunks] for forwarding to the ADK backend.
 *
 * Playback: accepts PCM audio from the backend and writes it to an [AudioTrack]
 * using USAGE_MEDIA (routes to STREAM_MUSIC — 15 volume steps vs STREAM_VOICE_CALL's
 * 5 steps, 3× louder on loudspeaker).
 * Trade-off: hardware AEC reference may be mismatched because the far-end audio is not
 * on the voice call stream. Software AEC (via VOICE_COMMUNICATION AudioRecord source)
 * is still active. If echo issues surface, switch back to USAGE_VOICE_COMMUNICATION
 * + MODE_IN_COMMUNICATION to restore the hardware AEC reference signal.
 */
@Singleton
class AudioStreamManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ── Recording (mic → backend) ──────────────────────────

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    @Volatile private var isRecording = false

    /** Managed scope for the recording coroutine; cancelled in [stopRecording]. */
    private val recordingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    private val _audioChunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 5)
    val audioChunks: SharedFlow<ByteArray> = _audioChunks

    /** Normalised audio level 0f..1f, updated each recording buffer read. */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(
            AppConfig.AUDIO_INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // enables software AEC + AGC + NS
            AppConfig.AUDIO_INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize — state=${audioRecord?.state}. " +
                "Check RECORD_AUDIO permission and hardware availability.")
            audioRecord?.release()
            audioRecord = null
            return
        }

        // Attach hardware AEC to cancel speaker output from mic input
        val sessionId = audioRecord!!.audioSessionId
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true }
            Log.d(TAG, "AcousticEchoCanceler enabled: ${echoCanceler?.enabled}")
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)?.also { it.enabled = true }
            Log.d(TAG, "NoiseSuppressor enabled: ${noiseSuppressor?.enabled}")
        }

        isRecording = true
        audioRecord?.startRecording()

        recordingJob = recordingScope.launch {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                when {
                    bytesRead > 0 -> {
                        _audioChunks.emit(buffer.copyOf(bytesRead))
                        _audioLevel.value = computeRmsLevel(buffer, bytesRead)
                    }
                    bytesRead == AudioRecord.ERROR_DEAD_OBJECT -> {
                        Log.e(TAG, "AudioRecord.ERROR_DEAD_OBJECT — mic connection lost, stopping")
                        break
                    }
                    bytesRead < 0 -> Log.e(TAG, "AudioRecord.read() error: $bytesRead")
                }
            }
            _audioLevel.value = 0f
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingScope.coroutineContext.cancelChildren()
        recordingJob = null
        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioRecord already stopped", e)
        }
        audioRecord = null
        _audioLevel.value = 0f
    }

    // ── Playback (backend → speaker) ──────────────────────

    private var audioTrack: AudioTrack? = null
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playbackJob: Job? = null
    private var playbackChannel: Channel<ByteArray>? = null

    fun initPlayback() {
        stopPlayback()

        val bufferSize = AudioTrack.getMinBufferSize(
            AppConfig.AUDIO_OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // USAGE_MEDIA → STREAM_MUSIC (15 volume steps, full loudspeaker).
                    // Software AEC active via VOICE_COMMUNICATION source; hardware AEC
                    // reference may be mismatched (far-end not on voice call stream).
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(AppConfig.AUDIO_OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 8)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        // Drain audio chunks on a dedicated IO thread to avoid blocking Main
        val ch = Channel<ByteArray>(capacity = 50)
        playbackChannel = ch
        playbackJob = playbackScope.launch {
            for (chunk in ch) {
                val result = audioTrack?.write(chunk, 0, chunk.size) ?: -99
                if (result < 0) Log.e(TAG, "AudioTrack.write() error: $result")
            }
        }
    }

    fun playAudioChunk(data: ByteArray) {
        val sent = playbackChannel?.trySend(data)?.isSuccess ?: false
        if (!sent) Log.w(TAG, "Playback channel full — dropped ${data.size}B audio chunk")
    }

    /**
     * Immediately discards all queued audio and recreates the playback channel.
     * Called when the server sends `interrupted: true` (VAD detected user speaking).
     */
    fun interrupt() {
        val oldChannel = playbackChannel
        val ch = Channel<ByteArray>(capacity = 50)
        playbackChannel = ch
        oldChannel?.cancel()  // drops all buffered chunks
        playbackJob?.cancel()
        // flush() discards data already submitted to AudioTrack's hardware buffer and
        // unblocks any in-progress write() call in the old coroutine, preventing a race
        // where the old and new coroutines both write to the same AudioTrack concurrently.
        audioTrack?.flush()
        playbackJob = playbackScope.launch {
            for (chunk in ch) {
                val result = audioTrack?.write(chunk, 0, chunk.size) ?: -99
                if (result < 0) Log.e(TAG, "AudioTrack.write() error: $result")
            }
        }
        Log.d(TAG, "Playback interrupted — audio queue cleared")
    }

    fun stopPlayback() {
        playbackChannel?.close()  // signals no more sends
        playbackChannel = null
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioTrack already stopped", e)
        }
        audioTrack = null
    }

    fun releaseAll() {
        stopRecording()
        stopPlayback()
    }

    /**
     * Compute normalised RMS (0f..1f) from 16-bit PCM samples.
     * Uses a simple log scale so quiet speech still shows visible movement.
     */
    private fun computeRmsLevel(buffer: ByteArray, bytesRead: Int): Float {
        val shortBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        val sampleCount = shortBuffer.remaining()
        if (sampleCount == 0) return 0f

        var sumSquares = 0.0
        for (i in 0 until sampleCount) {
            val sample = shortBuffer[i].toDouble()
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / sampleCount)

        // Normalise: 16-bit PCM max is 32768; use log scale for better UX
        // Clamp to 0..1 range
        val db = if (rms > 1.0) 20.0 * Math.log10(rms / 32768.0) else -96.0
        // Map -60dB..0dB → 0..1 (anything below -60dB is silence)
        return ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
    }

    companion object {
        private const val TAG = "AudioStreamManager"
    }
}
