package com.gnani.livetranslation.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object BackendConnectionTester {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun test(host: String): Result<String> = withContext(Dispatchers.IO) {
        val normalized = BackendConfig.normalizeHost(host).ifBlank {
            BackendConfig.resolveDefaultHost()
        }
        if (!BackendConfig.isConfigured(normalized)) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter backend host (e.g. 192.168.1.2:3000)")
            )
        }
        try {
            val request = Request.Builder()
                .url("${BackendConfig.httpBaseUrl(normalized)}/health")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Connected to $normalized")
                } else {
                    Result.failure(Exception("Server returned ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    "Cannot reach $normalized. " +
                        "On your Mac run: ipconfig getifaddr en0 — use that IP:3000. " +
                        "Test in phone browser: http://$normalized/health",
                    e
                )
            )
        }
    }
}
