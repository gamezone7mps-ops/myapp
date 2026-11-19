package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
  entities = [
    TaskEntity::class,
    CalendarEventEntity::class,
    MemoryEntryEntity::class,
    DocumentRecordEntity::class,
    SyncOutboxEntity::class
  ],
  version = 1,
  exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun taskDao(): TaskDao
  abstract fun calendarEventDao(): CalendarEventDao
  abstract fun memoryEntryDao(): MemoryEntryDao
  abstract fun documentRecordDao(): DocumentRecordDao
  abstract fun syncOutboxDao(): SyncOutboxDao

  companion object {
    const val DATABASE_NAME = "lifeos_database.db"

    // Explicit migration registry — never use fallbackToDestructiveMigration().
    val ALL_MIGRATIONS: Array<Migration> = emptyArray()

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          DATABASE_NAME
        )
          .addMigrations(*ALL_MIGRATIONS)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
