package com.example.core.reminders

import com.example.domain.model.CalendarEvent
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskStatus

enum class ReminderTargetType(val key: String) {
  TASK("task"),
  EVENT("event");

  companion object {
    fun fromKey(key: String?): ReminderTargetType? =
      entries.firstOrNull { it.key == key }
  }
}

object ReminderScheduleCalculator {

  fun computeTaskTriggerAtUtcMillis(task: TaskItem, nowUtcMillis: Long): Long? {
    if (task.isDeleted) return null
    if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.ARCHIVED) return null
    val dueAt = task.dueAtUtcMillis ?: return null
    val offsetMinutes = task.reminderMinutesBefore ?: return null
    val triggerAt = dueAt - offsetMinutes.coerceAtLeast(0).toLong() * 60_000L
    return if (triggerAt > nowUtcMillis) triggerAt else null
  }

  fun computeEventTriggerAtUtcMillis(event: CalendarEvent, nowUtcMillis: Long): Long? {
    if (event.isDeleted) return null
    val offsetMinutes = event.reminderMinutesBefore ?: return null
    val triggerAt = event.startAtUtcMillis - offsetMinutes.coerceAtLeast(0).toLong() * 60_000L
    return if (triggerAt > nowUtcMillis) triggerAt else null
  }

  fun uniqueWorkName(targetType: ReminderTargetType, id: String): String =
    "lifeos_reminder_${targetType.key}_$id"

  fun notificationId(targetType: ReminderTargetType, id: String): Int =
    ("${targetType.key}:$id").hashCode()
}

interface ReminderScheduler {
  suspend fun scheduleTaskReminder(task: TaskItem)
  suspend fun cancelTaskReminder(taskId: String)
  suspend fun scheduleEventReminder(event: CalendarEvent)
  suspend fun cancelEventReminder(eventId: String)
  suspend fun cancelAllReminders()
  suspend fun rescheduleAllActiveReminders()
}

object NoOpReminderScheduler : ReminderScheduler {
  override suspend fun scheduleTaskReminder(task: TaskItem) = Unit
  override suspend fun cancelTaskReminder(taskId: String) = Unit
  override suspend fun scheduleEventReminder(event: CalendarEvent) = Unit
  override suspend fun cancelEventReminder(eventId: String) = Unit
  override suspend fun cancelAllReminders() = Unit
  override suspend fun rescheduleAllActiveReminders() = Unit
}
