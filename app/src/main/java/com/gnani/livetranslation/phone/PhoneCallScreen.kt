package com.gnani.livetranslation.phone

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
fun PhoneCallScreen(
    autoStart: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
    onNavigateBack: () -> Unit,
    viewModel: PhoneCallViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] != true) {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.READ_PHONE_STATE)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(autoStart) {
        if (autoStart && uiState.state == PhoneTranslationState.IDLE) {
            viewModel.startTranslation()
            onAutoStartConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Call Translator") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopTranslation()
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
            PhoneSetupCard(
                myLanguage = viewModel.languageLabel(uiState.sourceLanguage),
                theirLanguage = viewModel.languageLabel(uiState.remoteLanguage),
                hearLanguage = viewModel.languageLabel(uiState.listenLanguage)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LimitationCard()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }

            if (uiState.translationActive) {
                Spacer(modifier = Modifier.height(8.dp))
                RowWithIcon(
                    text = "Listening to your call…",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (uiState.state == PhoneTranslationState.ACTIVE) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    CaptionOverlay(modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            when (uiState.state) {
                PhoneTranslationState.IDLE,
                PhoneTranslationState.STOPPED,
                PhoneTranslationState.ERROR -> {
                    Button(
                        onClick = viewModel::startTranslation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Text("  Start Phone Translation")
                    }
                    if (uiState.state == PhoneTranslationState.STOPPED ||
                        uiState.state == PhoneTranslationState.ERROR
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::resetToIdle,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset")
                        }
                    }
                }
                PhoneTranslationState.STARTING -> {
                    Text("Connecting…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PhoneTranslationState.ACTIVE -> {
                    FloatingActionButton(
                        onClick = viewModel::stopTranslation,
                        shape = CircleShape,
                        containerColor = Color(0xFFE53935)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop translation",
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
private fun RowWithIcon(text: String, color: Color) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Translate,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "  $text",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PhoneSetupCard(myLanguage: String, theirLanguage: String, hearLanguage: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Your setup", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("You speak: $myLanguage")
            Text("Other person speaks: $theirLanguage")
            Text("You hear translations in: $hearLanguage")
        }
    }
}

@Composable
private fun LimitationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("How to use", fontWeight = FontWeight.SemiBold)
            Text(
                "1. Make your regular phone call first (Phone app)",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "2. Turn on speakerphone",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "3. Tap Start — you'll hear translations of what they say",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "4. When you speak, the app shows what to say in their language",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Note: Android cannot inject audio into carrier calls. " +
                    "The other person won't hear translated audio automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
