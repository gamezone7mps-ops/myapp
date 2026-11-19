package com.example.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.calendar.CalendarDate
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.common.InputValidators
import com.example.core.datastore.PreferencesRepository
import com.example.domain.model.CalendarEvent
import com.example.domain.model.MemoryEntry
import com.example.domain.model.RecurrenceRule
import com.example.domain.model.SyncStatus
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskStatus
import com.example.domain.model.UserPreferences
import com.example.domain.model.VaultCounts
import com.example.domain.repository.CalendarRepository
import com.example.domain.repository.MemoryRepository
import com.example.domain.repository.TaskRepository
import com.example.domain.repository.VaultSummaryRepository
import com.example.features.calendar.EventEditorDraft
import com.example.features.memory.MemoryEditorDraft
import com.example.features.tasks.TaskEditorDraft
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface QuickCaptureDraft {
  data class Task(val draft: TaskEditorDraft) : QuickCaptureDraft
  data class Event(val draft: EventEditorDraft) : QuickCaptureDraft
  data class Memory(val draft: MemoryEditorDraft) : QuickCaptureDraft
}

data class DashboardControls(
  val quickDraft: QuickCaptureDraft? = null,
  val isSaving: Boolean = false
)

sealed interface DashboardUiState {
  data object Loading : DashboardUiState
  data class Success(
    val preferences: UserPreferences,
    val counts: VaultCounts,
    val dueTasks: List<TaskItem>,
    val upcomingEvents: List<CalendarEvent>,
    val pinnedMemories: List<MemoryEntry>,
    val controls: DashboardControls
  ) : DashboardUiState
  data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(
  preferencesRepository: PreferencesRepository,
  vaultSummaryRepository: VaultSummaryRepository,
  private val taskRepository: TaskRepository,
  private val calendarRepository: CalendarRepository,
  private val memoryRepository: MemoryRepository
) : ViewModel() {

  private val controls = MutableStateFlow(DashboardControls())

  val uiState: StateFlow<DashboardUiState> = combine(
    preferencesRepository.userPreferences,
    vaultSummaryRepository.observeVaultCounts(),
    taskRepository.observeTasks(),
    calendarRepository.observeEvents(),
    memoryRepository.observeMemories(),
    controls
  ) { args ->
    val prefs = args[0] as UserPreferences
    val counts = args[1] as VaultCounts
    @Suppress("UNCHECKED_CAST")
    val tasks = args[2] as List<TaskItem>
    @Suppress("UNCHECKED_CAST")
    val events = args[3] as List<CalendarEvent>
    @Suppress("UNCHECKED_CAST")
    val memories = args[4] as List<MemoryEntry>
    val ctrl = args[5] as DashboardControls

    val now = System.currentTimeMillis()
    val todayParts = PersianCalendarEngine.utcMillisToZonedParts(
      now,
      prefs.defaultZoneId,
      prefs.calendarSystem
    )
    val todayStart = PersianCalendarEngine.startOfDayUtcMillis(
      CalendarDate(todayParts.year, todayParts.month, todayParts.day),
      prefs.defaultZoneId,
      prefs.calendarSystem
    )

    val dueTasks = tasks
      .filter { it.status == TaskStatus.TODO || it.status == TaskStatus.IN_PROGRESS }
      .sortedWith(
        compareBy<TaskItem> { it.dueAtUtcMillis == null }
          .thenBy { it.dueAtUtcMillis ?: Long.MAX_VALUE }
          .thenByDescending { it.priority.level }
      )
      .take(4)

    val upcomingEvents = events
      .filter { it.endAtUtcMillis >= todayStart }
      .sortedBy { it.startAtUtcMillis }
      .take(3)

    val pinnedMemories = memories
      .filter { it.isPinned }
      .take(2)

    DashboardUiState.Success(
      preferences = prefs,
      counts = counts,
      dueTasks = dueTasks,
      upcomingEvents = upcomingEvents,
      pinnedMemories = pinnedMemories,
      controls = ctrl
    )
  }
    .catch<DashboardUiState> { emit(DashboardUiState.Error(it.localizedMessage ?: "Dashboard load failed")) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = DashboardUiState.Loading
    )

  fun toggleTaskComplete(task: TaskItem, preferences: UserPreferences) {
    viewModelScope.launch {
      taskRepository.completeTask(
        task = task,
        nowUtcMillis = System.currentTimeMillis(),
        zoneId = preferences.defaultZoneId,
        calendarSystem = preferences.calendarSystem
      )
    }
  }

  fun openQuickTask() {
    controls.update {
      it.copy(
        quickDraft = QuickCaptureDraft.Task(
          TaskEditorDraft(
            id = UUID.randomUUID().toString(),
            isNew = true,
            createdAtUtcMillis = System.currentTimeMillis()
          )
        )
      )
    }
  }

  fun openQuickEvent(preferences: UserPreferences) {
    val now = System.currentTimeMillis()
    val parts = PersianCalendarEngine.utcMillisToZonedParts(
      now,
      preferences.defaultZoneId,
      preferences.calendarSystem
    )
    val startMillis = PersianCalendarEngine.zonedPartsToUtcMillis(
      year = parts.year,
      month = parts.month,
      day = parts.day,
      hour = (parts.hour + 1).coerceAtMost(23),
      minute = 0,
      zoneId = preferences.defaultZoneId,
      calendarSystem = preferences.calendarSystem
    )
    controls.update {
      it.copy(
        quickDraft = QuickCaptureDraft.Event(
          EventEditorDraft(
            id = UUID.randomUUID().toString(),
            isNew = true,
            startAtUtcMillis = startMillis,
            endAtUtcMillis = startMillis + 3_600_000L,
            zoneId = preferences.defaultZoneId,
            createdAtUtcMillis = now
          )
        )
      )
    }
  }

  fun openQuickMemory() {
    controls.update {
      it.copy(
        quickDraft = QuickCaptureDraft.Memory(
          MemoryEditorDraft(
            id = UUID.randomUUID().toString(),
            isNew = true,
            createdAtUtcMillis = System.currentTimeMillis()
          )
        )
      )
    }
  }

  fun updateTaskDraft(transform: (TaskEditorDraft) -> TaskEditorDraft) {
    controls.update { state ->
      val current = state.quickDraft as? QuickCaptureDraft.Task ?: return@update state
      state.copy(quickDraft = QuickCaptureDraft.Task(transform(current.draft).copy(validationError = null)))
    }
  }

  fun updateEventDraft(transform: (EventEditorDraft) -> EventEditorDraft) {
    controls.update { state ->
      val current = state.quickDraft as? QuickCaptureDraft.Event ?: return@update state
      state.copy(quickDraft = QuickCaptureDraft.Event(transform(current.draft).copy(validationError = null)))
    }
  }

  fun updateMemoryDraft(transform: (MemoryEditorDraft) -> MemoryEditorDraft) {
    controls.update { state ->
      val current = state.quickDraft as? QuickCaptureDraft.Memory ?: return@update state
      state.copy(quickDraft = QuickCaptureDraft.Memory(transform(current.draft).copy(validationError = null)))
    }
  }

  fun dismissQuickDraft() {
    controls.update { it.copy(quickDraft = null, isSaving = false) }
  }

  fun saveQuickDraft() {
    val current = controls.value
    if (current.isSaving) return
    val quick = current.quickDraft ?: return

    when (quick) {
      is QuickCaptureDraft.Task -> {
        val err = InputValidators.validateTaskTitle(quick.draft.title)
        if (err != null) {
          controls.update {
            it.copy(quickDraft = QuickCaptureDraft.Task(quick.draft.copy(validationError = err)))
          }
          return
        }
        controls.update { it.copy(isSaving = true) }
        viewModelScope.launch {
          val now = System.currentTimeMillis()
          taskRepository.upsertTask(
            TaskItem(
              id = quick.draft.id,
              title = quick.draft.title.trim(),
              description = quick.draft.description.trim(),
              dueAtUtcMillis = quick.draft.dueAtUtcMillis,
              priority = quick.draft.priority,
              status = quick.draft.status,
              category = quick.draft.category.trim().ifEmpty { null },
              recurrenceRule = if (quick.draft.recurrence == RecurrenceRule.NONE) null else quick.draft.recurrence.key,
              reminderMinutesBefore = if (quick.draft.dueAtUtcMillis != null) quick.draft.reminderMinutesBefore else null,
              createdAtUtcMillis = now,
              updatedAtUtcMillis = now,
              syncStatus = SyncStatus.PENDING_UPSERT
            )
          )
          controls.update { it.copy(quickDraft = null, isSaving = false) }
        }
      }

      is QuickCaptureDraft.Event -> {
        val err = InputValidators.validateEvent(
          quick.draft.title,
          quick.draft.startAtUtcMillis,
          quick.draft.endAtUtcMillis
        )
        if (err != null) {
          controls.update {
            it.copy(quickDraft = QuickCaptureDraft.Event(quick.draft.copy(validationError = err)))
          }
          return
        }
        controls.update { it.copy(isSaving = true) }
        viewModelScope.launch {
          val now = System.currentTimeMillis()
          calendarRepository.upsertEvent(
            CalendarEvent(
              id = quick.draft.id,
              title = quick.draft.title.trim(),
              description = quick.draft.description.trim(),
              startAtUtcMillis = quick.draft.startAtUtcMillis,
              endAtUtcMillis = quick.draft.endAtUtcMillis,
              zoneId = quick.draft.zoneId,
              location = quick.draft.location.trim().ifEmpty { null },
              isAllDay = quick.draft.isAllDay,
              reminderMinutesBefore = quick.draft.reminderMinutesBefore,
              createdAtUtcMillis = now,
              updatedAtUtcMillis = now,
              syncStatus = SyncStatus.PENDING_UPSERT
            )
          )
          controls.update { it.copy(quickDraft = null, isSaving = false) }
        }
      }

      is QuickCaptureDraft.Memory -> {
        val err = InputValidators.validateMemory(
          quick.draft.title,
          quick.draft.content,
          quick.draft.sourceUrl
        )
        if (err != null) {
          controls.update {
            it.copy(quickDraft = QuickCaptureDraft.Memory(quick.draft.copy(validationError = err)))
          }
          return
        }
        controls.update { it.copy(isSaving = true) }
        viewModelScope.launch {
          val now = System.currentTimeMillis()
          val parsedTags = quick.draft.tagsText
            .split(",", "،")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
          val resolvedTitle = quick.draft.title.trim().ifEmpty {
            quick.draft.content.trim().lineSequence().firstOrNull()?.take(48).orEmpty()
          }
          memoryRepository.upsertMemory(
            MemoryEntry(
              id = quick.draft.id,
              title = resolvedTitle,
              content = quick.draft.content.trim(),
              entryType = quick.draft.entryType,
              tags = parsedTags,
              sourceUrl = quick.draft.sourceUrl.trim().ifEmpty { null },
              isPinned = quick.draft.isPinned,
              createdAtUtcMillis = now,
              updatedAtUtcMillis = now,
              syncStatus = SyncStatus.PENDING_UPSERT
            )
          )
          controls.update { it.copy(quickDraft = null, isSaving = false) }
        }
      }
    }
  }

  class Factory(
    private val preferencesRepository: PreferencesRepository,
    private val vaultSummaryRepository: VaultSummaryRepository,
    private val taskRepository: TaskRepository,
    private val calendarRepository: CalendarRepository,
    private val memoryRepository: MemoryRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DashboardViewModel(
        preferencesRepository = preferencesRepository,
        vaultSummaryRepository = vaultSummaryRepository,
        taskRepository = taskRepository,
        calendarRepository = calendarRepository,
        memoryRepository = memoryRepository
      ) as T
    }
  }
}
