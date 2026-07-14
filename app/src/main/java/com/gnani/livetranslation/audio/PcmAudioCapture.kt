package com.gnani.livetranslation.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PcmAudioCapture(
    private val audioSource: AudioSourceType = AudioSourceType.VOIP,
    private val onPcmChunk: (ByteArray) -> Unit
) {
    enum class AudioSourceType {
        VOIP,
        PHONE_CALL
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 4
    }

    fun start(scope: CoroutineScope) {
        if (captureJob?.isActive == true) return

        val source = when (audioSource) {
            AudioSourceType.VOIP -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            AudioSourceType.PHONE_CALL -> MediaRecorder.AudioSource.MIC
        }

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBuffer, CHUNK_SAMPLES * 2)

        val record = AudioRecord(
            source,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        audioRecord = record
        record.startRecording()

        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(CHUNK_SAMPLES * 2)
            while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    onPcmChunk(buffer.copyOf(read))
                }
            }
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
