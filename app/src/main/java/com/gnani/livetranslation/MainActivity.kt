package com.gnani.livetranslation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gnani.livetranslation.call.CallScreen
import com.gnani.livetranslation.consent.ConsentScreen
import com.gnani.livetranslation.dial.DialScreen
import com.gnani.livetranslation.service.TranslationForegroundService
import com.gnani.livetranslation.settings.SettingsRepository
import com.gnani.livetranslation.settings.SettingsScreen
import com.gnani.livetranslation.ui.theme.LiveTranslationTheme

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private var pendingSessionId by mutableStateOf<String?>(null)
    private var pendingMode by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsRepository = SettingsRepository(this)
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            LiveTranslationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val startDestination = if (settingsRepository.hasAcceptedConsent()) {
                        "settings"
                    } else {
                        "consent"
                    }

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("consent") {
                            ConsentScreen(
                                onAccept = {
                                    settingsRepository.setConsentAccepted()
                                    navController.navigate("settings") {
                                        popUpTo("consent") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("settings") {
                            val settings = settingsRepository.getLanguageSettings()
                            val displayName = settingsRepository.getDisplayName()
                            val backendHost = settingsRepository.getBackendHost()
                            SettingsScreen(
                                settings = settings,
                                displayName = displayName,
                                backendHost = backendHost,
                                onSave = { newSettings, name, host ->
                                    settingsRepository.saveLanguageSettings(newSettings)
                                    settingsRepository.saveDisplayName(name)
                                    settingsRepository.saveBackendHost(host)
                                },
                                onNavigateToDial = {
                                    navController.navigate("dial")
                                },
                                onNavigateToInAppCall = {
                                    navController.navigate("call")
                                }
                            )
                        }
                        composable("dial") {
                            DialScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("call") {
                            CallScreen(
                                pendingRoomId = pendingSessionId,
                                onRoomIdConsumed = { pendingSessionId = null },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }

                    LaunchedEffectNavigation(
                        pendingMode = pendingMode,
                        navController = navController,
                        onConsumed = { pendingMode = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val sessionId = intent?.getStringExtra(TranslationForegroundService.EXTRA_ROOM_ID)
        if (!sessionId.isNullOrBlank()) {
            pendingSessionId = sessionId
        }
        val mode = intent?.getStringExtra(TranslationForegroundService.EXTRA_MODE)
        if (mode == "twilio") {
            pendingMode = "dial"
        } else if (mode == TranslationForegroundService.MODE_WEBRTC) {
            pendingMode = "call"
        }
    }
}

@androidx.compose.runtime.Composable
private fun LaunchedEffectNavigation(
    pendingMode: String?,
    navController: androidx.navigation.NavHostController,
    onConsumed: () -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(pendingMode) {
        when (pendingMode) {
            "dial" -> {
                navController.navigate("dial")
                onConsumed()
            }
            "call" -> {
                navController.navigate("call")
                onConsumed()
            }
        }
    }
}
