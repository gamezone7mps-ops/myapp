package com.example.data.local

import com.example.core.calendar.PersianCalendarEngine
import com.example.core.database.CalendarEventDao
import com.example.core.database.CalendarEventEntity
import com.example.core.database.DocumentRecordDao
import com.example.core.database.DocumentRecordEntity
import com.example.core.database.MemoryEntryDao
import com.example.core.database.MemoryEntryEntity
import com.example.core.database.TaskDao
import com.example.core.database.TaskEntity
import com.example.core.documents.DocumentFileStorage
import com.example.core.documents.NoOpDocumentFileStorage
import com.example.core.reminders.NoOpReminderScheduler
import com.example.core.reminders.ReminderScheduler
import com.example.domain.model.CalendarEvent
import com.example.domain.model.CalendarSystem
import com.example.domain.model.DocumentRecord
import com.example.domain.model.ExtractionStatus
import com.example.domain.model.MemoryEntry
import com.example.domain.model.MemoryEntryType
import com.example.domain.model.RecurrenceRule
import com.example.domain.model.SyncStatus
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus
import com.example.domain.model.VaultCounts
import com.example.domain.repository.CalendarRepository
import com.example.domain.repository.DocumentRepository
import com.example.domain.repository.MemoryRepository
import com.example.domain.repository.TaskRepository
import com.example.domain.repository.VaultSummaryRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomTaskRepository(
  private val dao: TaskDao,
  private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler
) : TaskRepository {
  override fun observeTasks(): Flow<List<TaskItem>> =
    dao.observeActiveTasks().map { list -> list.map { it.toDomain() } }

  override fun observePendingCount(): Flow<Int> = dao.observePendingTaskCount()

  override suspend fun getTaskById(id: String): TaskItem? = dao.getById(id)?.toDomain()

  override suspend fun upsertTask(task: TaskItem) {
    dao.upsert(task.toEntity())
    reminderScheduler.scheduleTaskReminder(task)
  }

  override suspend fun completeTask(
    task: TaskItem,
    nowUtcMillis: Long,
    zoneId: String,
    calendarSystem: CalendarSystem
  ): Boolean {
    val completed = task.copy(
      status = TaskStatus.COMPLETED,
      completedAtUtcMillis = nowUtcMillis,
      updatedAtUtcMillis = nowUtcMillis,
      syncStatus = SyncStatus.PENDING_UPSERT
    )
    val recurrence = RecurrenceRule.fromKey(task.recurrenceRule)
    if (recurrence == RecurrenceRule.NONE) {
      dao.upsert(completed.toEntity())
      reminderScheduler.cancelTaskReminder(completed.id)
      return false
    }

    val baseDue = task.dueAtUtcMillis ?: nowUtcMillis
    val nextDue = when (recurrence) {
      RecurrenceRule.DAILY -> PersianCalendarEngine.addDays(baseDue, 1, zoneId)
      RecurrenceRule.WEEKLY -> PersianCalendarEngine.addDays(baseDue, 7, zoneId)
      RecurrenceRule.MONTHLY -> PersianCalendarEngine.addMonths(baseDue, 1, zoneId, calendarSystem)
      RecurrenceRule.NONE -> baseDue
    }
    val nextTask = task.copy(
      id = UUID.randomUUID().toString(),
      status = TaskStatus.TODO,
      dueAtUtcMillis = nextDue,
      completedAtUtcMillis = null,
      createdAtUtcMillis = nowUtcMillis,
      updatedAtUtcMillis = nowUtcMillis,
      syncStatus = SyncStatus.PENDING_UPSERT,
      isDeleted = false
    )
    dao.completeRecurringTask(completed.toEntity(), nextTask.toEntity())
    reminderScheduler.cancelTaskReminder(completed.id)
    reminderScheduler.scheduleTaskReminder(nextTask)
    return true
  }

  override suspend fun updateTaskStatus(
    task: TaskItem,
    newStatus: TaskStatus,
    nowUtcMillis: Long
  ) {
    val updated = task.copy(
      status = newStatus,
      completedAtUtcMillis = if (newStatus == TaskStatus.COMPLETED) nowUtcMillis else null,
      updatedAtUtcMillis = nowUtcMillis,
      syncStatus = SyncStatus.PENDING_UPSERT
    )
    dao.upsert(updated.toEntity())
    reminderScheduler.scheduleTaskReminder(updated)
  }

  override suspend fun softDeleteTask(id: String, nowUtcMillis: Long) {
    dao.softDelete(id, nowUtcMillis, SyncStatus.PENDING_DELETE.key)
    reminderScheduler.cancelTaskReminder(id)
  }
}

class RoomCalendarRepository(
  private val dao: CalendarEventDao,
  private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler
) : CalendarRepository {
  override fun observeEvents(): Flow<List<CalendarEvent>> =
    dao.observeEvents().map { list -> list.map { it.toDomain() } }

  override fun observeEventCount(): Flow<Int> = dao.observeEventCount()

  override suspend fun getEventById(id: String): CalendarEvent? = dao.getById(id)?.toDomain()

  override suspend fun upsertEvent(event: CalendarEvent) {
    dao.upsert(event.toEntity())
    reminderScheduler.scheduleEventReminder(event)
  }

  override suspend fun softDeleteEvent(id: String, nowUtcMillis: Long) {
    dao.softDelete(id, nowUtcMillis, SyncStatus.PENDING_DELETE.key)
    reminderScheduler.cancelEventReminder(id)
  }
}

class RoomMemoryRepository(
  private val dao: MemoryEntryDao
) : MemoryRepository {
  override fun observeMemories(): Flow<List<MemoryEntry>> =
    dao.observeMemories().map { list -> list.map { it.toDomain() } }

  override fun observeMemoryCount(): Flow<Int> = dao.observeMemoryCount()

  override suspend fun getMemoryById(id: String): MemoryEntry? = dao.getById(id)?.toDomain()

  override suspend fun upsertMemory(entry: MemoryEntry) {
    dao.upsert(entry.toEntity())
  }

  override suspend fun softDeleteMemory(id: String, nowUtcMillis: Long) {
    dao.softDelete(id, nowUtcMillis, SyncStatus.PENDING_DELETE.key)
  }
}

class RoomDocumentRepository(
  private val dao: DocumentRecordDao,
  private val fileStorage: DocumentFileStorage = NoOpDocumentFileStorage
) : DocumentRepository {
  override fun observeDocuments(): Flow<List<DocumentRecord>> =
    dao.observeDocuments().map { list -> list.map { it.toDomain() } }

  override fun observeDocumentCount(): Flow<Int> = dao.observeDocumentCount()

  override suspend fun getDocumentById(id: String): DocumentRecord? = dao.getById(id)?.toDomain()

  override suspend fun upsertDocument(document: DocumentRecord) {
    dao.upsert(document.toEntity())
  }

  override suspend fun softDeleteDocument(id: String, nowUtcMillis: Long) {
    val existing = dao.getById(id)
    dao.softDelete(id, nowUtcMillis, SyncStatus.PENDING_DELETE.key)
    if (existing != null) {
      fileStorage.deleteLocalFile(existing.localRelativePath)
    }
  }
}

class RoomVaultSummaryRepository(
  private val taskDao: TaskDao,
  private val calendarEventDao: CalendarEventDao,
  private val memoryEntryDao: MemoryEntryDao,
  private val documentRecordDao: DocumentRecordDao
) : VaultSummaryRepository {
  override fun observeVaultCounts(): Flow<VaultCounts> = combine(
    taskDao.observePendingTaskCount(),
    calendarEventDao.observeEventCount(),
    memoryEntryDao.observeMemoryCount(),
    documentRecordDao.observeDocumentCount()
  ) { tasks, events, memories, docs ->
    VaultCounts(
      activeTasksCount = tasks,
      eventsCount = events,
      memoriesCount = memories,
      documentsCount = docs
    )
  }
}

internal fun TaskEntity.toDomain(): TaskItem = TaskItem(
  id = id,
  title = title,
  description = description,
  dueAtUtcMillis = dueAtUtcMillis,
  priority = TaskPriority.fromLevel(priority),
  status = TaskStatus.fromKey(status),
  category = category,
  recurrenceRule = recurrenceRule,
  reminderMinutesBefore = reminderMinutesBefore,
  completedAtUtcMillis = completedAtUtcMillis,
  createdAtUtcMillis = createdAtUtcMillis,
  updatedAtUtcMillis = updatedAtUtcMillis,
  syncStatus = SyncStatus.fromKey(syncStatus),
  isDeleted = isDeleted
)

internal fun TaskItem.toEntity(): TaskEntity = TaskEntity(
  id = id,
  title = title,
  description = description,
  dueAtUtcMillis = dueAtUtcMillis,
  priority = priority.level,
  status = status.key,
  category = category,
  recurrenceRule = recurrenceRule,
  reminderMinutesBefore = reminderMinutesBefore,
  completedAtUtcMillis = completedAtUtcMillis,
  createdAtUtcMillis = createdAtUtcMillis,
  updatedAtUtcMillis = updatedAtUtcMillis,
  syncStatus = syncStatus.key,
  isDeleted = isDeleted
)

internal fun CalendarEventEntity.toDomain(): CalendarEvent = CalendarEvent(
  id = id,
  title = title,
  description = description,
  startAtUtcMillis = startAtUtcMillis,
  endAtUtcMillis = endAtUtcMillis,
  zoneId = zoneId,
  location = location,
  isAllDay = isAllDay,
  reminderMinutesBefore = reminderMinutesBefore,
  recurrenceRule = recurrenceRule,
  createdAtUtcMillis = createdAtUtcMillis,
  updatedAtUtcMillis = updatedAtUtcMillis,
  syncStatus = SyncStatus.fromKey(syncStatus),
  isDeleted = isDeleted
)

internal fun CalendarEvent.toEntity(): CalendarEventEntity = CalendarEventEntity(
  id = id,
  title = title,
  description = description,
  startAtUtcMillis = startAtUtcMillis,
  endAtUtcMillis = endAtUtcMillis,
  zoneId = zoneId,
  location = location,
  isAllDay = isAllDay,
  reminderMinutesBefore = reminderMinutesBefore,
  recurrenceRule = recurrenceRule,
  createdAtUtcMillis = createdAtUtcMillis,
  updatedAtUtcMillis = updatedAtUtcMillis,
  syncStatus = syncStatus.key,
  isDeleted = isDeleted
)

internal fun MemoryEntryEntity.toDomain(): MemoryEntry = MemoryEntry(
  id = id,
  title = title,
  content = content,
  entryType = MemoryEntryType.fromKey(entryType),
  tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
  sourceUrl = sourceUrl,
  isPinned = isPinned,
  createdAtUtcMillis = createdAtUtcMillis,
  updatedAtUtcMillis = updatedAtUtcMillis,
  syncStatus = SyncStatus.fromKey(syncStatus),
  isDeleted = isDeleted
)

internal fun MemoryEntry.toEntity(): MemoryEntryEntity = MemoryEntryEntity(
  id = id,
  title = title,
  content = content,
  entryType = entryType.key,
  tagsCsv = tags.joinToString(","),
  sourceUrl = sourceUrl,
  isPinned = isPinned,
  createdAtUtcMillis = createdAtUtcMillis,
  updatedAtUtcMillis = updatedAtUtcMillis,
  syncStatus = syncStatus.key,
  isDeleted = isDeleted
)

internal fun DocumentRecordEntity.toDomain(): DocumentRecord = DocumentRecord(
  id = id,
  title = title,
  mimeType = mimeType,
  localRelativePath = localRelativePath,
  fileSizeBytes = fileSizeBytes,
  documentDateUtcMillis = documentDateUtcMillis,
  tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
  userVerifiedNotes = userVerifiedNotes,
  extractedSummary = extractedSummary,
  extractionStatus = ExtractionStatus.fromKey(extractionStatus),
  createdAtUtcMillis = createdAtUtcMillis,
  updatedAtUtcMillis = updatedAtUtcMillis,
  syncStatus = SyncStatus.fromKey(syncStatus),
  isDeleted = isDeleted
)

internal fun DocumentRecord.toEntity(): DocumentRecordEntity = DocumentRecordEntity(
  id = id,
  title = title,
  mimeType = mimeType,
  localRelativePath = localRelativePath,
  fileSizeBytes = fileSizeBytes,
  documentDateUtcMillis = documentDateUtcMillis,
  tagsCsv = tags.joinToString(","),
  userVerifiedNotes = userVerifiedNotes,
  extractedSummary = extractedSummary,
  extractionStatus = extractionStatus.key,
  createdAtUtcMillis = createdAtUtcMillis,
  updatedAtUtcMillis = updatedAtUtcMillis,
  syncStatus = syncStatus.key,
  isDeleted = isDeleted
)
