package com.gnani.livetranslation.data

data class Language(
    val code: String,
    val displayName: String
)

object SupportedLanguages {
    val all = listOf(
        Language("en", "English"),
        Language("hi", "Hindi"),
        Language("es", "Spanish"),
        Language("fr", "French"),
        Language("de", "German"),
        Language("ja", "Japanese"),
        Language("ko", "Korean"),
        Language("zh", "Chinese (Mandarin)"),
        Language("ar", "Arabic"),
        Language("pt", "Portuguese"),
        Language("ru", "Russian"),
        Language("ta", "Tamil"),
        Language("te", "Telugu"),
        Language("bn", "Bengali"),
        Language("mr", "Marathi")
    )

    fun findByCode(code: String): Language? = all.find { it.code == code }
}

data class UserLanguageSettings(
    val sourceLanguage: String = "hi",
    val targetLanguage: String = "hi",
    val remoteLanguage: String = "en",
    val hearOriginalAlongsideTranslation: Boolean = false
)

enum class CaptionDirection {
    INCOMING, OUTGOING, UNKNOWN
}

data class CaptionEntry(
    val originalText: String,
    val translatedText: String,
    val isFinal: Boolean,
    val direction: CaptionDirection = CaptionDirection.UNKNOWN,
    val label: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
