package com.example.features.common

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.core.reminders.NotificationPermissionStatus
import com.example.core.reminders.ReminderPermissionHelper

@Composable
fun ReminderPermissionHintBanner(
  notificationsPreferenceEnabled: Boolean,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var status by remember {
    mutableStateOf(ReminderPermissionHelper.getNotificationPermissionStatus(context))
  }

  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        status = ReminderPermissionHelper.getNotificationPermissionStatus(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) {
    status = ReminderPermissionHelper.getNotificationPermissionStatus(context)
  }

  if (!notificationsPreferenceEnabled) {
    Surface(
      color = MaterialTheme.colorScheme.secondaryContainer,
      shape = MaterialTheme.shapes.small,
      modifier = modifier.fillMaxWidth()
    ) {
      Text(
        text = stringResource(R.string.reminder_pref_disabled_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(10.dp)
      )
    }
    return
  }

  if (status != NotificationPermissionStatus.GRANTED) {
    Surface(
      color = MaterialTheme.colorScheme.errorContainer,
      shape = MaterialTheme.shapes.small,
      modifier = modifier
        .fillMaxWidth()
        .testTag("reminder_permission_banner")
    ) {
      Column(
        modifier = Modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.NotificationsOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = stringResource(
              if (status == NotificationPermissionStatus.PERMISSION_REQUIRED) {
                R.string.reminder_permission_needed_hint
              } else {
                R.string.reminder_system_disabled_hint
              }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
          )
        }
        TextButton(
          onClick = {
            if (status == NotificationPermissionStatus.PERMISSION_REQUIRED &&
              ReminderPermissionHelper.requiresRuntimePermission()
            ) {
              permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
              runCatching {
                val intent: Intent = ReminderPermissionHelper.createAppNotificationSettingsIntent(context)
                context.startActivity(intent)
              }
            }
          },
          modifier = Modifier.testTag("reminder_permission_action")
        ) {
          Text(
            text = stringResource(
              if (status == NotificationPermissionStatus.PERMISSION_REQUIRED) {
                R.string.action_grant_notification_permission
              } else {
                R.string.action_open_notification_settings
              }
            )
          )
        }
      }
    }
  }
}
