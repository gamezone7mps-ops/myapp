package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ai.configureAppCheck
import com.example.core.common.DefaultLifeOsContainer
import com.example.core.designsystem.LifeOsTheme
import com.example.core.localization.ProvideLifeOsLocale
import com.example.domain.model.UserPreferences

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()

    setContent {
      val container = remember { DefaultLifeOsContainer(applicationContext) }
      val preferences by container.preferencesRepository
        .userPreferences
        .collectAsStateWithLifecycle(initialValue = UserPreferences())

      ProvideLifeOsLocale(language = preferences.language) {
        LifeOsTheme(
          themeMode = preferences.themeMode,
          language = preferences.language
        ) {
          LifeOsApp(
            container = container,
            preferences = preferences
          )
        }
      }
    }
  }
}
