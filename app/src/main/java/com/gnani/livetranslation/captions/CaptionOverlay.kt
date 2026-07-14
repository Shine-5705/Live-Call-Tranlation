package com.gnani.livetranslation.captions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gnani.livetranslation.data.CaptionDirection
import com.gnani.livetranslation.data.CaptionEntry

@Composable
fun CaptionOverlay(modifier: Modifier = Modifier) {
    val captions by CaptionStateHolder.captions.collectAsState()
    val interimOriginal by CaptionStateHolder.interimOriginal.collectAsState()
    val interimTranslated by CaptionStateHolder.interimTranslated.collectAsState()
    val interimDirection by CaptionStateHolder.interimDirection.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(captions.size, interimTranslated) {
        if (captions.isNotEmpty() || interimTranslated.isNotEmpty()) {
            listState.animateScrollToItem(
                maxOf(0, captions.size + if (interimTranslated.isNotEmpty()) 1 else 0) - 1
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.75f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(captions, key = { it.timestampMs }) { entry ->
                CaptionItem(entry)
            }
            if (interimTranslated.isNotEmpty() || interimOriginal.isNotEmpty()) {
                item {
                    CaptionItem(
                        CaptionEntry(
                            originalText = interimOriginal,
                            translatedText = interimTranslated,
                            isFinal = false,
                            direction = interimDirection
                        ),
                        isInterim = true
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptionItem(entry: CaptionEntry, isInterim: Boolean = false) {
    val directionLabel = when (entry.direction) {
        CaptionDirection.INCOMING -> "They said"
        CaptionDirection.OUTGOING -> entry.label ?: "Say to them"
        CaptionDirection.UNKNOWN -> null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        directionLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = when (entry.direction) {
                    CaptionDirection.OUTGOING -> Color(0xFFFFD54F)
                    else -> Color(0xFF81D4FA)
                }.copy(alpha = if (isInterim) 0.7f else 1f),
                fontWeight = FontWeight.SemiBold
            )
        }
        if (entry.originalText.isNotBlank()) {
            Text(
                text = entry.originalText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = if (isInterim) 0.5f else 0.6f)
            )
        }
        if (entry.translatedText.isNotBlank()) {
            Text(
                text = entry.translatedText + if (isInterim) " …" else "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = if (isInterim) 0.85f else 1f)
            )
        }
    }
}
