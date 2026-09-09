package com.example.vivoicons

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.example.vivoicons.ui.AppRoot
import com.example.vivoicons.ui.PatchViewModel
import com.example.vivoicons.ui.settings.SettingsViewModel
import com.example.vivoicons.ui.theme.VivoiconsTheme

class MainActivity : ComponentActivity() {

    private val patchViewModel: PatchViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsViewModel.settings.collectAsState()
            VivoiconsTheme(
                darkMode = settings.darkMode,
                dynamicColor = settings.dynamicColor,
                seedColor = Color(settings.seedColor),
            ) {
                AppRoot(patchViewModel, settingsViewModel)
            }
        }
    }
}
