package com.gnani.livetranslation.captions

import com.gnani.livetranslation.data.CaptionDirection
import com.gnani.livetranslation.data.CaptionEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object CaptionStateHolder {
    private val _captions = MutableStateFlow<List<CaptionEntry>>(emptyList())
    val captions: StateFlow<List<CaptionEntry>> = _captions.asStateFlow()

    private val _interimOriginal = MutableStateFlow("")
    val interimOriginal: StateFlow<String> = _interimOriginal.asStateFlow()

    private val _interimTranslated = MutableStateFlow("")
    val interimTranslated: StateFlow<String> = _interimTranslated.asStateFlow()

    private val _interimDirection = MutableStateFlow(CaptionDirection.UNKNOWN)
    val interimDirection: StateFlow<CaptionDirection> = _interimDirection.asStateFlow()

    fun addOrUpdateCaption(entry: CaptionEntry) {
        if (entry.isFinal) {
            _interimOriginal.value = ""
            _interimTranslated.value = ""
            _interimDirection.value = CaptionDirection.UNKNOWN
            _captions.update { current ->
                (current + entry).takeLast(50)
            }
        } else {
            _interimOriginal.value = entry.originalText
            _interimTranslated.value = entry.translatedText
            _interimDirection.value = entry.direction
        }
    }

    fun clear() {
        _captions.value = emptyList()
        _interimOriginal.value = ""
        _interimTranslated.value = ""
        _interimDirection.value = CaptionDirection.UNKNOWN
    }
}
