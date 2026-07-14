package com.gnani.livetranslation.call

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gnani.livetranslation.audio.TtsAudioPlayer
import com.gnani.livetranslation.captions.CaptionStateHolder
import com.gnani.livetranslation.data.BackendConfig
import com.gnani.livetranslation.data.SupportedLanguages
import com.gnani.livetranslation.data.UserLanguageSettings
import com.gnani.livetranslation.pipeline.PipelineConfig
import com.gnani.livetranslation.pipeline.PipelineMode
import com.gnani.livetranslation.pipeline.TranslationPipelineClient
import com.gnani.livetranslation.service.TranslationForegroundService
import com.gnani.livetranslation.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.util.UUID

enum class CallState {
    IDLE, CONNECTING, WAITING_FOR_PEER, IN_CALL, ENDED, ERROR
}

data class CallUiState(
    val callState: CallState = CallState.IDLE,
    val roomId: String = "",
    val peerId: String = "",
    val peerDisplayName: String? = null,
    val isMuted: Boolean = false,
    val translationActive: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String = "Enter a room ID to start or join a call",
    val sourceLanguage: String = "hi",
    val targetLanguage: String = "en"
)

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val httpClient = OkHttpClient()

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private var webRtcClient: WebRtcClient? = null
    private var signalingClient: SignalingClient? = null
    private var pipelineClient: TranslationPipelineClient? = null
    private var ttsPlayer: TtsAudioPlayer? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var authToken: String? = null
    private var isInitiator = false
    private var languageSettings: UserLanguageSettings = settingsRepo.getLanguageSettings()
    private var backendHost: String = settingsRepo.getBackendHost()

    fun refreshSettings() {
        languageSettings = settingsRepo.getLanguageSettings()
        backendHost = settingsRepo.getBackendHost()
        _uiState.update {
            it.copy(
                sourceLanguage = languageSettings.sourceLanguage,
                targetLanguage = languageSettings.targetLanguage
            )
        }
    }

    fun setRoomId(roomId: String) {
        _uiState.update { it.copy(roomId = roomId.trim()) }
    }

    fun resetToIdle() {
        _uiState.update {
            it.copy(
                callState = CallState.IDLE,
                errorMessage = null,
                statusMessage = "Enter a room ID to start or join a call",
                translationActive = false,
                peerDisplayName = null
            )
        }
    }

    fun startCall(asInitiator: Boolean) {
        val roomId = _uiState.value.roomId
        if (roomId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Room ID is required") }
            return
        }
        if (languageSettings.sourceLanguage == languageSettings.targetLanguage &&
            languageSettings.remoteLanguage == languageSettings.sourceLanguage
        ) {
            _uiState.update { it.copy(errorMessage = "Languages must differ for in-app calls") }
            return
        }

        isInitiator = asInitiator
        val peerId = UUID.randomUUID().toString().take(8)
        _uiState.update {
            it.copy(
                callState = CallState.CONNECTING,
                peerId = peerId,
                errorMessage = null,
                statusMessage = "Authenticating…",
                sourceLanguage = languageSettings.sourceLanguage,
                targetLanguage = languageSettings.targetLanguage
            )
        }
        viewModelScope.launch {
            try {
                val token = fetchAuthToken(roomId, peerId)
                authToken = token
                startForegroundService(roomId)
                connectSignaling(token, roomId, peerId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start call", e)
                _uiState.update {
                    it.copy(
                        callState = CallState.ERROR,
                        errorMessage = e.message ?: "Failed to start call"
                    )
                }
            }
        }
    }

    fun toggleMute() {
        val muted = !_uiState.value.isMuted
        webRtcClient?.setMuted(muted)
        _uiState.update { it.copy(isMuted = muted) }
    }

    fun endCall() {
        pipelineClient?.disconnect()
        signalingClient?.disconnect()
        webRtcClient?.dispose()
        ttsPlayer?.stop()
        remoteAudioTrack?.setEnabled(false)
        stopForegroundService()
        CaptionStateHolder.clear()
        pipelineClient = null
        signalingClient = null
        webRtcClient = null
        ttsPlayer = null
        remoteAudioTrack = null
        _uiState.update {
            it.copy(
                callState = CallState.ENDED,
                statusMessage = "Call ended",
                translationActive = false
            )
        }
    }

    private suspend fun fetchAuthToken(roomId: String, peerId: String): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("roomId", roomId)
                .put("peerId", peerId)
                .put("displayName", settingsRepo.getDisplayName())
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${BackendConfig.httpBaseUrl(backendHost)}/auth/token")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Auth failed: ${response.code}")
                }
                val json = JSONObject(response.body?.string() ?: "{}")
                json.getString("token")
            }
        }

    private fun connectSignaling(token: String, roomId: String, peerId: String) {
        signalingClient = SignalingClient(
            backendHost = backendHost,
            token = token,
            roomId = roomId,
            peerId = peerId,
            onMessage = { handleSignalingMessage(it) },
            onConnected = {
                _uiState.update {
                    it.copy(statusMessage = if (isInitiator) "Waiting for peer…" else "Joining room…")
                }
            },
            onDisconnected = {
                if (_uiState.value.callState == CallState.IN_CALL ||
                    _uiState.value.callState == CallState.WAITING_FOR_PEER
                ) {
                    endCall()
                }
            }
        )
        signalingClient?.connect()
    }

    private fun handleSignalingMessage(message: JSONObject) {
        when (message.optString("type")) {
            "room-ready" -> {
                val iceServers = parseIceServers(message.getJSONArray("iceServers"))
                setupWebRtc(iceServers)
                if (isInitiator) {
                    _uiState.update { it.copy(callState = CallState.WAITING_FOR_PEER) }
                }
            }
            "waiting-for-peer" -> {
                _uiState.update {
                    it.copy(
                        callState = CallState.WAITING_FOR_PEER,
                        statusMessage = "Waiting for another person to join…"
                    )
                }
            }
            "peer-joined" -> {
                val name = message.optString("displayName", "Peer")
                _uiState.update {
                    it.copy(
                        peerDisplayName = name,
                        statusMessage = "$name joined — connecting…"
                    )
                }
                if (isInitiator && webRtcClient != null) {
                    webRtcClient?.createOffer { sdp ->
                        signalingClient?.sendSignal("offer", sdpToJson(sdp))
                    }
                }
            }
            "offer" -> {
                val payload = message.getJSONObject("payload")
                val sdp = jsonToSdp(payload, SessionDescription.Type.OFFER)
                webRtcClient?.setRemoteDescription(sdp) {
                    webRtcClient?.createAnswer { answer ->
                        signalingClient?.sendSignal("answer", sdpToJson(answer))
                    }
                }
            }
            "answer" -> {
                val payload = message.getJSONObject("payload")
                val sdp = jsonToSdp(payload, SessionDescription.Type.ANSWER)
                webRtcClient?.setRemoteDescription(sdp)
            }
            "ice-candidate" -> {
                val payload = message.getJSONObject("payload")
                val candidate = IceCandidate(
                    payload.getString("sdpMid"),
                    payload.getInt("sdpMLineIndex"),
                    payload.getString("candidate")
                )
                webRtcClient?.addIceCandidate(candidate)
            }
            "call-connected" -> onCallConnected()
            "peer-left" -> {
                _uiState.update {
                    it.copy(statusMessage = "Other party left the call")
                }
                endCall()
            }
            "error" -> {
                _uiState.update {
                    it.copy(
                        callState = CallState.ERROR,
                        errorMessage = message.optString("message", "Signaling error")
                    )
                }
            }
        }
    }

    private fun setupWebRtc(iceServers: List<PeerConnection.IceServer>) {
        val app = getApplication<Application>()
        webRtcClient = WebRtcClient(
            context = app,
            peerId = _uiState.value.peerId,
            onRemoteAudioTrack = { track ->
                remoteAudioTrack = track
                applyRemoteVolume()
            },
            onIceCandidate = { candidate ->
                val payload = JSONObject()
                    .put("sdpMid", candidate.sdpMid)
                    .put("sdpMLineIndex", candidate.sdpMLineIndex)
                    .put("candidate", candidate.sdp)
                signalingClient?.sendSignal("ice-candidate", payload)
            },
            onPcmSamples = { pcm ->
                pipelineClient?.sendAudioPcm(pcm)
            },
            onError = { msg ->
                _uiState.update { it.copy(errorMessage = msg) }
            }
        )
        webRtcClient?.createPeerConnection(iceServers)
    }

    private fun onCallConnected() {
        _uiState.update {
            it.copy(
                callState = CallState.IN_CALL,
                statusMessage = "Connected — translation active"
            )
        }
        startTranslationPipeline()
    }

    private fun startTranslationPipeline() {
        val token = authToken ?: return
        val roomId = _uiState.value.roomId
        val peerId = _uiState.value.peerId

        ttsPlayer = TtsAudioPlayer(viewModelScope).also { it.start() }

        pipelineClient = TranslationPipelineClient(
            backendHost = backendHost,
            token = token,
            roomId = roomId,
            peerId = peerId,
            config = PipelineConfig(
                mode = PipelineMode.WEBRTC,
                sourceLanguage = languageSettings.sourceLanguage,
                targetLanguage = languageSettings.targetLanguage
            ),
            ttsPlayer = ttsPlayer!!,
            onConnected = {
                _uiState.update { it.copy(translationActive = true) }
            },
            onError = { msg ->
                _uiState.update { it.copy(errorMessage = msg) }
            }
        )
        pipelineClient?.connect()
    }

    private fun applyRemoteVolume() {
        val volume = if (languageSettings.hearOriginalAlongsideTranslation) 0.35 else 0.05
        remoteAudioTrack?.setVolume(volume)
    }

    private fun parseIceServers(array: org.json.JSONArray): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val urls = obj.getJSONArray("urls")
            val urlList = (0 until urls.length()).map { urls.getString(it) }
            val builder = PeerConnection.IceServer.builder(urlList)
            if (obj.has("username")) builder.setUsername(obj.getString("username"))
            if (obj.has("credential")) builder.setPassword(obj.getString("credential"))
            servers.add(builder.createIceServer())
        }
        return servers
    }

    private fun sdpToJson(sdp: SessionDescription): JSONObject {
        return JSONObject()
            .put("type", sdp.type.canonicalForm())
            .put("sdp", sdp.description)
    }

    private fun jsonToSdp(json: JSONObject, type: SessionDescription.Type): SessionDescription {
        return SessionDescription(type, json.getString("sdp"))
    }

    private fun startForegroundService(roomId: String) {
        val intent = Intent(getApplication(), TranslationForegroundService::class.java).apply {
            putExtra(TranslationForegroundService.EXTRA_ROOM_ID, roomId)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    private fun stopForegroundService() {
        val intent = Intent(getApplication(), TranslationForegroundService::class.java)
        getApplication<Application>().stopService(intent)
    }

    fun languageLabel(code: String): String =
        SupportedLanguages.findByCode(code)?.displayName ?: code

    override fun onCleared() {
        endCall()
        super.onCleared()
    }

    companion object {
        private const val TAG = "CallViewModel"
    }
}
