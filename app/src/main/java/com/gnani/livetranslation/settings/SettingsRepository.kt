package com.gnani.livetranslation.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gnani.livetranslation.data.BackendConfig
import com.gnani.livetranslation.data.UserLanguageSettings

class SettingsRepository(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getLanguageSettings(): UserLanguageSettings {
        return UserLanguageSettings(
            sourceLanguage = prefs.getString(KEY_SOURCE_LANG, "hi") ?: "hi",
            targetLanguage = prefs.getString(KEY_TARGET_LANG, "hi") ?: "hi",
            remoteLanguage = prefs.getString(KEY_REMOTE_LANG, "en") ?: "en",
            hearOriginalAlongsideTranslation = prefs.getBoolean(KEY_HEAR_ORIGINAL, false)
        )
    }

    fun saveLanguageSettings(settings: UserLanguageSettings) {
        prefs.edit()
            .putString(KEY_SOURCE_LANG, settings.sourceLanguage)
            .putString(KEY_TARGET_LANG, settings.targetLanguage)
            .putString(KEY_REMOTE_LANG, settings.remoteLanguage)
            .putBoolean(KEY_HEAR_ORIGINAL, settings.hearOriginalAlongsideTranslation)
            .apply()
    }

    fun getBackendHost(): String =
        BackendConfig.effectiveHost(prefs.getString(KEY_BACKEND_HOST, null))

    fun saveBackendHost(host: String) {
        prefs.edit()
            .putString(KEY_BACKEND_HOST, BackendConfig.normalizeHost(host))
            .apply()
    }

    fun hasAcceptedConsent(): Boolean = prefs.getBoolean(KEY_CONSENT_ACCEPTED, false)

    fun setConsentAccepted() {
        prefs.edit().putBoolean(KEY_CONSENT_ACCEPTED, true).apply()
    }

    fun getDisplayName(): String = prefs.getString(KEY_DISPLAY_NAME, "User") ?: "User"

    fun saveDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name.trim()).apply()
    }

    companion object {
        private const val PREFS_NAME = "live_translation_settings"
        private const val KEY_SOURCE_LANG = "source_language"
        private const val KEY_TARGET_LANG = "target_language"
        private const val KEY_REMOTE_LANG = "remote_language"
        private const val KEY_HEAR_ORIGINAL = "hear_original"
        private const val KEY_CONSENT_ACCEPTED = "consent_accepted"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_BACKEND_HOST = "backend_host"
    }
}
