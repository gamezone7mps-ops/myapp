package com.example.core.ai

import com.example.domain.model.CalendarEvent
import com.example.domain.model.DocumentRecord
import com.example.domain.model.MemoryEntry
import com.example.domain.model.MemoryEntryType
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus

enum class AiToolName(val wireName: String) {
  SEARCH_TASKS("searchTasks"),
  SEARCH_CALENDAR("searchCalendar"),
  SEARCH_MEMORY("searchMemory"),
  SEARCH_DOCUMENTS("searchDocuments"),
  CREATE_TASK("createTask"),
  UPDATE_TASK("updateTask"),
  CREATE_CALENDAR_EVENT("createCalendarEvent"),
  UPDATE_CALENDAR_EVENT("updateCalendarEvent"),
  CREATE_MEMORY("createMemory")
}

data class DocumentSearchExcerpt(
  val record: DocumentRecord,
  val textExcerpt: String? = null
)

data class WorkspaceContextSnapshot(
  val allTasks: List<TaskItem> = emptyList(),
  val allEvents: List<CalendarEvent> = emptyList(),
  val matchedTasks: List<TaskItem> = emptyList(),
  val matchedEvents: List<CalendarEvent> = emptyList(),
  val matchedMemories: List<MemoryEntry> = emptyList(),
  val matchedDocuments: List<DocumentSearchExcerpt> = emptyList()
) {
  fun totalMatchedCount(): Int =
    matchedTasks.size + matchedEvents.size + matchedMemories.size + matchedDocuments.size
}

sealed interface AiProposedAction {
  data class CreateTaskProposal(
    val title: String,
    val description: String = "",
    val dueAtUtcMillis: Long? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val category: String? = null,
    val recurrenceRule: String? = null,
    val reminderMinutesBefore: Int? = null
  ) : AiProposedAction

  data class UpdateTaskProposal(
    val targetTaskId: String,
    val targetCurrentTitle: String,
    val newTitle: String,
    val newDescription: String,
    val newDueAtUtcMillis: Long?,
    val newPriority: TaskPriority,
    val newStatus: TaskStatus
  ) : AiProposedAction

  data class CreateEventProposal(
    val title: String,
    val description: String = "",
    val location: String? = null,
    val startAtUtcMillis: Long,
    val endAtUtcMillis: Long,
    val zoneId: String,
    val isAllDay: Boolean = false,
    val reminderMinutesBefore: Int? = null,
    val recurrenceRule: String? = null
  ) : AiProposedAction

  data class UpdateEventProposal(
    val targetEventId: String,
    val targetCurrentTitle: String,
    val newTitle: String,
    val newDescription: String,
    val newLocation: String?,
    val newStartAtUtcMillis: Long,
    val newEndAtUtcMillis: Long,
    val zoneId: String
  ) : AiProposedAction

  data class CreateMemoryProposal(
    val title: String,
    val content: String,
    val entryType: MemoryEntryType = MemoryEntryType.NOTE,
    val tags: List<String> = emptyList(),
    val sourceUrl: String? = null
  ) : AiProposedAction
}

enum class AiUnavailableReason {
  CONSENT_REQUIRED,
  OFFLINE,
  TIMEOUT,
  SERVICE_ERROR
}

sealed interface AiTurnOutcome {
  data class Reply(
    val replyText: String,
    val referencedItems: List<String> = emptyList()
  ) : AiTurnOutcome

  data class ClarificationNeeded(
    val questionText: String,
    val suggestions: List<String> = emptyList()
  ) : AiTurnOutcome

  data class Proposal(
    val explanationText: String,
    val action: AiProposedAction,
    val referencedItems: List<String> = emptyList()
  ) : AiTurnOutcome

  data class Unavailable(
    val reason: AiUnavailableReason,
    val detail: String? = null
  ) : AiTurnOutcome
}

data class AssistantMessageTurn(
  val isUser: Boolean,
  val text: String
)

sealed interface ToolExecutionResult {
  data class Success(val actionTitle: String, val entityId: String) : ToolExecutionResult
  data class Failure(val reason: String) : ToolExecutionResult
}
