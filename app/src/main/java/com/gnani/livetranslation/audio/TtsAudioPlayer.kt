package com.gnani.livetranslation.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class TtsAudioPlayer(
    private val scope: CoroutineScope
) {
    private val queue = Channel<TtsChunk>(Channel.UNLIMITED)
    private var playbackJob: Job? = null
    private val isPlaying = AtomicBoolean(false)

    data class TtsChunk(
        val pcm: ByteArray,
        val sampleRate: Int = 24000
    )

    fun start() {
        if (playbackJob?.isActive == true) return
        playbackJob = scope.launch(Dispatchers.IO) {
            for (chunk in queue) {
                playChunk(chunk)
            }
        }
    }

    @Synchronized
    fun enqueuePcm(pcmData: ByteArray, sampleRate: Int = 24000) {
        val isMp3 = pcmData.size > 2 &&
            pcmData[0] == 0xFF.toByte() &&
            (pcmData[1].toInt() and 0xE0) == 0xE0

        val pcm = if (isMp3) pcmData else pcmData
        if (!isMp3) {
            queue.trySend(TtsChunk(pcm, sampleRate))
        } else {
            playMp3Fallback(pcmData)
        }
    }

    private fun playChunk(chunk: TtsChunk) {
        val wav = wrapPcmAsWav(chunk.pcm, chunk.sampleRate)
        val pcmOnly = wav.copyOfRange(44, wav.size)
        playPcmBlocking(pcmOnly, chunk.sampleRate)
    }

    private fun playPcmBlocking(pcm: ByteArray, sampleRate: Int) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, pcm.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(pcm, 0, pcm.size)
            isPlaying.set(true)
            track.play()
            val durationMs = (pcm.size.toLong() * 1000) / (sampleRate * 2)
            Thread.sleep(durationMs + 50)
            track.stop()
        } catch (e: Exception) {
            android.util.Log.e("TtsAudioPlayer", "Playback failed", e)
        } finally {
            track.release()
            isPlaying.set(false)
        }
    }

    private fun playMp3Fallback(data: ByteArray) {
        try {
            val tempFile = java.io.File.createTempFile("tts_chunk_", ".mp3")
            java.io.FileOutputStream(tempFile).use { it.write(data) }
            val player = android.media.MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    tempFile.delete()
                    release()
                }
                prepare()
                start()
            }
            while (player.isPlaying) {
                Thread.sleep(50)
            }
        } catch (e: Exception) {
            android.util.Log.e("TtsAudioPlayer", "MP3 fallback failed", e)
        }
    }

    private fun wrapPcmAsWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        val totalDataLen = pcm.size + 36
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[20] = 1
        header[22] = channels.toByte()
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[34] = bitsPerSample.toByte()
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcm.size and 0xff).toByte()
        header[41] = (pcm.size shr 8 and 0xff).toByte()
        header[42] = (pcm.size shr 16 and 0xff).toByte()
        header[43] = (pcm.size shr 24 and 0xff).toByte()
        return header + pcm
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        while (queue.tryReceive().isSuccess) { /* drain */ }
    }
}
