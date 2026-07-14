package com.gnani.livetranslation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gnani.livetranslation.data.BackendConfig
import com.gnani.livetranslation.data.SupportedLanguages
import com.gnani.livetranslation.data.UserLanguageSettings
import com.gnani.livetranslation.util.DeviceUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserLanguageSettings,
    displayName: String,
    backendHost: String,
    onSave: (UserLanguageSettings, String, String) -> Unit,
    onNavigateToDial: () -> Unit,
    onNavigateToInAppCall: () -> Unit
) {
    var sourceLang by remember(settings) { mutableStateOf(settings.sourceLanguage) }
    var targetLang by remember(settings) { mutableStateOf(settings.targetLanguage) }
    var remoteLang by remember(settings) { mutableStateOf(settings.remoteLanguage) }
    var name by remember(displayName) { mutableStateOf(displayName) }
    var serverHost by remember(backendHost) { mutableStateOf(backendHost) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "AI Call Translator",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Call any real phone number. The person who answers needs no app — " +
                "your backend translates the conversation in real time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your display name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = serverHost,
            onValueChange = { serverHost = it },
            label = { Text("Backend server (host:port)") },
            placeholder = {
                Text(
                    if (DeviceUtils.isEmulator()) DeviceUtils.EMULATOR_BACKEND_HOST
                    else BackendConfig.resolveDefaultHost().ifBlank { "192.168.1.3:3000" }
                )
            },
            supportingText = {
                Text(
                    if (DeviceUtils.isEmulator()) {
                        "Emulator: use 10.0.2.2:3000 (your Mac's localhost)"
                    } else {
                        "Physical phone: use your Mac's Wi-Fi IP, e.g. 192.168.1.3:3000 — not 10.0.2.2"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        LanguageDropdown(
            label = "My language (what I speak)",
            selectedCode = sourceLang,
            onSelected = { sourceLang = it }
        )
        Spacer(modifier = Modifier.height(16.dp))

        LanguageDropdown(
            label = "Their language (person on phone)",
            selectedCode = remoteLang,
            onSelected = { remoteLang = it }
        )
        Spacer(modifier = Modifier.height(16.dp))

        LanguageDropdown(
            label = "What I want to hear",
            selectedCode = targetLang,
            onSelected = { targetLang = it }
        )

        validationError?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (sourceLang == remoteLang) {
                    validationError = "Your language and their language must differ"
                    return@Button
                }
                val host = BackendConfig.normalizeHost(serverHost).ifBlank {
                    BackendConfig.resolveDefaultHost()
                }
                if (!BackendConfig.isConfigured(host)) {
                    validationError = "Backend server is required (e.g. 192.168.1.3:3000)"
                    return@Button
                }
                if (!DeviceUtils.isEmulator() && host == DeviceUtils.EMULATOR_BACKEND_HOST) {
                    validationError = "10.0.2.2 only works on emulator. Use your Mac's IP."
                    return@Button
                }
                validationError = null
                onSave(
                    UserLanguageSettings(sourceLang, targetLang, remoteLang, false),
                    name,
                    host
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Settings")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Start a call", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onNavigateToDial, modifier = Modifier.fillMaxWidth()) {
            Text("Call a Phone Number")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Dial any real number. They hear only translated audio. AI disclosure plays at connect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onNavigateToInAppCall, modifier = Modifier.fillMaxWidth()) {
            Text("In-App Call (legacy — both need app)")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    label: String,
    selectedCode: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = SupportedLanguages.findByCode(selectedCode)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        TextField(
            value = selected?.displayName ?: selectedCode,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SupportedLanguages.all.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.displayName) },
                    onClick = {
                        onSelected(lang.code)
                        expanded = false
                    }
                )
            }
        }
    }
}
