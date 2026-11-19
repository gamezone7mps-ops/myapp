package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppThemeMode
import com.example.domain.model.CalendarSystem
import com.example.domain.model.UserPreferences
import java.io.IOException
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.lifeOsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "lifeos_user_preferences"
)

interface PreferencesRepository {
  val userPreferences: Flow<UserPreferences>
  suspend fun setLanguage(language: AppLanguage)
  suspend fun setThemeMode(themeMode: AppThemeMode)
  suspend fun setCalendarSystem(calendarSystem: CalendarSystem)
  suspend fun setNotificationsEnabled(enabled: Boolean)
  suspend fun setAiDataConsentGranted(granted: Boolean)
}

class DataStorePreferencesRepository(
  private val context: Context
) : PreferencesRepository {

  private object Keys {
    val LANGUAGE = stringPreferencesKey("app_language")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val CALENDAR_SYSTEM = stringPreferencesKey("calendar_system")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val AI_DATA_CONSENT = booleanPreferencesKey("ai_data_consent_granted")
    val DEFAULT_ZONE_ID = stringPreferencesKey("default_zone_id")
  }

  override val userPreferences: Flow<UserPreferences> =
    context.lifeOsDataStore.data
      .catch { exception ->
        if (exception is IOException) {
          emit(emptyPreferences())
        } else {
          throw exception
        }
      }
      .map { prefs ->
        UserPreferences(
          language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
          themeMode = AppThemeMode.fromKey(prefs[Keys.THEME_MODE]),
          calendarSystem = CalendarSystem.fromKey(prefs[Keys.CALENDAR_SYSTEM]),
          notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
          aiDataConsentGranted = prefs[Keys.AI_DATA_CONSENT] ?: false,
          defaultZoneId = prefs[Keys.DEFAULT_ZONE_ID] ?: TimeZone.getDefault().id
        )
      }

  override suspend fun setLanguage(language: AppLanguage) {
    context.lifeOsDataStore.edit { it[Keys.LANGUAGE] = language.code }
  }

  override suspend fun setThemeMode(themeMode: AppThemeMode) {
    context.lifeOsDataStore.edit { it[Keys.THEME_MODE] = themeMode.key }
  }

  override suspend fun setCalendarSystem(calendarSystem: CalendarSystem) {
    context.lifeOsDataStore.edit { it[Keys.CALENDAR_SYSTEM] = calendarSystem.key }
  }

  override suspend fun setNotificationsEnabled(enabled: Boolean) {
    context.lifeOsDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
  }

  override suspend fun setAiDataConsentGranted(granted: Boolean) {
    context.lifeOsDataStore.edit { it[Keys.AI_DATA_CONSENT] = granted }
  }
}
