package com.example.core.common

import android.content.Context
import com.example.core.ai.AndroidNetworkStatusProvider
import com.example.core.ai.AssistantGateway
import com.example.core.ai.DetailedDocumentAnalyzer
import com.example.core.ai.GeminiAssistantGateway
import com.example.core.ai.GeminiDocumentAnalyzer
import com.example.core.ai.NetworkStatusProvider
import com.example.core.ai.WorkspaceToolExecutor
import com.example.core.database.AppDatabase
import com.example.core.datastore.DataStorePreferencesRepository
import com.example.core.datastore.PreferencesRepository
import com.example.core.documents.AppDocumentFileStorage
import com.example.core.documents.DocumentFileStorage
import com.example.core.reminders.ReminderScheduler
import com.example.core.reminders.WorkManagerReminderScheduler
import com.example.data.local.RoomCalendarRepository
import com.example.data.local.RoomDocumentRepository
import com.example.data.local.RoomMemoryRepository
import com.example.data.local.RoomTaskRepository
import com.example.data.local.RoomVaultSummaryRepository
import com.example.domain.repository.CalendarRepository
import com.example.domain.repository.DocumentRepository
import com.example.domain.repository.MemoryRepository
import com.example.domain.repository.TaskRepository
import com.example.domain.repository.VaultSummaryRepository

interface LifeOsContainer {
  val dispatchers: DispatcherProvider
  val preferencesRepository: PreferencesRepository
  val networkStatusProvider: NetworkStatusProvider
  val reminderScheduler: ReminderScheduler
  val documentFileStorage: DocumentFileStorage
  val taskRepository: TaskRepository
  val calendarRepository: CalendarRepository
  val memoryRepository: MemoryRepository
  val documentRepository: DocumentRepository
  val vaultSummaryRepository: VaultSummaryRepository
  val workspaceToolExecutor: WorkspaceToolExecutor
  val assistantGateway: AssistantGateway
  val documentAnalyzer: DetailedDocumentAnalyzer
}

class DefaultLifeOsContainer(context: Context) : LifeOsContainer {
  private val appContext = context.applicationContext
  private val database by lazy { AppDatabase.getInstance(appContext) }

  override val dispatchers: DispatcherProvider by lazy { DefaultDispatcherProvider() }

  override val preferencesRepository: PreferencesRepository by lazy {
    DataStorePreferencesRepository(appContext)
  }

  override val networkStatusProvider: NetworkStatusProvider by lazy {
    AndroidNetworkStatusProvider(appContext)
  }

  override val reminderScheduler: ReminderScheduler by lazy {
    WorkManagerReminderScheduler(
      context = appContext,
      preferencesRepository = preferencesRepository,
      taskDao = database.taskDao(),
      calendarEventDao = database.calendarEventDao()
    )
  }

  override val documentFileStorage: DocumentFileStorage by lazy {
    AppDocumentFileStorage(
      context = appContext,
      ioDispatcher = dispatchers.io
    )
  }

  override val taskRepository: TaskRepository by lazy {
    RoomTaskRepository(database.taskDao(), reminderScheduler)
  }

  override val calendarRepository: CalendarRepository by lazy {
    RoomCalendarRepository(database.calendarEventDao(), reminderScheduler)
  }

  override val memoryRepository: MemoryRepository by lazy {
    RoomMemoryRepository(database.memoryEntryDao())
  }

  override val documentRepository: DocumentRepository by lazy {
    RoomDocumentRepository(database.documentRecordDao(), documentFileStorage)
  }

  override val vaultSummaryRepository: VaultSummaryRepository by lazy {
    RoomVaultSummaryRepository(
      taskDao = database.taskDao(),
      calendarEventDao = database.calendarEventDao(),
      memoryEntryDao = database.memoryEntryDao(),
      documentRecordDao = database.documentRecordDao()
    )
  }

  override val workspaceToolExecutor: WorkspaceToolExecutor by lazy {
    WorkspaceToolExecutor(
      taskRepository = taskRepository,
      calendarRepository = calendarRepository,
      memoryRepository = memoryRepository,
      documentRepository = documentRepository,
      documentFileStorage = documentFileStorage
    )
  }

  override val assistantGateway: AssistantGateway by lazy {
    GeminiAssistantGateway()
  }

  override val documentAnalyzer: DetailedDocumentAnalyzer by lazy {
    GeminiDocumentAnalyzer(
      documentFileStorage = documentFileStorage,
      preferencesRepository = preferencesRepository,
      networkStatusProvider = networkStatusProvider,
      ioDispatcher = dispatchers.io
    )
  }
}
