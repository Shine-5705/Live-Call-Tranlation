package com.gnani.livetranslation.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gnani.livetranslation.captions.CaptionOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    pendingRoomId: String? = null,
    onRoomIdConsumed: () -> Unit = {},
    onNavigateBack: () -> Unit,
    viewModel: CallViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(pendingRoomId) {
        if (!pendingRoomId.isNullOrBlank()) {
            viewModel.setRoomId(pendingRoomId)
            onRoomIdConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Call Translator") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endCall()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LanguageConfigCard(
                sourceLabel = viewModel.languageLabel(uiState.sourceLanguage),
                targetLabel = viewModel.languageLabel(uiState.targetLanguage)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.callState == CallState.IDLE ||
                uiState.callState == CallState.ENDED ||
                uiState.callState == CallState.ERROR
            ) {
                OutlinedTextField(
                    value = uiState.roomId,
                    onValueChange = viewModel::setRoomId,
                    label = { Text("Room ID") },
                    placeholder = { Text("e.g. meeting-123") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.startCall(asInitiator = true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Text("  Create Call")
                    }
                    Button(
                        onClick = { viewModel.startCall(asInitiator = false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Join Call")
                    }
                }
                if (uiState.callState == CallState.ENDED || uiState.callState == CallState.ERROR) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.resetToIdle() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("New Call")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            uiState.peerDisplayName?.let { name ->
                Text(
                    text = "With: $name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }

            if (uiState.callState != CallState.IDLE) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Room: ${uiState.roomId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.translationActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "  Translation active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (uiState.callState == CallState.IN_CALL) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    CaptionOverlay(modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (uiState.callState == CallState.IN_CALL ||
                uiState.callState == CallState.WAITING_FOR_PEER ||
                uiState.callState == CallState.CONNECTING
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingActionButton(
                        onClick = viewModel::toggleMute,
                        shape = CircleShape,
                        containerColor = if (uiState.isMuted) Color.Gray else MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            viewModel.endCall()
                            onNavigateBack()
                        },
                        shape = CircleShape,
                        containerColor = Color(0xFFE53935)
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "End call",
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LanguageConfigCard(sourceLabel: String, targetLabel: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your translation setup",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You speak: $sourceLabel",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "You hear: $targetLabel",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "The other person hears you translated into their target language.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
