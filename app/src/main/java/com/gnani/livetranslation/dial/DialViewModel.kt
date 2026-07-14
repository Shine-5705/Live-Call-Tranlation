package com.gnani.livetranslation.dial

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gnani.livetranslation.captions.CaptionStateHolder
import com.gnani.livetranslation.data.BackendConfig
import com.gnani.livetranslation.data.CaptionDirection
import com.gnani.livetranslation.data.CaptionEntry
import com.gnani.livetranslation.data.SupportedLanguages
import com.gnani.livetranslation.data.UserLanguageSettings
import com.gnani.livetranslation.service.TranslationForegroundService
import com.gnani.livetranslation.settings.SettingsRepository
import com.gnani.livetranslation.twilio.TwilioCallManager
import com.gnani.livetranslation.twilio.TwilioCallState
import com.gnani.livetranslation.util.DeviceUtils
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
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

enum class DialCallState {
    IDLE, DIALING, IN_CALL, ENDED, ERROR
}

data class DialUiState(
    val phoneNumber: String = "",
    val callState: DialCallState = DialCallState.IDLE,
    val statusMessage: String = "Enter a phone number to call",
    val errorMessage: String? = null,
    val isMuted: Boolean = false,
    val sessionId: String? = null,
    val sourceLanguage: String = "hi",
    val remoteLanguage: String = "en",
    val listenLanguage: String = "hi"
)

class DialViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val httpClient = OkHttpClient()
    private val twilioManager = TwilioCallManager(application)

    private val _uiState = MutableStateFlow(DialUiState())
    val uiState: StateFlow<DialUiState> = _uiState.asStateFlow()

    private var authToken: String? = null
    private var eventsClient: WebSocketClient? = null
    private var languageSettings: UserLanguageSettings = settingsRepo.getLanguageSettings()
    private var backendHost: String = settingsRepo.getBackendHost()

    init {
        viewModelScope.launch {
            twilioManager.state.collect { twilioState ->
                when (twilioState) {
                    TwilioCallState.CONNECTED -> {
                        _uiState.update {
                            it.copy(
                                callState = DialCallState.IN_CALL,
                                statusMessage = "Connected — translation active"
                            )
                        }
                    }
                    TwilioCallState.DISCONNECTED -> {
                        if (_uiState.value.callState == DialCallState.IN_CALL) {
                            endCall()
                        }
                    }
                    TwilioCallState.ERROR -> {
                        _uiState.update {
                            it.copy(
                                callState = DialCallState.ERROR,
                                errorMessage = twilioManager.error.value
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun refreshSettings() {
        languageSettings = settingsRepo.getLanguageSettings()
        backendHost = settingsRepo.getBackendHost()
        _uiState.update {
            it.copy(
                sourceLanguage = languageSettings.sourceLanguage,
                remoteLanguage = languageSettings.remoteLanguage,
                listenLanguage = languageSettings.targetLanguage
            )
        }
    }

    fun setPhoneNumber(number: String) {
        _uiState.update { it.copy(phoneNumber = number) }
    }

    fun startCall() {
        refreshSettings()
        val number = _uiState.value.phoneNumber.trim()
        Log.d(TAG, "startCall: host=$backendHost, number=$number")
        
        if (number.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Phone number is required") }
            return
        }
        if (!BackendConfig.isConfigured(backendHost)) {
            _uiState.update {
                it.copy(errorMessage = "Set backend server in Settings (e.g. 192.168.1.3:3000)")
            }
            return
        }
        if (!DeviceUtils.isEmulator() && backendHost == DeviceUtils.EMULATOR_BACKEND_HOST) {
            _uiState.update {
                it.copy(errorMessage = "10.0.2.2 only works on emulator. Use your Mac's IP in Settings.")
            }
            return
        }

        val e164 = if (number.startsWith("+")) number else "+$number"

        _uiState.update {
            it.copy(
                callState = DialCallState.DIALING,
                errorMessage = null,
                statusMessage = "Placing call…"
            )
        }

        viewModelScope.launch {
            try {
                Log.i(TAG, "Attempting to connect to: ${BackendConfig.httpBaseUrl(backendHost)}")
                val jwt = fetchAuthToken()
                authToken = jwt
                val session = startCallSession(jwt, e164)
                connectCaptionEvents(session.sessionId)
                startForegroundService(session.sessionId)
                twilioManager.connect(session.accessToken, session.sessionId)
                _uiState.update {
                    it.copy(sessionId = session.sessionId, statusMessage = "Ringing…")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Call failed to host $backendHost", e)
                val userFriendlyMessage = when {
                    e is java.net.ConnectException || e.message?.contains("timeout", ignoreCase = true) == true -> {
                        "Failed to connect to $backendHost. \n\n1. Check if backend is running.\n2. Verify Wi-Fi (phone & PC on same network).\n3. Disable Firewall on PC."
                    }
                    else -> e.message ?: "Call failed"
                }
                _uiState.update {
                    it.copy(
                        callState = DialCallState.ERROR,
                        errorMessage = userFriendlyMessage
                    )
                }
            }
        }
    }

    fun toggleMute() {
        val muted = !_uiState.value.isMuted
        twilioManager.setMuted(muted)
        _uiState.update { it.copy(isMuted = muted) }
    }

    fun endCall() {
        val sessionId = _uiState.value.sessionId
        twilioManager.disconnect()
        eventsClient?.close()
        eventsClient = null
        stopForegroundService()

        if (sessionId != null && authToken != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val body = JSONObject().put("sessionId", sessionId).toString()
                        .toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("${BackendConfig.httpBaseUrl(backendHost)}/v1/calls/end")
                        .addHeader("Authorization", "Bearer $authToken")
                        .post(body)
                        .build()
                    httpClient.newCall(request).execute().close()
                } catch (_: Exception) {}
            }
        }

        CaptionStateHolder.clear()
        twilioManager.reset()
        authToken = null
        _uiState.update {
            it.copy(
                callState = DialCallState.ENDED,
                statusMessage = "Call ended",
                sessionId = null
            )
        }
    }

    fun resetToIdle() {
        _uiState.update {
            it.copy(
                callState = DialCallState.IDLE,
                statusMessage = "Enter a phone number to call",
                errorMessage = null
            )
        }
    }

    private suspend fun fetchAuthToken(): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("identity", settingsRepo.getDisplayName())
            .put("displayName", settingsRepo.getDisplayName())
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${BackendConfig.httpBaseUrl(backendHost)}/auth/token")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Auth failed: ${response.code}")
            JSONObject(response.body?.string() ?: "{}").getString("token")
        }
    }

    private data class CallSessionResponse(
        val sessionId: String,
        val accessToken: String
    )

    private suspend fun startCallSession(jwt: String, toNumber: String): CallSessionResponse =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("toNumber", toNumber)
                .put("sourceLanguage", languageSettings.sourceLanguage)
                .put("targetLanguage", languageSettings.targetLanguage)
                .put("remoteLanguage", languageSettings.remoteLanguage)
                .put("identity", settingsRepo.getDisplayName())
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${BackendConfig.httpBaseUrl(backendHost)}/v1/calls/start")
                .addHeader("Authorization", "Bearer $jwt")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    throw IllegalStateException("Call start failed: ${response.code} $err")
                }
                val json = JSONObject(response.body?.string() ?: "{}")
                CallSessionResponse(
                    sessionId = json.getString("sessionId"),
                    accessToken = json.getString("accessToken")
                )
            }
        }

    private fun connectCaptionEvents(sessionId: String) {
        val wsUrl = "${BackendConfig.wsBaseUrl(backendHost)}/call-events?sessionId=$sessionId"
        eventsClient = object : WebSocketClient(URI(wsUrl)) {
            override fun onMessage(message: String?) {
                message ?: return
                val json = JSONObject(message)
                if (json.optString("type") != "caption") return

                val direction = when (json.optString("direction")) {
                    "incoming" -> CaptionDirection.INCOMING
                    "outgoing" -> CaptionDirection.OUTGOING
                    else -> CaptionDirection.UNKNOWN
                }
                val entry = CaptionEntry(
                    originalText = json.optString("original", ""),
                    translatedText = json.optString("translated", ""),
                    isFinal = json.optBoolean("isFinal", true),
                    direction = direction,
                    label = if (direction == CaptionDirection.OUTGOING) "You said" else "They said"
                )
                viewModelScope.launch {
                    CaptionStateHolder.addOrUpdateCaption(entry)
                }
            }

            override fun onOpen(handshakedata: ServerHandshake?) {}
            override fun onClose(code: Int, reason: String?, remote: Boolean) {}
            override fun onError(ex: Exception?) {}
        }
        eventsClient?.connect()
    }

    private fun startForegroundService(sessionId: String) {
        val intent = Intent(getApplication(), TranslationForegroundService::class.java).apply {
            putExtra(TranslationForegroundService.EXTRA_ROOM_ID, sessionId)
            putExtra(TranslationForegroundService.EXTRA_MODE, "twilio")
        }
        getApplication<Application>().startForegroundService(intent)
    }

    private fun stopForegroundService() {
        getApplication<Application>().stopService(
            Intent(getApplication(), TranslationForegroundService::class.java)
        )
    }

    fun languageLabel(code: String): String =
        SupportedLanguages.findByCode(code)?.displayName ?: code

    override fun onCleared() {
        endCall()
        super.onCleared()
    }

    companion object {
        private const val TAG = "DialViewModel"
    }
}
