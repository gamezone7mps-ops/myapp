package com.example.core.reminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

enum class NotificationPermissionStatus {
  GRANTED,
  PERMISSION_REQUIRED,
  SYSTEM_DISABLED
}

object ReminderPermissionHelper {

  fun requiresRuntimePermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

  fun hasPostNotificationsPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun getNotificationPermissionStatus(context: Context): NotificationPermissionStatus {
    if (!hasPostNotificationsPermission(context)) {
      return NotificationPermissionStatus.PERMISSION_REQUIRED
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
      return NotificationPermissionStatus.SYSTEM_DISABLED
    }
    return NotificationPermissionStatus.GRANTED
  }

  fun createAppNotificationSettingsIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } else {
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  }
}
