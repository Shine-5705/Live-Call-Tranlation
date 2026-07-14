package com.gnani.livetranslation.dial

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gnani.livetranslation.captions.CaptionOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialScreen(
    onNavigateBack: () -> Unit,
    viewModel: DialViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) onNavigateBack()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call & Translate") },
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
            LanguageCard(
                myLang = viewModel.languageLabel(uiState.sourceLanguage),
                theirLang = viewModel.languageLabel(uiState.remoteLanguage),
                hearLang = viewModel.languageLabel(uiState.listenLanguage)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.callState == DialCallState.IDLE ||
                uiState.callState == DialCallState.ENDED ||
                uiState.callState == DialCallState.ERROR
            ) {
                OutlinedTextField(
                    value = uiState.phoneNumber,
                    onValueChange = viewModel::setPhoneNumber,
                    label = { Text("Phone number") },
                    placeholder = { Text("+919876543210") },
                    supportingText = { Text("Include country code (E.164). Other person needs no app.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = viewModel::startCall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Text("  Call with Translation")
                }
                if (uiState.callState == DialCallState.ENDED || uiState.callState == DialCallState.ERROR) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = viewModel::resetToIdle, modifier = Modifier.fillMaxWidth()) {
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
            uiState.errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            if (uiState.callState == DialCallState.IN_CALL) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Calling ${uiState.phoneNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (uiState.callState == DialCallState.IN_CALL) {
                CaptionOverlay(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
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
                        onClick = viewModel::endCall,
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
private fun LanguageCard(myLang: String, theirLang: String, hearLang: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Translation setup", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("You speak: $myLang")
            Text("They speak: $theirLang")
            Text("You hear: $hearLang")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Only translated audio is passed between you — raw voice is not relayed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
