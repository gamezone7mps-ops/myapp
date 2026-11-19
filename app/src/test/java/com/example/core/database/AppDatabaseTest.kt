package com.example.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.RoomCalendarRepository
import com.example.data.local.RoomTaskRepository
import com.example.domain.model.CalendarEvent
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseTest {

  private lateinit var db: AppDatabase
  private lateinit var taskRepo: RoomTaskRepository
  private lateinit var calendarRepo: RoomCalendarRepository

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    taskRepo = RoomTaskRepository(db.taskDao())
    calendarRepo = RoomCalendarRepository(db.calendarEventDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `task upsert and soft delete behave consistently`() = runTest {
    val task = TaskItem(
      id = "task-1",
      title = "مرور برنامه هفتگی",
      priority = TaskPriority.HIGH,
      status = TaskStatus.TODO,
      createdAtUtcMillis = 1_700_000_000_000L,
      updatedAtUtcMillis = 1_700_000_000_000L
    )
    taskRepo.upsertTask(task)
    val loaded = taskRepo.observeTasks().first()
    assertEquals(1, loaded.size)
    assertEquals("مرور برنامه هفتگی", loaded.first().title)

    taskRepo.softDeleteTask("task-1", 1_700_000_005_000L)
    val afterDelete = taskRepo.observeTasks().first()
    assertTrue(afterDelete.isEmpty())
  }

  @Test
  fun `calendar event preserves exact utc millis and zoneId`() = runTest {
    val event = CalendarEvent(
      id = "event-1",
      title = "Exam",
      startAtUtcMillis = 1_795_086_000_000L,
      endAtUtcMillis = 1_795_089_600_000L,
      zoneId = "Asia/Tehran",
      createdAtUtcMillis = 1_795_000_000_000L,
      updatedAtUtcMillis = 1_795_000_000_000L
    )
    calendarRepo.upsertEvent(event)
    val stored = calendarRepo.getEventById("event-1")
    assertEquals(1_795_086_000_000L, stored?.startAtUtcMillis)
    assertEquals("Asia/Tehran", stored?.zoneId)
  }
}
