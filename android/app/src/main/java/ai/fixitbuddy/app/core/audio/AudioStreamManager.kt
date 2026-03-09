package ai.fixitbuddy.app.core.audio

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import ai.fixitbuddy.app.core.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
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
 * Playback: accepts PCM audio from the backend and writes it to an
 * [AudioTrack] configured at the output sample rate.
 */
@Singleton
class AudioStreamManager @Inject constructor() {

    // ── Recording (mic → backend) ──────────────────────────

    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false

    /** Managed scope for the recording coroutine; cancelled in [stopRecording]. */
    private val recordingScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
            MediaRecorder.AudioSource.MIC,
            AppConfig.AUDIO_INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        isRecording = true
        audioRecord?.startRecording()

        recordingJob = recordingScope.launch {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0) {
                    _audioChunks.emit(buffer.copyOf(bytesRead))
                    _audioLevel.value = computeRmsLevel(buffer, bytesRead)
                }
            }
            _audioLevel.value = 0f
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingScope.coroutineContext.cancelChildren()
        recordingJob = null
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

    fun initPlayback() {
        // Release any existing AudioTrack to prevent leaks
        stopPlayback()

        val bufferSize = AudioTrack.getMinBufferSize(
            AppConfig.AUDIO_OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

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
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    fun playAudioChunk(data: ByteArray) {
        audioTrack?.write(data, 0, data.size)
    }

    fun stopPlayback() {
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
