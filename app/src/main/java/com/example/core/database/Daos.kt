package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
  @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY CASE WHEN dueAtUtcMillis IS NULL THEN 1 ELSE 0 END, dueAtUtcMillis ASC, priority DESC")
  fun observeActiveTasks(): Flow<List<TaskEntity>>

  @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0 AND status != 'COMPLETED' AND status != 'ARCHIVED'")
  fun observePendingTaskCount(): Flow<Int>

  @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): TaskEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: TaskEntity)

  @Transaction
  suspend fun completeRecurringTask(completedTask: TaskEntity, nextTask: TaskEntity) {
    upsert(completedTask)
    upsert(nextTask)
  }

  @Query("UPDATE tasks SET isDeleted = 1, updatedAtUtcMillis = :updatedAt, syncStatus = :syncStatus WHERE id = :id")
  suspend fun softDelete(id: String, updatedAt: Long, syncStatus: String)
}

@Dao
interface CalendarEventDao {
  @Query("SELECT * FROM calendar_events WHERE isDeleted = 0 ORDER BY startAtUtcMillis ASC")
  fun observeEvents(): Flow<List<CalendarEventEntity>>

  @Query("SELECT COUNT(*) FROM calendar_events WHERE isDeleted = 0")
  fun observeEventCount(): Flow<Int>

  @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): CalendarEventEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: CalendarEventEntity)

  @Query("UPDATE calendar_events SET isDeleted = 1, updatedAtUtcMillis = :updatedAt, syncStatus = :syncStatus WHERE id = :id")
  suspend fun softDelete(id: String, updatedAt: Long, syncStatus: String)
}

@Dao
interface MemoryEntryDao {
  @Query("SELECT * FROM memory_entries WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAtUtcMillis DESC")
  fun observeMemories(): Flow<List<MemoryEntryEntity>>

  @Query("SELECT COUNT(*) FROM memory_entries WHERE isDeleted = 0")
  fun observeMemoryCount(): Flow<Int>

  @Query("SELECT * FROM memory_entries WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): MemoryEntryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: MemoryEntryEntity)

  @Query("UPDATE memory_entries SET isDeleted = 1, updatedAtUtcMillis = :updatedAt, syncStatus = :syncStatus WHERE id = :id")
  suspend fun softDelete(id: String, updatedAt: Long, syncStatus: String)
}

@Dao
interface DocumentRecordDao {
  @Query("SELECT * FROM document_records WHERE isDeleted = 0 ORDER BY updatedAtUtcMillis DESC")
  fun observeDocuments(): Flow<List<DocumentRecordEntity>>

  @Query("SELECT COUNT(*) FROM document_records WHERE isDeleted = 0")
  fun observeDocumentCount(): Flow<Int>

  @Query("SELECT * FROM document_records WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): DocumentRecordEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: DocumentRecordEntity)

  @Query("UPDATE document_records SET isDeleted = 1, updatedAtUtcMillis = :updatedAt, syncStatus = :syncStatus WHERE id = :id")
  suspend fun softDelete(id: String, updatedAt: Long, syncStatus: String)
}

@Dao
interface SyncOutboxDao {
  @Query("SELECT * FROM sync_outbox ORDER BY createdAtUtcMillis ASC")
  fun observeOutbox(): Flow<List<SyncOutboxEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun enqueue(item: SyncOutboxEntity)

  @Query("DELETE FROM sync_outbox WHERE id = :id")
  suspend fun remove(id: String)
}
