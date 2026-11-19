package com.example.domain.usecase

import com.example.core.calendar.PersianCalendarEngine
import com.example.domain.model.CalendarEvent
import com.example.domain.model.CalendarSystem
import com.example.domain.model.DocumentRecord
import com.example.domain.model.MemoryEntry
import com.example.domain.model.MemoryEntryType
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus

data class DayWorkloadPoint(
  val dayStartUtcMillis: Long,
  val eventsCount: Int,
  val dueTasksCount: Int
)

data class InsightsSummary(
  val hasAnyData: Boolean,
  val totalTasks: Int,
  val completedTasks: Int,
  val activeTasks: Int,
  val archivedTasks: Int,
  val overdueTasks: Int,
  val completionRatePercent: Int,
  val activeByPriority: Map<TaskPriority, Int>,
  val upcomingSevenDays: List<DayWorkloadPoint>,
  val totalEvents: Int,
  val totalMemories: Int,
  val memoriesByType: Map<MemoryEntryType, Int>,
  val totalDocuments: Int
)

class ComputeInsightsUseCase {
  operator fun invoke(
    tasks: List<TaskItem>,
    events: List<CalendarEvent>,
    memories: List<MemoryEntry>,
    documents: List<DocumentRecord>,
    nowUtcMillis: Long,
    zoneId: String,
    calendarSystem: CalendarSystem
  ): InsightsSummary {
    val activeTasksList = tasks.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.IN_PROGRESS }
    val completedCount = tasks.count { it.status == TaskStatus.COMPLETED }
    val archivedCount = tasks.count { it.status == TaskStatus.ARCHIVED }
    val overdueCount = activeTasksList.count { task ->
      val due = task.dueAtUtcMillis
      due != null && due < nowUtcMillis
    }

    val nonArchivedTotal = activeTasksList.size + completedCount
    val completionPercent = if (nonArchivedTotal > 0) {
      ((completedCount * 100.0) / nonArchivedTotal).toInt().coerceIn(0, 100)
    } else {
      0
    }

    val priorityCounts = TaskPriority.entries.associateWith { priority ->
      activeTasksList.count { it.priority == priority }
    }

    val todayParts = PersianCalendarEngine.utcMillisToZonedParts(nowUtcMillis, zoneId, calendarSystem)
    val todayDate = com.example.core.calendar.CalendarDate(todayParts.year, todayParts.month, todayParts.day)
    val todayStart = PersianCalendarEngine.startOfDayUtcMillis(todayDate, zoneId, calendarSystem)

    val nextSevenDays = (0..6).map { dayOffset ->
      val dayStart = PersianCalendarEngine.addDays(todayStart, dayOffset, zoneId)
      val dayEnd = PersianCalendarEngine.addDays(dayStart, 1, zoneId) - 1L
      val dayEvents = events.count { ev ->
        ev.startAtUtcMillis <= dayEnd && ev.endAtUtcMillis >= dayStart
      }
      val dayTasks = activeTasksList.count { t ->
        val due = t.dueAtUtcMillis
        due != null && due in dayStart..dayEnd
      }
      DayWorkloadPoint(
        dayStartUtcMillis = dayStart,
        eventsCount = dayEvents,
        dueTasksCount = dayTasks
      )
    }

    val memoryBreakdown = MemoryEntryType.entries.associateWith { type ->
      memories.count { it.entryType == type }
    }

    val hasAnyData = tasks.isNotEmpty() || events.isNotEmpty() || memories.isNotEmpty() || documents.isNotEmpty()

    return InsightsSummary(
      hasAnyData = hasAnyData,
      totalTasks = tasks.size,
      completedTasks = completedCount,
      activeTasks = activeTasksList.size,
      archivedTasks = archivedCount,
      overdueTasks = overdueCount,
      completionRatePercent = completionPercent,
      activeByPriority = priorityCounts,
      upcomingSevenDays = nextSevenDays,
      totalEvents = events.size,
      totalMemories = memories.size,
      memoriesByType = memoryBreakdown,
      totalDocuments = documents.size
    )
  }
}
