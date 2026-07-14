package com.gnani.livetranslation.data

import com.gnani.livetranslation.BuildConfig
import com.gnani.livetranslation.util.DeviceUtils

object BackendConfig {
    fun normalizeHost(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return ""
        return trimmed
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")
    }

    fun resolveDefaultHost(): String {
        if (DeviceUtils.isEmulator()) {
            return DeviceUtils.EMULATOR_BACKEND_HOST
        }
        val fromBuild = normalizeHost(BuildConfig.BACKEND_HOST)
        if (fromBuild.isNotBlank() && fromBuild != DeviceUtils.EMULATOR_BACKEND_HOST) {
            return fromBuild
        }
        return ""
    }

    fun effectiveHost(saved: String?): String {
        val normalized = normalizeHost(saved)
        if (normalized.isNotBlank()) {
            if (!DeviceUtils.isEmulator() && normalized == DeviceUtils.EMULATOR_BACKEND_HOST) {
                return resolveDefaultHost()
            }
            return normalized
        }
        return resolveDefaultHost()
    }

    fun isConfigured(host: String): Boolean = normalizeHost(host).isNotBlank()

    fun httpBaseUrl(host: String): String = "http://${normalizeHost(host)}"

    fun wsBaseUrl(host: String): String = "ws://${normalizeHost(host)}"
}
