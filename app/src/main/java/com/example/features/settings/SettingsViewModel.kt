package com.example.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.PreferencesRepository
import com.example.core.reminders.NoOpReminderScheduler
import com.example.core.reminders.ReminderScheduler
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppThemeMode
import com.example.domain.model.CalendarSystem
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
  data object Loading : SettingsUiState
  data class Success(val preferences: UserPreferences) : SettingsUiState
  data class Error(val message: String) : SettingsUiState
}

class SettingsViewModel(
  private val preferencesRepository: PreferencesRepository,
  private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler
) : ViewModel() {

  val uiState: StateFlow<SettingsUiState> = preferencesRepository.userPreferences
    .map<UserPreferences, SettingsUiState> { SettingsUiState.Success(it) }
    .catch { emit(SettingsUiState.Error(it.localizedMessage ?: "Settings error")) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = SettingsUiState.Loading
    )

  fun selectLanguage(language: AppLanguage) {
    viewModelScope.launch {
      preferencesRepository.setLanguage(language)
    }
  }

  fun selectThemeMode(mode: AppThemeMode) {
    viewModelScope.launch {
      preferencesRepository.setThemeMode(mode)
    }
  }

  fun selectCalendarSystem(calendarSystem: CalendarSystem) {
    viewModelScope.launch {
      preferencesRepository.setCalendarSystem(calendarSystem)
    }
  }

  fun setNotificationsEnabled(enabled: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setNotificationsEnabled(enabled)
      if (enabled) {
        reminderScheduler.rescheduleAllActiveReminders()
      } else {
        reminderScheduler.cancelAllReminders()
      }
    }
  }

  fun setAiDataConsentGranted(granted: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setAiDataConsentGranted(granted)
    }
  }

  class Factory(
    private val preferencesRepository: PreferencesRepository,
    private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SettingsViewModel(preferencesRepository, reminderScheduler) as T
    }
  }
}
