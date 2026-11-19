package com.example.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "tasks",
  indices = [
    Index(value = ["isDeleted", "status", "dueAtUtcMillis"]),
    Index(value = ["syncStatus"])
  ]
)
data class TaskEntity(
  @PrimaryKey val id: String,
  val title: String,
  val description: String,
  val dueAtUtcMillis: Long?,
  val priority: Int,
  val status: String,
  val category: String?,
  val recurrenceRule: String?,
  val reminderMinutesBefore: Int?,
  val completedAtUtcMillis: Long?,
  val createdAtUtcMillis: Long,
  val updatedAtUtcMillis: Long,
  val syncStatus: String,
  val isDeleted: Boolean
)

@Entity(
  tableName = "calendar_events",
  indices = [
    Index(value = ["isDeleted", "startAtUtcMillis", "endAtUtcMillis"]),
    Index(value = ["syncStatus"])
  ]
)
data class CalendarEventEntity(
  @PrimaryKey val id: String,
  val title: String,
  val description: String,
  val startAtUtcMillis: Long,
  val endAtUtcMillis: Long,
  val zoneId: String,
  val location: String?,
  val isAllDay: Boolean,
  val reminderMinutesBefore: Int?,
  val recurrenceRule: String?,
  val createdAtUtcMillis: Long,
  val updatedAtUtcMillis: Long,
  val syncStatus: String,
  val isDeleted: Boolean
)

@Entity(
  tableName = "memory_entries",
  indices = [
    Index(value = ["isDeleted", "entryType", "updatedAtUtcMillis"]),
    Index(value = ["syncStatus"])
  ]
)
data class MemoryEntryEntity(
  @PrimaryKey val id: String,
  val title: String,
  val content: String,
  val entryType: String,
  val tagsCsv: String,
  val sourceUrl: String?,
  val isPinned: Boolean,
  val createdAtUtcMillis: Long,
  val updatedAtUtcMillis: Long,
  val syncStatus: String,
  val isDeleted: Boolean
)

@Entity(
  tableName = "document_records",
  indices = [
    Index(value = ["isDeleted", "updatedAtUtcMillis"]),
    Index(value = ["syncStatus"])
  ]
)
data class DocumentRecordEntity(
  @PrimaryKey val id: String,
  val title: String,
  val mimeType: String,
  val localRelativePath: String,
  val fileSizeBytes: Long,
  val documentDateUtcMillis: Long?,
  val tagsCsv: String,
  val userVerifiedNotes: String,
  val extractedSummary: String?,
  val extractionStatus: String,
  val createdAtUtcMillis: Long,
  val updatedAtUtcMillis: Long,
  val syncStatus: String,
  val isDeleted: Boolean
)

@Entity(
  tableName = "sync_outbox",
  indices = [
    Index(value = ["entityType", "entityId"]),
    Index(value = ["createdAtUtcMillis"])
  ]
)
data class SyncOutboxEntity(
  @PrimaryKey val id: String,
  val entityType: String,
  val entityId: String,
  val operation: String,
  val payloadJson: String,
  val retryCount: Int,
  val lastAttemptAtUtcMillis: Long?,
  val lastError: String?,
  val createdAtUtcMillis: Long
)
