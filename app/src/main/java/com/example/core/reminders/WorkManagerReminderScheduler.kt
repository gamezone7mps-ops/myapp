package com.example.core.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.core.database.CalendarEventDao
import com.example.core.database.TaskDao
import com.example.core.datastore.PreferencesRepository
import com.example.data.local.toDomain
import com.example.domain.model.CalendarEvent
import com.example.domain.model.TaskItem
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class WorkManagerReminderScheduler(
  private val context: Context,
  private val preferencesRepository: PreferencesRepository,
  private val taskDao: TaskDao,
  private val calendarEventDao: CalendarEventDao,
  private val workManagerProvider: () -> WorkManager = { WorkManager.getInstance(context) },
  private val nowUtcMillisProvider: () -> Long = { System.currentTimeMillis() }
) : ReminderScheduler {

  override suspend fun scheduleTaskReminder(task: TaskItem) {
    val wm = workManagerOrNull() ?: return
    val workName = ReminderScheduleCalculator.uniqueWorkName(ReminderTargetType.TASK, task.id)
    val prefs = preferencesRepository.userPreferences.first()
    if (!prefs.notificationsEnabled) {
      runCatching { wm.cancelUniqueWork(workName) }
      return
    }
    val now = nowUtcMillisProvider()
    val triggerAt = ReminderScheduleCalculator.computeTaskTriggerAtUtcMillis(task, now)
    if (triggerAt == null) {
      runCatching { wm.cancelUniqueWork(workName) }
      return
    }
    enqueueUniqueReminder(
      workManager = wm,
      workName = workName,
      targetType = ReminderTargetType.TASK,
      targetId = task.id,
      delayMillis = (triggerAt - now).coerceAtLeast(0L)
    )
  }

  override suspend fun cancelTaskReminder(taskId: String) {
    val wm = workManagerOrNull() ?: return
    val workName = ReminderScheduleCalculator.uniqueWorkName(ReminderTargetType.TASK, taskId)
    runCatching { wm.cancelUniqueWork(workName) }
  }

  override suspend fun scheduleEventReminder(event: CalendarEvent) {
    val wm = workManagerOrNull() ?: return
    val workName = ReminderScheduleCalculator.uniqueWorkName(ReminderTargetType.EVENT, event.id)
    val prefs = preferencesRepository.userPreferences.first()
    if (!prefs.notificationsEnabled) {
      runCatching { wm.cancelUniqueWork(workName) }
      return
    }
    val now = nowUtcMillisProvider()
    val triggerAt = ReminderScheduleCalculator.computeEventTriggerAtUtcMillis(event, now)
    if (triggerAt == null) {
      runCatching { wm.cancelUniqueWork(workName) }
      return
    }
    enqueueUniqueReminder(
      workManager = wm,
      workName = workName,
      targetType = ReminderTargetType.EVENT,
      targetId = event.id,
      delayMillis = (triggerAt - now).coerceAtLeast(0L)
    )
  }

  override suspend fun cancelEventReminder(eventId: String) {
    val wm = workManagerOrNull() ?: return
    val workName = ReminderScheduleCalculator.uniqueWorkName(ReminderTargetType.EVENT, eventId)
    runCatching { wm.cancelUniqueWork(workName) }
  }

  override suspend fun cancelAllReminders() {
    val wm = workManagerOrNull() ?: return
    runCatching { wm.cancelAllWorkByTag(WORK_TAG_REMINDER) }
  }

  override suspend fun rescheduleAllActiveReminders() {
    val prefs = preferencesRepository.userPreferences.first()
    if (!prefs.notificationsEnabled) {
      cancelAllReminders()
      return
    }
    val tasks = taskDao.observeActiveTasks().first().map { it.toDomain() }
    val events = calendarEventDao.observeEvents().first().map { it.toDomain() }
    tasks.forEach { scheduleTaskReminder(it) }
    events.forEach { scheduleEventReminder(it) }
  }

  private fun workManagerOrNull(): WorkManager? =
    runCatching { workManagerProvider() }.getOrNull()

  private fun enqueueUniqueReminder(
    workManager: WorkManager,
    workName: String,
    targetType: ReminderTargetType,
    targetId: String,
    delayMillis: Long
  ) {
    val input = Data.Builder()
      .putString(ReminderWorker.KEY_TARGET_TYPE, targetType.key)
      .putString(ReminderWorker.KEY_TARGET_ID, targetId)
      .build()

    val request = OneTimeWorkRequestBuilder<ReminderWorker>()
      .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
      .setInputData(input)
      .addTag(WORK_TAG_REMINDER)
      .addTag("${WORK_TAG_REMINDER}_${targetType.key}_$targetId")
      .build()

    runCatching {
      workManager.enqueueUniqueWork(
        workName,
        ExistingWorkPolicy.REPLACE,
        request
      )
    }
  }

  companion object {
    const val WORK_TAG_REMINDER = "lifeos_reminder"
  }
}
