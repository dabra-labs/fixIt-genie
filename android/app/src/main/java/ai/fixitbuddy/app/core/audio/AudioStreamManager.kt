package ai.fixitbuddy.app.core.audio

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import ai.fixitbuddy.app.core.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioStreamManager @Inject constructor() {

    // ── Recording (mic → backend) ──────────────────────────

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val _audioChunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 5)
    val audioChunks: SharedFlow<ByteArray> = _audioChunks

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

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0) {
                    _audioChunks.emit(buffer.copyOf(bytesRead))
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
            // Already stopped
        }
        audioRecord = null
    }

    // ── Playback (backend → speaker) ──────────────────────

    private var audioTrack: AudioTrack? = null

    fun initPlayback() {
        val bufferSize = AudioTrack.getMinBufferSize(
            AppConfig.AUDIO_OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
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
        } catch (_: Exception) {
            // Already stopped
        }
        audioTrack = null
    }

    fun releaseAll() {
        stopRecording()
        stopPlayback()
    }
}
