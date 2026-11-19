package com.example.core.ai

import com.example.core.documents.DocumentFileStorage
import com.example.core.documents.DocumentSupport
import com.example.domain.model.CalendarEvent
import com.example.domain.model.MemoryEntry
import com.example.domain.model.SyncStatus
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskStatus
import com.example.domain.repository.CalendarRepository
import com.example.domain.repository.DocumentRepository
import com.example.domain.repository.MemoryRepository
import com.example.domain.repository.TaskRepository
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * Provides strictly scoped read-only searches across permitted user repositories and executes
 * explicitly confirmed workspace mutations after user preview approval.
 */
class WorkspaceToolExecutor(
  private val taskRepository: TaskRepository,
  private val calendarRepository: CalendarRepository,
  private val memoryRepository: MemoryRepository,
  private val documentRepository: DocumentRepository,
  private val documentFileStorage: DocumentFileStorage
) {

  suspend fun buildContextSnapshot(query: String): WorkspaceContextSnapshot {
    val allTasks = taskRepository.observeTasks().first().filter { !it.isDeleted }
    val allEvents = calendarRepository.observeEvents().first().filter { !it.isDeleted }
    val allMemories = memoryRepository.observeMemories().first().filter { !it.isDeleted }
    val allDocs = documentRepository.observeDocuments().first().filter { !it.isDeleted }

    val tokens = tokenize(query)

    val matchedTasks = searchTasks(allTasks, tokens)
    val matchedEvents = searchEvents(allEvents, tokens)
    val matchedMemories = searchMemories(allMemories, tokens)
    val matchedDocuments = searchDocuments(allDocs, tokens)

    return WorkspaceContextSnapshot(
      allTasks = allTasks.take(30),
      allEvents = allEvents.take(30),
      matchedTasks = matchedTasks,
      matchedEvents = matchedEvents,
      matchedMemories = matchedMemories,
      matchedDocuments = matchedDocuments
    )
  }

  private fun searchTasks(allTasks: List<TaskItem>, tokens: List<String>): List<TaskItem> {
    if (allTasks.isEmpty()) return emptyList()
    val scored = allTasks.map { task ->
      val text = "${task.title} ${task.description} ${task.category.orEmpty()}"
      val score = scoreText(text, tokens) + if (task.status == TaskStatus.TODO || task.status == TaskStatus.IN_PROGRESS) 1 else 0
      task to score
    }
    val relevant = scored.filter { (_, score) -> tokens.isEmpty() || score > 1 }
      .sortedByDescending { it.second }
      .map { it.first }
    return (if (relevant.isNotEmpty()) relevant else allTasks.filter { it.status != TaskStatus.ARCHIVED })
      .take(8)
  }

  private fun searchEvents(allEvents: List<CalendarEvent>, tokens: List<String>): List<CalendarEvent> {
    if (allEvents.isEmpty()) return emptyList()
    val relevant = allEvents.map { ev ->
      val text = "${ev.title} ${ev.description} ${ev.location.orEmpty()}"
      ev to scoreText(text, tokens)
    }.filter { it.second > 0 }
      .sortedByDescending { it.second }
      .map { it.first }
    return (if (relevant.isNotEmpty()) relevant else allEvents.sortedBy { it.startAtUtcMillis })
      .take(8)
  }

  private fun searchMemories(allMemories: List<MemoryEntry>, tokens: List<String>): List<MemoryEntry> {
    if (allMemories.isEmpty()) return emptyList()
    val relevant = allMemories.map { mem ->
      val text = "${mem.title} ${mem.content} ${mem.tags.joinToString(" ")} ${mem.sourceUrl.orEmpty()}"
      mem to scoreText(text, tokens)
    }.filter { it.second > 0 }
      .sortedByDescending { it.second }
      .map { it.first }
    return (if (relevant.isNotEmpty()) relevant else allMemories.sortedByDescending { it.isPinned })
      .take(8)
  }

  private suspend fun searchDocuments(
    allDocs: List<com.example.domain.model.DocumentRecord>,
    tokens: List<String>
  ): List<DocumentSearchExcerpt> {
    if (allDocs.isEmpty()) return emptyList()
    val candidates = allDocs.map { doc ->
      val text = "${doc.title} ${doc.userVerifiedNotes} ${doc.extractedSummary.orEmpty()} ${doc.tags.joinToString(" ")}"
      doc to scoreText(text, tokens)
    }.sortedByDescending { it.second }
      .take(6)

    return candidates.map { (doc, _) ->
      val excerpt = if (DocumentSupport.isTextMimeType(doc.mimeType)) {
        documentFileStorage.readTextPreview(doc.localRelativePath, doc.mimeType, maxChars = 1000)
          ?.takeIf { it.isNotBlank() }
      } else {
        null
      }
      DocumentSearchExcerpt(record = doc, textExcerpt = excerpt)
    }
  }

  suspend fun executeConfirmedProposal(
    action: AiProposedAction,
    nowUtcMillis: Long = System.currentTimeMillis()
  ): ToolExecutionResult {
    return try {
      when (action) {
        is AiProposedAction.CreateTaskProposal -> {
          val id = UUID.randomUUID().toString()
          taskRepository.upsertTask(
            TaskItem(
              id = id,
              title = action.title,
              description = action.description,
              dueAtUtcMillis = action.dueAtUtcMillis,
              priority = action.priority,
              status = TaskStatus.TODO,
              category = action.category,
              recurrenceRule = action.recurrenceRule,
              reminderMinutesBefore = action.reminderMinutesBefore,
              completedAtUtcMillis = null,
              createdAtUtcMillis = nowUtcMillis,
              updatedAtUtcMillis = nowUtcMillis,
              syncStatus = SyncStatus.PENDING_UPSERT,
              isDeleted = false
            )
          )
          ToolExecutionResult.Success(actionTitle = action.title, entityId = id)
        }

        is AiProposedAction.UpdateTaskProposal -> {
          val existing = taskRepository.getTaskById(action.targetTaskId)
            ?: return ToolExecutionResult.Failure("Task no longer exists.")
          val completedAt = if (action.newStatus == TaskStatus.COMPLETED && existing.completedAtUtcMillis == null) {
            nowUtcMillis
          } else if (action.newStatus != TaskStatus.COMPLETED) {
            null
          } else {
            existing.completedAtUtcMillis
          }
          taskRepository.upsertTask(
            existing.copy(
              title = action.newTitle,
              description = action.newDescription,
              dueAtUtcMillis = action.newDueAtUtcMillis,
              priority = action.newPriority,
              status = action.newStatus,
              completedAtUtcMillis = completedAt,
              updatedAtUtcMillis = nowUtcMillis,
              syncStatus = SyncStatus.PENDING_UPSERT
            )
          )
          ToolExecutionResult.Success(actionTitle = action.newTitle, entityId = existing.id)
        }

        is AiProposedAction.CreateEventProposal -> {
          val id = UUID.randomUUID().toString()
          calendarRepository.upsertEvent(
            CalendarEvent(
              id = id,
              title = action.title,
              description = action.description,
              startAtUtcMillis = action.startAtUtcMillis,
              endAtUtcMillis = action.endAtUtcMillis,
              zoneId = action.zoneId,
              location = action.location,
              isAllDay = action.isAllDay,
              reminderMinutesBefore = action.reminderMinutesBefore,
              recurrenceRule = action.recurrenceRule,
              createdAtUtcMillis = nowUtcMillis,
              updatedAtUtcMillis = nowUtcMillis,
              syncStatus = SyncStatus.PENDING_UPSERT,
              isDeleted = false
            )
          )
          ToolExecutionResult.Success(actionTitle = action.title, entityId = id)
        }

        is AiProposedAction.UpdateEventProposal -> {
          val existing = calendarRepository.getEventById(action.targetEventId)
            ?: return ToolExecutionResult.Failure("Event no longer exists.")
          calendarRepository.upsertEvent(
            existing.copy(
              title = action.newTitle,
              description = action.newDescription,
              location = action.newLocation,
              startAtUtcMillis = action.newStartAtUtcMillis,
              endAtUtcMillis = action.newEndAtUtcMillis,
              updatedAtUtcMillis = nowUtcMillis,
              syncStatus = SyncStatus.PENDING_UPSERT
            )
          )
          ToolExecutionResult.Success(actionTitle = action.newTitle, entityId = existing.id)
        }

        is AiProposedAction.CreateMemoryProposal -> {
          val id = UUID.randomUUID().toString()
          memoryRepository.upsertMemory(
            MemoryEntry(
              id = id,
              title = action.title,
              content = action.content,
              entryType = action.entryType,
              tags = action.tags,
              sourceUrl = action.sourceUrl,
              isPinned = false,
              createdAtUtcMillis = nowUtcMillis,
              updatedAtUtcMillis = nowUtcMillis,
              syncStatus = SyncStatus.PENDING_UPSERT,
              isDeleted = false
            )
          )
          ToolExecutionResult.Success(actionTitle = action.title, entityId = id)
        }
      }
    } catch (e: Exception) {
      ToolExecutionResult.Failure(e.localizedMessage ?: "Failed to save changes.")
    }
  }

  private fun tokenize(query: String): List<String> =
    query.lowercase()
      .split(Regex("[\\s,،.;:!?؟/()]+"))
      .map { it.trim() }
      .filter { it.length >= 2 }
      .distinct()

  private fun scoreText(haystack: String, tokens: List<String>): Int {
    if (tokens.isEmpty()) return 0
    val lower = haystack.lowercase()
    return tokens.count { token -> lower.contains(token) }
  }
}
