package com.example.appcategorizer

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.appcategorizer.ui.main.MainScreen
import com.example.appcategorizer.ui.main.CategoryDetailScreen
import com.example.appcategorizer.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onNavigateToSettings = { backStack.add(Settings) },
            onNavigateToCategory = { parentCategory -> backStack.add(CategoryDetail(parentCategory)) }
          )
        }
        entry<Settings> {
          SettingsScreen(onBack = {
            backStack.removeLastOrNull()
          })
        }
        entry<CategoryDetail> {
          CategoryDetailScreen(
            parentCategory = it.parentCategory,
            onBack = { backStack.removeLastOrNull() }
          )
        }
      },
  )
}
