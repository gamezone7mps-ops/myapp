package com.example.domain.model

enum class AppLanguage(val code: String) {
  PERSIAN("fa"),
  ENGLISH("en");

  companion object {
    fun fromCode(code: String?): AppLanguage =
      entries.firstOrNull { it.code == code } ?: PERSIAN
  }
}

enum class AppThemeMode(val key: String) {
  SYSTEM("system"),
  LIGHT("light"),
  DARK("dark");

  companion object {
    fun fromKey(key: String?): AppThemeMode =
      entries.firstOrNull { it.key == key } ?: SYSTEM
  }
}

enum class CalendarSystem(val key: String) {
  JALALI("jalali"),
  GREGORIAN("gregorian");

  companion object {
    fun fromKey(key: String?): CalendarSystem =
      entries.firstOrNull { it.key == key } ?: JALALI
  }
}

enum class SyncStatus(val key: String) {
  LOCAL_ONLY("LOCAL_ONLY"),
  PENDING_UPSERT("PENDING_UPSERT"),
  PENDING_DELETE("PENDING_DELETE"),
  SYNCED("SYNCED"),
  CONFLICT("CONFLICT");

  companion object {
    fun fromKey(key: String?): SyncStatus =
      entries.firstOrNull { it.key == key } ?: LOCAL_ONLY
  }
}

enum class TaskPriority(val level: Int) {
  LOW(0),
  MEDIUM(1),
  HIGH(2),
  URGENT(3);

  companion object {
    fun fromLevel(level: Int): TaskPriority =
      entries.firstOrNull { it.level == level } ?: MEDIUM
  }
}

enum class TaskStatus(val key: String) {
  TODO("TODO"),
  IN_PROGRESS("IN_PROGRESS"),
  COMPLETED("COMPLETED"),
  ARCHIVED("ARCHIVED");

  companion object {
    fun fromKey(key: String?): TaskStatus =
      entries.firstOrNull { it.key == key } ?: TODO
  }
}

enum class MemoryEntryType(val key: String) {
  NOTE("NOTE"),
  IDEA("IDEA"),
  LINK("LINK"),
  FACT("FACT"),
  REFERENCE("REFERENCE");

  companion object {
    fun fromKey(key: String?): MemoryEntryType =
      entries.firstOrNull { it.key == key } ?: NOTE
  }
}

enum class ExtractionStatus(val key: String) {
  NOT_ANALYZED("NOT_ANALYZED"),
  COMPLETED("COMPLETED"),
  FAILED("FAILED");

  companion object {
    fun fromKey(key: String?): ExtractionStatus =
      entries.firstOrNull { it.key == key } ?: NOT_ANALYZED
  }
}

data class UserPreferences(
  val language: AppLanguage = AppLanguage.PERSIAN,
  val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  val calendarSystem: CalendarSystem = CalendarSystem.JALALI,
  val notificationsEnabled: Boolean = true,
  val aiDataConsentGranted: Boolean = false,
  val defaultZoneId: String = "Asia/Tehran"
)

data class TaskItem(
  val id: String,
  val title: String,
  val description: String = "",
  val dueAtUtcMillis: Long? = null,
  val priority: TaskPriority = TaskPriority.MEDIUM,
  val status: TaskStatus = TaskStatus.TODO,
  val category: String? = null,
  val recurrenceRule: String? = null,
  val reminderMinutesBefore: Int? = null,
  val completedAtUtcMillis: Long? = null,
  val createdAtUtcMillis: Long,
  val updatedAtUtcMillis: Long,
  val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
  val isDeleted: Boolean = false
)

data class CalendarEvent(
  val id: String,
  val title: String,
  val description: String = "",
  val startAtUtcMillis: Long,
  val endAtUtcMillis: Long,
  val zoneId: String,
  val location: String? = null,
  val isAllDay: Boolean = false,
  val reminderMinutesBefore: Int? = null,
  val recurrenceRule: String? = null,
  val createdAtUtcMillis: Long,
  val updatedAtUtcMillis: Long,
  val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
  val isDeleted: Boolean = false
)

data class MemoryEntry(
  val id: String,
  val title: String,
  val content: String,
  val entryType: MemoryEntryType = MemoryEntryType.NOTE,
  val tags: List<String> = emptyList(),
  val sourceUrl: String? = null,
  val isPinned: Boolean = false,
  val createdAtUtcMillis: Long,
  val updatedAtUtcMillis: Long,
  val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
  val isDeleted: Boolean = false
)

data class DocumentRecord(
  val id: String,
  val title: String,
  val mimeType: String,
  val localRelativePath: String,
  val fileSizeBytes: Long,
  val documentDateUtcMillis: Long? = null,
  val tags: List<String> = emptyList(),
  val userVerifiedNotes: String = "",
  val extractedSummary: String? = null,
  val extractionStatus: ExtractionStatus = ExtractionStatus.NOT_ANALYZED,
  val createdAtUtcMillis: Long,
  val updatedAtUtcMillis: Long,
  val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
  val isDeleted: Boolean = false
)

data class VaultCounts(
  val activeTasksCount: Int = 0,
  val eventsCount: Int = 0,
  val memoriesCount: Int = 0,
  val documentsCount: Int = 0
)
