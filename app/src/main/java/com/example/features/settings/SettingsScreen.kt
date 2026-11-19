package com.example.features.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.reminders.NotificationPermissionStatus
import com.example.core.reminders.ReminderPermissionHelper
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppThemeMode
import com.example.domain.model.CalendarSystem

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  when (val state = uiState) {
    SettingsUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("settings_loading"))
      }
    }

    is SettingsUiState.Error -> {
      Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = state.message,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyLarge
        )
      }
    }

    is SettingsUiState.Success -> {
      val prefs = state.preferences
      val nowUtc = remember { System.currentTimeMillis() }
      val previewDate = remember(prefs.calendarSystem, prefs.language, prefs.defaultZoneId) {
        PersianCalendarEngine.formatFullDate(
          utcMillis = nowUtc,
          zoneId = prefs.defaultZoneId,
          calendarSystem = prefs.calendarSystem,
          language = prefs.language
        )
      }

      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
      ) {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp)
            .padding(horizontal = 16.dp)
            .testTag("settings_list"),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item { Spacer(modifier = Modifier.height(4.dp)) }

          item {
            SettingsSectionCard(
              icon = Icons.Outlined.Language,
              title = stringResource(R.string.settings_section_language)
            ) {
              Text(
                text = stringResource(R.string.settings_language_label),
                style = MaterialTheme.typography.titleSmall
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                FilterChip(
                  selected = prefs.language == AppLanguage.PERSIAN,
                  onClick = { viewModel.selectLanguage(AppLanguage.PERSIAN) },
                  label = { Text(stringResource(R.string.language_persian)) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("settings_language_persian")
                )
                FilterChip(
                  selected = prefs.language == AppLanguage.ENGLISH,
                  onClick = { viewModel.selectLanguage(AppLanguage.ENGLISH) },
                  label = { Text(stringResource(R.string.language_english)) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("settings_language_english")
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = stringResource(R.string.settings_language_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          item {
            SettingsSectionCard(
              icon = Icons.Outlined.DarkMode,
              title = stringResource(R.string.settings_section_appearance)
            ) {
              Text(
                text = stringResource(R.string.settings_theme_label),
                style = MaterialTheme.typography.titleSmall
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                FilterChip(
                  selected = prefs.themeMode == AppThemeMode.SYSTEM,
                  onClick = { viewModel.selectThemeMode(AppThemeMode.SYSTEM) },
                  label = { Text(stringResource(R.string.theme_system)) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("settings_theme_system")
                )
                FilterChip(
                  selected = prefs.themeMode == AppThemeMode.LIGHT,
                  onClick = { viewModel.selectThemeMode(AppThemeMode.LIGHT) },
                  label = { Text(stringResource(R.string.theme_light)) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("settings_theme_light")
                )
                FilterChip(
                  selected = prefs.themeMode == AppThemeMode.DARK,
                  onClick = { viewModel.selectThemeMode(AppThemeMode.DARK) },
                  label = { Text(stringResource(R.string.theme_dark)) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("settings_theme_dark")
                )
              }
            }
          }

          item {
            SettingsSectionCard(
              icon = Icons.Outlined.CalendarMonth,
              title = stringResource(R.string.settings_section_calendar)
            ) {
              Text(
                text = stringResource(R.string.settings_calendar_label),
                style = MaterialTheme.typography.titleSmall
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                FilterChip(
                  selected = prefs.calendarSystem == CalendarSystem.JALALI,
                  onClick = { viewModel.selectCalendarSystem(CalendarSystem.JALALI) },
                  label = { Text(stringResource(R.string.calendar_jalali)) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("settings_calendar_jalali")
                )
                FilterChip(
                  selected = prefs.calendarSystem == CalendarSystem.GREGORIAN,
                  onClick = { viewModel.selectCalendarSystem(CalendarSystem.GREGORIAN) },
                  label = { Text(stringResource(R.string.calendar_gregorian)) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("settings_calendar_gregorian")
                )
              }
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = stringResource(R.string.settings_preview_date_label, previewDate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("settings_calendar_preview")
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = stringResource(R.string.settings_calendar_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          item {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            var permissionStatus by remember {
              mutableStateOf(ReminderPermissionHelper.getNotificationPermissionStatus(context))
            }

            DisposableEffect(lifecycleOwner, context) {
              val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                  permissionStatus = ReminderPermissionHelper.getNotificationPermissionStatus(context)
                }
              }
              lifecycleOwner.lifecycle.addObserver(observer)
              onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
              contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
              permissionStatus = ReminderPermissionHelper.getNotificationPermissionStatus(context)
              if (granted) {
                viewModel.setNotificationsEnabled(true)
              }
            }

            SettingsSectionCard(
              icon = Icons.Outlined.Notifications,
              title = stringResource(R.string.settings_section_notifications)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = stringResource(R.string.settings_notifications_toggle),
                  style = MaterialTheme.typography.titleSmall,
                  modifier = Modifier.weight(1f)
                )
                Switch(
                  checked = prefs.notificationsEnabled,
                  onCheckedChange = { wantEnabled ->
                    viewModel.setNotificationsEnabled(wantEnabled)
                    if (wantEnabled &&
                      ReminderPermissionHelper.requiresRuntimePermission() &&
                      !ReminderPermissionHelper.hasPostNotificationsPermission(context)
                    ) {
                      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                  },
                  modifier = Modifier.testTag("settings_notifications_switch")
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Surface(
                color = when (permissionStatus) {
                  NotificationPermissionStatus.GRANTED -> MaterialTheme.colorScheme.primaryContainer
                  NotificationPermissionStatus.PERMISSION_REQUIRED,
                  NotificationPermissionStatus.SYSTEM_DISABLED -> MaterialTheme.colorScheme.errorContainer
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("settings_notification_status_badge")
              ) {
                Text(
                  text = stringResource(
                    when (permissionStatus) {
                      NotificationPermissionStatus.GRANTED -> R.string.settings_notification_status_granted
                      NotificationPermissionStatus.PERMISSION_REQUIRED -> R.string.settings_notification_status_permission_needed
                      NotificationPermissionStatus.SYSTEM_DISABLED -> R.string.settings_notification_status_system_disabled
                    }
                  ),
                  style = MaterialTheme.typography.labelMedium,
                  color = when (permissionStatus) {
                    NotificationPermissionStatus.GRANTED -> MaterialTheme.colorScheme.onPrimaryContainer
                    NotificationPermissionStatus.PERMISSION_REQUIRED,
                    NotificationPermissionStatus.SYSTEM_DISABLED -> MaterialTheme.colorScheme.onErrorContainer
                  },
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
              }

              if (permissionStatus != NotificationPermissionStatus.GRANTED) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                  onClick = {
                    if (permissionStatus == NotificationPermissionStatus.PERMISSION_REQUIRED &&
                      ReminderPermissionHelper.requiresRuntimePermission()
                    ) {
                      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                      runCatching {
                        context.startActivity(
                          ReminderPermissionHelper.createAppNotificationSettingsIntent(context)
                        )
                      }
                    }
                  },
                  modifier = Modifier.testTag("settings_notification_permission_button")
                ) {
                  Text(
                    stringResource(
                      if (permissionStatus == NotificationPermissionStatus.PERMISSION_REQUIRED) {
                        R.string.action_grant_notification_permission
                      } else {
                        R.string.action_open_notification_settings
                      }
                    )
                  )
                }
              }

              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = stringResource(R.string.settings_notifications_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          item {
            SettingsSectionCard(
              icon = Icons.Outlined.Shield,
              title = stringResource(R.string.settings_section_privacy)
            ) {
              Text(
                text = stringResource(R.string.settings_local_account_title),
                style = MaterialTheme.typography.titleSmall
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = stringResource(R.string.settings_local_account_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = stringResource(R.string.settings_ai_consent_label),
                  style = MaterialTheme.typography.titleSmall,
                  modifier = Modifier.weight(1f)
                )
                Switch(
                  checked = prefs.aiDataConsentGranted,
                  onCheckedChange = { viewModel.setAiDataConsentGranted(it) },
                  modifier = Modifier.testTag("settings_ai_consent_switch")
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = stringResource(
                  if (prefs.aiDataConsentGranted) {
                    R.string.settings_ai_consent_enabled
                  } else {
                    R.string.settings_ai_consent_disabled
                  }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (prefs.aiDataConsentGranted) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.testTag("settings_ai_consent_status")
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = stringResource(R.string.settings_ai_consent_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          item { Spacer(modifier = Modifier.height(20.dp)) }
        }
      }
    }
  }
}

@Composable
private fun SettingsSectionCard(
  icon: ImageVector,
  title: String,
  content: @Composable () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
      Spacer(modifier = Modifier.height(12.dp))
      content()
    }
  }
}
