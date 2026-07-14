package com.gnani.livetranslation.pipeline

import android.util.Base64
import com.gnani.livetranslation.audio.TtsAudioPlayer
import com.gnani.livetranslation.captions.CaptionStateHolder
import com.gnani.livetranslation.data.BackendConfig
import com.gnani.livetranslation.data.CaptionDirection
import com.gnani.livetranslation.data.CaptionEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer

enum class PipelineMode {
    WEBRTC, PHONE
}

data class PipelineConfig(
    val mode: PipelineMode = PipelineMode.WEBRTC,
    val sourceLanguage: String,
    val targetLanguage: String,
    val remoteLanguage: String = targetLanguage,
    val listenLanguage: String = sourceLanguage,
    val playTts: Boolean = true
)

class TranslationPipelineClient(
    private val backendHost: String,
    private val token: String,
    private val roomId: String,
    private val peerId: String,
    private val config: PipelineConfig,
    private val ttsPlayer: TtsAudioPlayer,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    private var webSocket: WebSocketClient? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect() {
        val base = BackendConfig.wsBaseUrl(backendHost)
        val wsUrl = "$base/pipeline?token=$token&roomId=$roomId&peerId=$peerId"
        webSocket = object : WebSocketClient(URI(wsUrl)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                sendConfig()
                scope.launch(Dispatchers.Main) { onConnected() }
            }

            override fun onMessage(message: String?) {
                message ?: return
                handleJsonMessage(message)
            }

            override fun onMessage(bytes: ByteBuffer?) {
                bytes ?: return
                val pcm = ByteArray(bytes.remaining())
                bytes.get(pcm)
                if (config.playTts) {
                    ttsPlayer.enqueuePcm(pcm)
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                scope.launch(Dispatchers.Main) { onDisconnected() }
            }

            override fun onError(ex: Exception?) {
                scope.launch(Dispatchers.Main) {
                    onError(ex?.message ?: "Pipeline connection error")
                }
            }
        }
        webSocket?.connect()
    }

    fun sendAudioPcm(pcm: ByteArray) {
        webSocket?.send(pcm)
    }

    fun disconnect() {
        webSocket?.close()
        webSocket = null
    }

    private fun sendConfig() {
        val configJson = JSONObject()
            .put("type", "config")
            .put("mode", if (config.mode == PipelineMode.PHONE) "phone" else "webrtc")
            .put("sourceLanguage", config.sourceLanguage)
            .put("targetLanguage", config.targetLanguage)
            .put("remoteLanguage", config.remoteLanguage)
            .put("listenLanguage", config.listenLanguage)
        webSocket?.send(configJson.toString())
    }

    private fun handleJsonMessage(message: String) {
        val json = JSONObject(message)
        when (json.optString("type")) {
            "caption" -> {
                val direction = when (json.optString("direction")) {
                    "incoming" -> CaptionDirection.INCOMING
                    "outgoing" -> CaptionDirection.OUTGOING
                    else -> CaptionDirection.UNKNOWN
                }
                val entry = CaptionEntry(
                    originalText = json.optString("original", ""),
                    translatedText = json.optString("translated", ""),
                    isFinal = json.optBoolean("isFinal", false),
                    direction = direction,
                    label = json.optString("label").ifBlank { null }
                )
                scope.launch(Dispatchers.Main) {
                    CaptionStateHolder.addOrUpdateCaption(entry)
                }
            }
            "tts" -> {
                if (!config.playTts) return
                val b64 = json.optString("audio", "")
                if (b64.isNotEmpty()) {
                    val pcm = Base64.decode(b64, Base64.DEFAULT)
                    val sampleRate = json.optInt("sampleRate", 24000)
                    ttsPlayer.enqueuePcm(pcm, sampleRate)
                }
            }
            "translation-status" -> {
                scope.launch(Dispatchers.Main) { onConnected() }
            }
            "error" -> {
                scope.launch(Dispatchers.Main) {
                    onError(json.optString("message", "Pipeline error"))
                }
            }
        }
    }
}
