package com.example.domain.repository

import com.example.domain.model.CalendarEvent
import com.example.domain.model.CalendarSystem
import com.example.domain.model.DocumentRecord
import com.example.domain.model.MemoryEntry
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskStatus
import com.example.domain.model.VaultCounts
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
  fun observeTasks(): Flow<List<TaskItem>>
  fun observePendingCount(): Flow<Int>
  suspend fun getTaskById(id: String): TaskItem?
  suspend fun upsertTask(task: TaskItem)
  suspend fun completeTask(
    task: TaskItem,
    nowUtcMillis: Long,
    zoneId: String,
    calendarSystem: CalendarSystem
  ): Boolean
  suspend fun updateTaskStatus(task: TaskItem, newStatus: TaskStatus, nowUtcMillis: Long)
  suspend fun softDeleteTask(id: String, nowUtcMillis: Long)
}

interface CalendarRepository {
  fun observeEvents(): Flow<List<CalendarEvent>>
  fun observeEventCount(): Flow<Int>
  suspend fun getEventById(id: String): CalendarEvent?
  suspend fun upsertEvent(event: CalendarEvent)
  suspend fun softDeleteEvent(id: String, nowUtcMillis: Long)
}

interface MemoryRepository {
  fun observeMemories(): Flow<List<MemoryEntry>>
  fun observeMemoryCount(): Flow<Int>
  suspend fun getMemoryById(id: String): MemoryEntry?
  suspend fun upsertMemory(entry: MemoryEntry)
  suspend fun softDeleteMemory(id: String, nowUtcMillis: Long)
}

interface DocumentRepository {
  fun observeDocuments(): Flow<List<DocumentRecord>>
  fun observeDocumentCount(): Flow<Int>
  suspend fun getDocumentById(id: String): DocumentRecord?
  suspend fun upsertDocument(document: DocumentRecord)
  suspend fun softDeleteDocument(id: String, nowUtcMillis: Long)
}

interface VaultSummaryRepository {
  fun observeVaultCounts(): Flow<VaultCounts>
}
