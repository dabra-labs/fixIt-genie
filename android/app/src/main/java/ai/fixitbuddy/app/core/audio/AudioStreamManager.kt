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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
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
    private val audioTrackLock = Any()

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
        // Max out media volume for demo clarity
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)

        val bufferSize = AudioTrack.getMinBufferSize(
            AppConfig.AUDIO_OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        synchronized(audioTrackLock) {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
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

            try {
                audioTrack?.play()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "AudioTrack.play() failed during init", e)
                audioTrack?.release()
                audioTrack = null
                return
            }
        }

        // Drain audio chunks on a dedicated IO thread to avoid blocking Main
        val ch = Channel<ByteArray>(capacity = AppConfig.AUDIO_PLAYBACK_QUEUE_CAPACITY)
        playbackChannel = ch
        startPlaybackLoop(ch)
    }

    fun playAudioChunk(data: ByteArray) {
        val sent = playbackChannel?.trySend(applyOutputGain(data))?.isSuccess ?: false
        if (!sent) Log.w(TAG, "Playback channel full — dropped ${data.size}B audio chunk")
    }

    /**
     * Immediately discards all queued audio and recreates the playback channel.
     * Called when the server sends `interrupted: true` (VAD detected user speaking).
     */
    fun interrupt() {
        val oldChannel = playbackChannel
        val ch = Channel<ByteArray>(capacity = AppConfig.AUDIO_PLAYBACK_QUEUE_CAPACITY)
        playbackChannel = ch
        oldChannel?.cancel()  // drops all buffered chunks
        playbackJob?.cancel()
        // flush() discards data already submitted to AudioTrack's hardware buffer and
        // unblocks any in-progress write() call in the old coroutine, preventing a race
        // where the old and new coroutines both write to the same AudioTrack concurrently.
        synchronized(audioTrackLock) {
            try {
                audioTrack?.flush()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioTrack.flush() failed during interrupt", e)
            }
        }
        startPlaybackLoop(ch)
        Log.d(TAG, "Playback interrupted — audio queue cleared")
    }

    fun stopPlayback() {
        playbackChannel?.close()  // signals no more sends
        playbackChannel = null
        playbackJob?.cancel()
        playbackJob = null
        synchronized(audioTrackLock) {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioTrack already stopped", e)
            }
            audioTrack = null
        }
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

    private fun applyOutputGain(pcm16: ByteArray): ByteArray {
        if (pcm16.size < 2 || AppConfig.AUDIO_OUTPUT_GAIN <= 1f) return pcm16

        val input = ByteBuffer.wrap(pcm16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        val sampleCount = input.remaining()
        if (sampleCount == 0) return pcm16

        var peak = 0
        for (i in 0 until sampleCount) {
            peak = maxOf(peak, abs(input[i].toInt()))
        }
        if (peak == 0) return pcm16

        val safeGain = min(
            AppConfig.AUDIO_OUTPUT_GAIN,
            (Short.MAX_VALUE.toFloat() * 0.92f) / peak.toFloat()
        )
        if (safeGain <= 1.01f) return pcm16

        val boosted = ByteArray(pcm16.size)
        val output = ByteBuffer.wrap(boosted)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        for (i in 0 until sampleCount) {
            val scaled = (input[i].toFloat() * safeGain)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.put(i, scaled.toShort())
        }
        if (pcm16.size % 2 != 0) {
            boosted[pcm16.lastIndex] = pcm16.last()
        }
        return boosted
    }

    private fun startPlaybackLoop(channel: Channel<ByteArray>) {
        playbackJob = playbackScope.launch {
            try {
                while (isActive) {
                    val firstChunk = channel.receiveCatching().getOrNull() ?: break
                    val batchedChunk = collectPlaybackBatch(firstChunk, channel)
                    writeFullyToTrack(batchedChunk)
                }
            } catch (_: CancellationException) {
                // Normal shutdown path.
            } catch (t: Throwable) {
                Log.e(TAG, "Playback loop aborted after audio pipeline failure", t)
                synchronized(audioTrackLock) {
                    try {
                        audioTrack?.pause()
                        audioTrack?.flush()
                    } catch (trackError: IllegalStateException) {
                        Log.w(TAG, "AudioTrack cleanup failed after playback loop abort", trackError)
                    }
                }
            }
        }
    }

    private suspend fun collectPlaybackBatch(
        firstChunk: ByteArray,
        channel: ReceiveChannel<ByteArray>
    ): ByteArray {
        if (firstChunk.isEmpty()) return firstChunk

        val parts = ArrayList<ByteArray>(4)
        var totalBytes = 0

        fun append(chunk: ByteArray) {
            if (chunk.isEmpty()) return
            parts += chunk
            totalBytes += chunk.size
        }

        append(firstChunk)

        while (totalBytes < AppConfig.AUDIO_PLAYBACK_TARGET_WRITE_BYTES) {
            val nextChunk = if (parts.size == 1) {
                withTimeoutOrNull(AppConfig.AUDIO_PLAYBACK_BATCH_WAIT_MS) {
                    channel.receiveCatching().getOrNull()
                }
            } else {
                channel.tryReceive().getOrNull()
            } ?: break
            append(nextChunk)
        }

        if (parts.size == 1) return firstChunk

        val combined = ByteArray(totalBytes)
        var offset = 0
        for (part in parts) {
            part.copyInto(combined, destinationOffset = offset)
            offset += part.size
        }
        return combined
    }

    private fun writeFullyToTrack(data: ByteArray) {
        if (data.isEmpty()) return
        synchronized(audioTrackLock) {
            val track = audioTrack ?: return

            var offset = 0
            while (offset < data.size) {
                val written = try {
                    track.write(
                        data,
                        offset,
                        data.size - offset,
                        AudioTrack.WRITE_BLOCKING
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "AudioTrack.write() aborted because the track became invalid", t)
                    break
                }
                when {
                    written > 0 -> offset += written
                    written == 0 -> {
                        Log.w(TAG, "AudioTrack.write() returned 0 with ${data.size - offset}B remaining")
                        break
                    }
                    else -> {
                        Log.e(TAG, "AudioTrack.write() error: $written")
                        break
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AudioStreamManager"
    }
}
