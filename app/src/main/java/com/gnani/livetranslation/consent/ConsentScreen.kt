package com.gnani.livetranslation.consent

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ConsentScreen(onAccept: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Consent Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Before you use AI Call Translator, please read and accept:",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(12.dp))
                ConsentBullet("You can call real phone numbers. The other person does not need this app.")
                ConsentBullet("Calls are placed via a telephony provider (Twilio). PSTN calls incur per-minute charges.")
                ConsentBullet("Your voice and the other party's voice are sent to third-party AI services (speech-to-text, translation, text-to-speech) in real time.")
                ConsentBullet("The person you call hears an AI disclosure message when they answer, since they have no app to consent through.")
                ConsentBullet("Only translated audio is relayed between parties — raw untranslated voice is not passed through.")
                ConsentBullet("Audio is processed during the live session only and is not stored unless you opt in to call history (not enabled).")
                ConsentBullet("You are responsible for complying with telecom and recording consent laws in your jurisdiction.")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "By tapping Accept, you confirm you understand these terms and have authority to place translated calls.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text("I Accept")
        }
    }
}

@Composable
private fun ConsentBullet(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
