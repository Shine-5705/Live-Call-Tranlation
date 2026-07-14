package com.gnani.livetranslation.data

import com.gnani.livetranslation.BuildConfig

object BackendConfig {
    private const val DEFAULT_HOST = BuildConfig.BACKEND_HOST

    fun normalizeHost(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return DEFAULT_HOST
        return trimmed
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")
    }

    fun httpBaseUrl(host: String): String = "http://$host"

    fun wsBaseUrl(host: String): String = "ws://$host"
}
