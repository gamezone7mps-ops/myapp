package com.example.core.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.database.AppDatabase
import com.example.core.datastore.DataStorePreferencesRepository
import com.example.domain.model.TaskStatus
import java.util.Locale
import kotlinx.coroutines.flow.first

class ReminderWorker(
  appContext: Context,
  params: WorkerParameters
) : CoroutineWorker(appContext, params) {

  @SuppressLint("MissingPermission")
  override suspend fun doWork(): Result {
    val targetType = ReminderTargetType.fromKey(inputData.getString(KEY_TARGET_TYPE))
      ?: return Result.failure()
    val targetId = inputData.getString(KEY_TARGET_ID)?.takeIf { it.isNotBlank() }
      ?: return Result.failure()

    val prefs = DataStorePreferencesRepository(applicationContext).userPreferences.first()
    if (!prefs.notificationsEnabled) {
      return Result.success()
    }
    if (!ReminderPermissionHelper.hasPostNotificationsPermission(applicationContext)) {
      return Result.success()
    }

    val localizedContext = localizedContext(applicationContext, prefs.language.code)
    val database = AppDatabase.getInstance(applicationContext)

    val notificationContent: Pair<String, String> = when (targetType) {
      ReminderTargetType.TASK -> {
        val task = database.taskDao().getById(targetId) ?: return Result.success()
        val status = TaskStatus.fromKey(task.status)
        if (task.isDeleted || status == TaskStatus.COMPLETED || status == TaskStatus.ARCHIVED) {
          return Result.success()
        }
        if (task.dueAtUtcMillis == null || task.reminderMinutesBefore == null) {
          return Result.success()
        }
        val formattedTime = PersianCalendarEngine.formatDateTime(
          utcMillis = task.dueAtUtcMillis,
          zoneId = prefs.defaultZoneId,
          calendarSystem = prefs.calendarSystem,
          language = prefs.language
        )
        val title = localizedContext.getString(R.string.notification_task_title, task.title)
        val body = localizedContext.getString(R.string.notification_task_body, formattedTime)
        title to body
      }

      ReminderTargetType.EVENT -> {
        val event = database.calendarEventDao().getById(targetId) ?: return Result.success()
        if (event.isDeleted || event.reminderMinutesBefore == null) {
          return Result.success()
        }
        val formattedTime = PersianCalendarEngine.formatDateTime(
          utcMillis = event.startAtUtcMillis,
          zoneId = event.zoneId,
          calendarSystem = prefs.calendarSystem,
          language = prefs.language
        )
        val title = localizedContext.getString(R.string.notification_event_title, event.title)
        val body = if (!event.location.isNullOrBlank()) {
          localizedContext.getString(
            R.string.notification_event_body_with_location,
            formattedTime,
            event.location
          )
        } else {
          localizedContext.getString(R.string.notification_event_body, formattedTime)
        }
        title to body
      }
    }

    ensureNotificationChannel(localizedContext)

    val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_TARGET_TYPE, targetType.key)
      putExtra(EXTRA_TARGET_ID, targetId)
    }
    val pendingIntent = PendingIntent.getActivity(
      applicationContext,
      ReminderScheduleCalculator.notificationId(targetType, targetId),
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(localizedContext, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification_reminder)
      .setContentTitle(notificationContent.first)
      .setContentText(notificationContent.second)
      .setStyle(NotificationCompat.BigTextStyle().bigText(notificationContent.second))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)
      .build()

    NotificationManagerCompat.from(applicationContext).notify(
      ReminderScheduleCalculator.notificationId(targetType, targetId),
      notification
    )

    return Result.success()
  }

  private fun localizedContext(base: Context, langCode: String): Context {
    val locale = Locale(langCode)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
  }

  private fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = context.getString(R.string.notification_channel_desc)
      }
      manager.createNotificationChannel(channel)
    }
  }

  companion object {
    const val CHANNEL_ID = "lifeos_reminders"
    const val KEY_TARGET_TYPE = "target_type"
    const val KEY_TARGET_ID = "target_id"
    const val EXTRA_TARGET_TYPE = "lifeos_reminder_target_type"
    const val EXTRA_TARGET_ID = "lifeos_reminder_target_id"
  }
}
