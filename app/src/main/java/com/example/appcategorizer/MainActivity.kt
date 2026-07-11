package com.example.appcategorizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcategorizer.ui.settings.SettingsViewModel
import com.example.appcategorizer.theme.ThemePref
import androidx.compose.ui.Modifier
import com.example.appcategorizer.theme.AppCategorizerTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
        // Obtain SettingsViewModel (Hilt provides it)
        val settingsViewModel: SettingsViewModel = viewModel()
        val themePrefString by settingsViewModel.themePreference.collectAsState(initial = "System")
        val themePref = when (themePrefString) {
            "Dark" -> ThemePref.DARK
            "Light" -> ThemePref.LIGHT
            else -> ThemePref.SYSTEM
        }
        AppCategorizerTheme(themePref = themePref) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MainNavigation()
            }
        }
    }
  }
}
