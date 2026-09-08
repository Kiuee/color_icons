package com.example.vivoicons

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.vivoicons.ui.AppRoot
import com.example.vivoicons.ui.PatchViewModel
import com.example.vivoicons.ui.theme.VivoiconsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VivoiconsTheme {
                AppRoot(viewModel)
            }
        }
    }
}
