package com.example.domain.usecase

import com.example.core.common.InputValidators
import com.example.core.common.ValidationError
import com.example.domain.model.CalendarEvent
import com.example.domain.model.CalendarSystem
import com.example.domain.model.MemoryEntry
import com.example.domain.model.MemoryEntryType
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorsAndInsightsTest {

  private val computeInsights = ComputeInsightsUseCase()

  @Test
  fun `input validators reject invalid titles, reversed event times, and bad urls`() {
    assertEquals(ValidationError.TASK_TITLE_REQUIRED, InputValidators.validateTaskTitle("   "))
    assertNull(InputValidators.validateTaskTitle("Buy milk"))

    assertEquals(
      ValidationError.EVENT_END_BEFORE_START,
      InputValidators.validateEvent("Meeting", 2_000L, 1_000L)
    )
    assertNull(InputValidators.validateEvent("Meeting", 1_000L, 2_000L))

    assertEquals(
      ValidationError.MEMORY_EMPTY,
      InputValidators.validateMemory("  ", " ", null)
    )
    assertEquals(
      ValidationError.MEMORY_INVALID_URL,
      InputValidators.validateMemory("Link", "", "ftp://example.com")
    )
    assertNull(InputValidators.validateMemory("Link", "", "https://example.com/docs"))
  }

  @Test
  fun `compute insights returns empty state when vault has no data`() {
    val summary = computeInsights(
      tasks = emptyList(),
      events = emptyList(),
      memories = emptyList(),
      documents = emptyList(),
      nowUtcMillis = 1_795_086_000_000L,
      zoneId = "Asia/Tehran",
      calendarSystem = CalendarSystem.JALALI
    )
    assertFalse(summary.hasAnyData)
    assertEquals(0, summary.totalTasks)
    assertEquals(0, summary.completionRatePercent)
  }

  @Test
  fun `compute insights calculates exact counts from stored records`() {
    val now = com.example.core.calendar.PersianCalendarEngine.zonedPartsToUtcMillis(
      year = 1405,
      month = 8,
      day = 28,
      hour = 12,
      minute = 0,
      zoneId = "Asia/Tehran",
      calendarSystem = CalendarSystem.JALALI
    )
    val tasks = listOf(
      TaskItem(
        id = "t1",
        title = "Done",
        status = TaskStatus.COMPLETED,
        priority = TaskPriority.HIGH,
        createdAtUtcMillis = now,
        updatedAtUtcMillis = now
      ),
      TaskItem(
        id = "t2",
        title = "Overdue",
        status = TaskStatus.TODO,
        priority = TaskPriority.URGENT,
        dueAtUtcMillis = now - 86_400_000L,
        createdAtUtcMillis = now,
        updatedAtUtcMillis = now
      ),
      TaskItem(
        id = "t3",
        title = "Upcoming",
        status = TaskStatus.IN_PROGRESS,
        priority = TaskPriority.URGENT,
        dueAtUtcMillis = now + 3_600_000L,
        createdAtUtcMillis = now,
        updatedAtUtcMillis = now
      )
    )
    val events = listOf(
      CalendarEvent(
        id = "e1",
        title = "Exam",
        startAtUtcMillis = now + 1_800_000L,
        endAtUtcMillis = now + 5_400_000L,
        zoneId = "Asia/Tehran",
        createdAtUtcMillis = now,
        updatedAtUtcMillis = now
      )
    )
    val memories = listOf(
      MemoryEntry(
        id = "m1",
        title = "Note",
        content = "Details",
        entryType = MemoryEntryType.IDEA,
        createdAtUtcMillis = now,
        updatedAtUtcMillis = now
      )
    )

    val summary = computeInsights(
      tasks = tasks,
      events = events,
      memories = memories,
      documents = emptyList(),
      nowUtcMillis = now,
      zoneId = "Asia/Tehran",
      calendarSystem = CalendarSystem.JALALI
    )

    assertTrue(summary.hasAnyData)
    assertEquals(3, summary.totalTasks)
    assertEquals(1, summary.completedTasks)
    assertEquals(2, summary.activeTasks)
    assertEquals(1, summary.overdueTasks)
    assertEquals(33, summary.completionRatePercent)
    assertEquals(2, summary.activeByPriority[TaskPriority.URGENT])
    assertEquals(1, summary.upcomingSevenDays.first().eventsCount)
    assertEquals(1, summary.upcomingSevenDays.first().dueTasksCount)
    assertEquals(1, summary.memoriesByType[MemoryEntryType.IDEA])
  }
}
