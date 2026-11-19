package com.example.core.reminders

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.core.database.AppDatabase
import com.example.core.datastore.PreferencesRepository
import com.example.data.local.RoomCalendarRepository
import com.example.data.local.RoomTaskRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppThemeMode
import com.example.domain.model.CalendarEvent
import com.example.domain.model.CalendarSystem
import com.example.domain.model.RecurrenceRule
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private class FakePreferencesRepository(
  initial: UserPreferences = UserPreferences()
) : PreferencesRepository {
  private val state = MutableStateFlow(initial)
  override val userPreferences: Flow<UserPreferences> = state

  override suspend fun setLanguage(language: AppLanguage) {
    state.value = state.value.copy(language = language)
  }

  override suspend fun setThemeMode(themeMode: AppThemeMode) {
    state.value = state.value.copy(themeMode = themeMode)
  }

  override suspend fun setCalendarSystem(calendarSystem: CalendarSystem) {
    state.value = state.value.copy(calendarSystem = calendarSystem)
  }

  override suspend fun setNotificationsEnabled(enabled: Boolean) {
    state.value = state.value.copy(notificationsEnabled = enabled)
  }

  override suspend fun setAiDataConsentGranted(granted: Boolean) {
    state.value = state.value.copy(aiDataConsentGranted = granted)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderSchedulerTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var workManager: WorkManager
  private lateinit var prefsRepo: FakePreferencesRepository
  private lateinit var scheduler: WorkManagerReminderScheduler
  private lateinit var taskRepo: RoomTaskRepository
  private lateinit var calendarRepo: RoomCalendarRepository
  private var fixedNowUtc: Long = 1_795_000_000_000L

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    val config = Configuration.Builder()
      .setMinimumLoggingLevel(Log.DEBUG)
      .setExecutor(SynchronousExecutor())
      .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    workManager = WorkManager.getInstance(context)

    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    prefsRepo = FakePreferencesRepository(UserPreferences(notificationsEnabled = true))
    scheduler = WorkManagerReminderScheduler(
      context = context,
      preferencesRepository = prefsRepo,
      taskDao = db.taskDao(),
      calendarEventDao = db.calendarEventDao(),
      workManagerProvider = { workManager },
      nowUtcMillisProvider = { fixedNowUtc }
    )
    taskRepo = RoomTaskRepository(db.taskDao(), scheduler)
    calendarRepo = RoomCalendarRepository(db.calendarEventDao(), scheduler)
  }

  @After
  fun tearDown() {
    workManager.cancelAllWork()
    db.close()
  }

  @Test
  fun `schedule calculator validates active status, offsets, and future trigger times`() {
    val due = fixedNowUtc + 3_600_000L
    val activeTask = TaskItem(
      id = "t1",
      title = "Doctor",
      dueAtUtcMillis = due,
      reminderMinutesBefore = 30,
      status = TaskStatus.TODO,
      createdAtUtcMillis = fixedNowUtc,
      updatedAtUtcMillis = fixedNowUtc
    )
    assertEquals(
      due - 30L * 60_000L,
      ReminderScheduleCalculator.computeTaskTriggerAtUtcMillis(activeTask, fixedNowUtc)
    )
    assertNull(
      ReminderScheduleCalculator.computeTaskTriggerAtUtcMillis(
        activeTask.copy(status = TaskStatus.COMPLETED),
        fixedNowUtc
      )
    )
    assertNull(
      ReminderScheduleCalculator.computeTaskTriggerAtUtcMillis(
        activeTask.copy(isDeleted = true),
        fixedNowUtc
      )
    )
    assertNull(
      ReminderScheduleCalculator.computeTaskTriggerAtUtcMillis(
        activeTask.copy(reminderMinutesBefore = 120),
        fixedNowUtc
      )
    )
  }

  @Test
  fun `task upsert schedules reminder and completion or deletion cancels it`() = runTest {
    val due = fixedNowUtc + 7_200_000L
    val task = TaskItem(
      id = "task-rem-1",
      title = "Pay rent",
      dueAtUtcMillis = due,
      priority = TaskPriority.HIGH,
      status = TaskStatus.TODO,
      reminderMinutesBefore = 30,
      createdAtUtcMillis = fixedNowUtc,
      updatedAtUtcMillis = fixedNowUtc
    )
    taskRepo.upsertTask(task)

    val workName = ReminderScheduleCalculator.uniqueWorkName(ReminderTargetType.TASK, task.id)
    val enqueued = workManager.getWorkInfosForUniqueWork(workName).get()
    assertEquals(1, enqueued.size)
    assertEquals(WorkInfo.State.ENQUEUED, enqueued.first().state)

    taskRepo.updateTaskStatus(task, TaskStatus.COMPLETED, fixedNowUtc + 1000L)
    val afterComplete = workManager.getWorkInfosForUniqueWork(workName).get()
    assertTrue(afterComplete.all { it.state == WorkInfo.State.CANCELLED })

    // Re-open task and verify deletion also cancels
    taskRepo.upsertTask(task)
    assertEquals(
      WorkInfo.State.ENQUEUED,
      workManager.getWorkInfosForUniqueWork(workName).get().first().state
    )
    taskRepo.softDeleteTask(task.id, fixedNowUtc + 2000L)
    val afterDelete = workManager.getWorkInfosForUniqueWork(workName).get()
    assertTrue(afterDelete.all { it.state == WorkInfo.State.CANCELLED })
  }

  @Test
  fun `recurring task completion cancels completed reminder and schedules next occurrence`() = runTest {
    val due = fixedNowUtc + 3_600_000L
    val task = TaskItem(
      id = "rec-rem-1",
      title = "Daily standup",
      dueAtUtcMillis = due,
      recurrenceRule = RecurrenceRule.DAILY.key,
      reminderMinutesBefore = 10,
      createdAtUtcMillis = fixedNowUtc,
      updatedAtUtcMillis = fixedNowUtc
    )
    taskRepo.upsertTask(task)

    taskRepo.completeTask(
      task = task,
      nowUtcMillis = fixedNowUtc + 1000L,
      zoneId = "Asia/Tehran",
      calendarSystem = CalendarSystem.JALALI
    )

    val oldName = ReminderScheduleCalculator.uniqueWorkName(ReminderTargetType.TASK, task.id)
    assertTrue(workManager.getWorkInfosForUniqueWork(oldName).get().all { it.state == WorkInfo.State.CANCELLED })

    val allTagged = workManager.getWorkInfosByTag(WorkManagerReminderScheduler.WORK_TAG_REMINDER).get()
    val activeNew = allTagged.filter { it.state == WorkInfo.State.ENQUEUED }
    assertEquals(1, activeNew.size)
  }

  @Test
  fun `disabling notifications cancels active reminders and re-enabling reschedules them`() = runTest {
    val start = fixedNowUtc + 10_800_000L
    val event = CalendarEvent(
      id = "ev-rem-1",
      title = "Final Exam",
      startAtUtcMillis = start,
      endAtUtcMillis = start + 3_600_000L,
      zoneId = "Asia/Tehran",
      reminderMinutesBefore = 60,
      createdAtUtcMillis = fixedNowUtc,
      updatedAtUtcMillis = fixedNowUtc
    )
    calendarRepo.upsertEvent(event)

    val eventWorkName = ReminderScheduleCalculator.uniqueWorkName(ReminderTargetType.EVENT, event.id)
    assertEquals(
      WorkInfo.State.ENQUEUED,
      workManager.getWorkInfosForUniqueWork(eventWorkName).get().first().state
    )

    prefsRepo.setNotificationsEnabled(false)
    scheduler.cancelAllReminders()
    assertTrue(
      workManager.getWorkInfosForUniqueWork(eventWorkName).get().all { it.state == WorkInfo.State.CANCELLED }
    )

    prefsRepo.setNotificationsEnabled(true)
    scheduler.rescheduleAllActiveReminders()
    val restored = workManager.getWorkInfosForUniqueWork(eventWorkName).get()
    assertNotNull(restored.firstOrNull { it.state == WorkInfo.State.ENQUEUED })
  }

  @Test
  fun `notification permission helper reflects API 33 permission grant and denial`() {
    val app = context as android.app.Application
    shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    assertEquals(
      NotificationPermissionStatus.PERMISSION_REQUIRED,
      ReminderPermissionHelper.getNotificationPermissionStatus(context)
    )

    shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    assertEquals(
      PackageManager.PERMISSION_GRANTED,
      context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    )
  }
}
