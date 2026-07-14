package com.gnani.livetranslation.phone

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gnani.livetranslation.audio.PcmAudioCapture
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
import java.util.UUID

enum class PhoneTranslationState {
    IDLE, STARTING, ACTIVE, STOPPED, ERROR
}

data class PhoneCallUiState(
    val state: PhoneTranslationState = PhoneTranslationState.IDLE,
    val statusMessage: String = "Start translation during your phone call",
    val errorMessage: String? = null,
    val translationActive: Boolean = false,
    val sourceLanguage: String = "hi",
    val remoteLanguage: String = "en",
    val listenLanguage: String = "hi"
)

class PhoneCallViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val httpClient = OkHttpClient()

    private val _uiState = MutableStateFlow(PhoneCallUiState())
    val uiState: StateFlow<PhoneCallUiState> = _uiState.asStateFlow()

    private var pipelineClient: TranslationPipelineClient? = null
    private var ttsPlayer: TtsAudioPlayer? = null
    private var pcmCapture: PcmAudioCapture? = null
    private var authToken: String? = null
    private var sessionId: String? = null
    private var languageSettings: UserLanguageSettings = settingsRepo.getLanguageSettings()
    private var backendHost: String = settingsRepo.getBackendHost()

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

    fun startTranslation() {
        if (_uiState.value.state == PhoneTranslationState.ACTIVE) return

        val peerId = UUID.randomUUID().toString().take(8)
        val roomId = "phone-$peerId"
        sessionId = roomId

        _uiState.update {
            it.copy(
                state = PhoneTranslationState.STARTING,
                errorMessage = null,
                statusMessage = "Connecting translation…"
            )
        }

        viewModelScope.launch {
            try {
                val token = fetchAuthToken(roomId, peerId)
                authToken = token
                startForegroundService(roomId)
                connectPipeline(token, roomId, peerId)
                startMicCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start phone translation", e)
                _uiState.update {
                    it.copy(
                        state = PhoneTranslationState.ERROR,
                        errorMessage = e.message ?: "Failed to start translation"
                    )
                }
            }
        }
    }

    fun stopTranslation() {
        pipelineClient?.disconnect()
        pcmCapture?.stop()
        ttsPlayer?.stop()
        stopForegroundService()
        CaptionStateHolder.clear()
        pipelineClient = null
        pcmCapture = null
        ttsPlayer = null
        authToken = null
        sessionId = null
        _uiState.update {
            it.copy(
                state = PhoneTranslationState.STOPPED,
                statusMessage = "Translation stopped",
                translationActive = false
            )
        }
    }

    fun resetToIdle() {
        _uiState.update {
            it.copy(
                state = PhoneTranslationState.IDLE,
                statusMessage = "Start translation during your phone call",
                errorMessage = null,
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

    private fun connectPipeline(token: String, roomId: String, peerId: String) {
        ttsPlayer = TtsAudioPlayer(viewModelScope).also { it.start() }

        val config = PipelineConfig(
            mode = PipelineMode.PHONE,
            sourceLanguage = languageSettings.sourceLanguage,
            targetLanguage = languageSettings.remoteLanguage,
            remoteLanguage = languageSettings.remoteLanguage,
            listenLanguage = languageSettings.targetLanguage,
            playTts = true
        )

        pipelineClient = TranslationPipelineClient(
            backendHost = backendHost,
            token = token,
            roomId = roomId,
            peerId = peerId,
            config = config,
            ttsPlayer = ttsPlayer!!,
            onConnected = {
                _uiState.update {
                    it.copy(
                        state = PhoneTranslationState.ACTIVE,
                        statusMessage = "Translation active — use speakerphone for best results",
                        translationActive = true
                    )
                }
            },
            onError = { msg ->
                _uiState.update { it.copy(errorMessage = msg) }
            }
        )
        pipelineClient?.connect()
    }

    private fun startMicCapture() {
        pcmCapture = PcmAudioCapture(
            audioSource = PcmAudioCapture.AudioSourceType.PHONE_CALL,
            onPcmChunk = { pcm ->
                pipelineClient?.sendAudioPcm(pcm)
            }
        )
        pcmCapture?.start(viewModelScope)
    }

    private fun startForegroundService(sessionId: String) {
        val intent = Intent(getApplication(), TranslationForegroundService::class.java).apply {
            putExtra(TranslationForegroundService.EXTRA_ROOM_ID, sessionId)
            putExtra(TranslationForegroundService.EXTRA_MODE, TranslationForegroundService.MODE_PHONE)
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
        stopTranslation()
        super.onCleared()
    }

    companion object {
        private const val TAG = "PhoneCallViewModel"
    }
}
