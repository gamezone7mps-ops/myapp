package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.database.AppDatabase
import com.example.domain.model.CalendarSystem
import com.example.domain.model.MemoryEntry
import com.example.domain.model.MemoryEntryType
import com.example.domain.model.RecurrenceRule
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
class Phase2RepositoryTest {

  private lateinit var db: AppDatabase
  private lateinit var taskRepo: RoomTaskRepository
  private lateinit var memoryRepo: RoomMemoryRepository

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    taskRepo = RoomTaskRepository(db.taskDao())
    memoryRepo = RoomMemoryRepository(db.memoryEntryDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `completing a recurring weekly task marks current completed and spawns next week task`() = runTest {
    val due = PersianCalendarEngine.zonedPartsToUtcMillis(
      year = 1405,
      month = 8,
      day = 28,
      hour = 9,
      minute = 0,
      zoneId = "Asia/Tehran",
      calendarSystem = CalendarSystem.JALALI
    )
    val task = TaskItem(
      id = "rec-1",
      title = "Weekly review",
      dueAtUtcMillis = due,
      priority = TaskPriority.HIGH,
      status = TaskStatus.TODO,
      recurrenceRule = RecurrenceRule.WEEKLY.key,
      createdAtUtcMillis = due,
      updatedAtUtcMillis = due
    )
    taskRepo.upsertTask(task)

    val spawned = taskRepo.completeTask(
      task = task,
      nowUtcMillis = due + 1000L,
      zoneId = "Asia/Tehran",
      calendarSystem = CalendarSystem.JALALI
    )
    assertTrue(spawned)

    val all = taskRepo.observeTasks().first()
    assertEquals(2, all.size)
    val completed = all.first { it.id == "rec-1" }
    val next = all.first { it.id != "rec-1" }

    assertEquals(TaskStatus.COMPLETED, completed.status)
    assertEquals(TaskStatus.TODO, next.status)
    val expectedNextDue = PersianCalendarEngine.addDays(due, 7, "Asia/Tehran")
    assertEquals(expectedNextDue, next.dueAtUtcMillis)
  }

  @Test
  fun `memory entry tags and pin status persist cleanly`() = runTest {
    val now = 1_795_086_000_000L
    val entry = MemoryEntry(
      id = "mem-1",
      title = "Passport renewal",
      content = "Bring two photos",
      entryType = MemoryEntryType.FACT,
      tags = listOf("travel", "docs"),
      isPinned = true,
      createdAtUtcMillis = now,
      updatedAtUtcMillis = now
    )
    memoryRepo.upsertMemory(entry)

    val loaded = memoryRepo.observeMemories().first()
    assertEquals(1, loaded.size)
    assertEquals(listOf("travel", "docs"), loaded.first().tags)
    assertTrue(loaded.first().isPinned)
  }
}
