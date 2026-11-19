package com.example.features.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.InputValidators
import com.example.core.common.ValidationError
import com.example.core.datastore.PreferencesRepository
import com.example.domain.model.RecurrenceRule
import com.example.domain.model.SyncStatus
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus
import com.example.domain.model.UserPreferences
import com.example.domain.repository.TaskRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaskFilter { ACTIVE, COMPLETED, ARCHIVED, ALL }
enum class TaskSort { DUE_DATE, PRIORITY, UPDATED }

data class TaskEditorDraft(
  val id: String,
  val isNew: Boolean,
  val title: String = "",
  val description: String = "",
  val category: String = "",
  val priority: TaskPriority = TaskPriority.MEDIUM,
  val status: TaskStatus = TaskStatus.TODO,
  val dueAtUtcMillis: Long? = null,
  val reminderMinutesBefore: Int? = null,
  val recurrence: RecurrenceRule = RecurrenceRule.NONE,
  val createdAtUtcMillis: Long = 0L,
  val validationError: ValidationError? = null
)

data class TasksControlState(
  val searchQuery: String = "",
  val filter: TaskFilter = TaskFilter.ACTIVE,
  val sort: TaskSort = TaskSort.DUE_DATE,
  val selectedCategory: String? = null,
  val editorDraft: TaskEditorDraft? = null,
  val isSaving: Boolean = false,
  val pendingDeleteTask: TaskItem? = null,
  val snackbarEvent: String? = null
)

sealed interface TasksUiState {
  data object Loading : TasksUiState
  data class Success(
    val preferences: UserPreferences,
    val visibleTasks: List<TaskItem>,
    val availableCategories: List<String>,
    val controls: TasksControlState
  ) : TasksUiState
  data class Error(val message: String) : TasksUiState
}

class TasksViewModel(
  private val taskRepository: TaskRepository,
  preferencesRepository: PreferencesRepository
) : ViewModel() {

  private val controls = MutableStateFlow(TasksControlState())

  val uiState: StateFlow<TasksUiState> = combine(
    taskRepository.observeTasks(),
    preferencesRepository.userPreferences,
    controls
  ) { allTasks, prefs, ctrl ->
    val categories = allTasks.mapNotNull { it.category?.trim() }
      .filter { it.isNotEmpty() }
      .distinct()
      .sorted()

    val filtered = allTasks.filter { task ->
      val matchesFilter = when (ctrl.filter) {
        TaskFilter.ACTIVE -> task.status == TaskStatus.TODO || task.status == TaskStatus.IN_PROGRESS
        TaskFilter.COMPLETED -> task.status == TaskStatus.COMPLETED
        TaskFilter.ARCHIVED -> task.status == TaskStatus.ARCHIVED
        TaskFilter.ALL -> true
      }
      val matchesCategory = ctrl.selectedCategory == null ||
        task.category.equals(ctrl.selectedCategory, ignoreCase = true)
      val q = ctrl.searchQuery.trim()
      val matchesQuery = q.isEmpty() ||
        task.title.contains(q, ignoreCase = true) ||
        task.description.contains(q, ignoreCase = true) ||
        (task.category?.contains(q, ignoreCase = true) == true)
      matchesFilter && matchesCategory && matchesQuery
    }

    val sorted = when (ctrl.sort) {
      TaskSort.DUE_DATE -> filtered.sortedWith(
        compareBy<TaskItem> { it.dueAtUtcMillis == null }
          .thenBy { it.dueAtUtcMillis ?: Long.MAX_VALUE }
          .thenByDescending { it.priority.level }
      )
      TaskSort.PRIORITY -> filtered.sortedWith(
        compareByDescending<TaskItem> { it.priority.level }
          .thenBy { it.dueAtUtcMillis == null }
          .thenBy { it.dueAtUtcMillis ?: Long.MAX_VALUE }
      )
      TaskSort.UPDATED -> filtered.sortedByDescending { it.updatedAtUtcMillis }
    }

    TasksUiState.Success(
      preferences = prefs,
      visibleTasks = sorted,
      availableCategories = categories,
      controls = ctrl
    )
  }
    .catch<TasksUiState> { emit(TasksUiState.Error(it.localizedMessage ?: "Failed to load tasks")) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = TasksUiState.Loading
    )

  fun onSearchQueryChanged(query: String) {
    controls.update { it.copy(searchQuery = query) }
  }

  fun onFilterSelected(filter: TaskFilter) {
    controls.update { it.copy(filter = filter) }
  }

  fun onSortSelected(sort: TaskSort) {
    controls.update { it.copy(sort = sort) }
  }

  fun onCategorySelected(category: String?) {
    controls.update { it.copy(selectedCategory = category) }
  }

  fun openNewTaskEditor() {
    val now = System.currentTimeMillis()
    controls.update {
      it.copy(
        editorDraft = TaskEditorDraft(
          id = UUID.randomUUID().toString(),
          isNew = true,
          createdAtUtcMillis = now
        )
      )
    }
  }

  fun openEditTaskEditor(task: TaskItem) {
    controls.update {
      it.copy(
        editorDraft = TaskEditorDraft(
          id = task.id,
          isNew = false,
          title = task.title,
          description = task.description,
          category = task.category.orEmpty(),
          priority = task.priority,
          status = task.status,
          dueAtUtcMillis = task.dueAtUtcMillis,
          reminderMinutesBefore = task.reminderMinutesBefore,
          recurrence = RecurrenceRule.fromKey(task.recurrenceRule),
          createdAtUtcMillis = task.createdAtUtcMillis
        )
      )
    }
  }

  fun updateDraft(transform: (TaskEditorDraft) -> TaskEditorDraft) {
    controls.update { state ->
      val current = state.editorDraft ?: return@update state
      state.copy(editorDraft = transform(current).copy(validationError = null))
    }
  }

  fun dismissEditor() {
    controls.update { it.copy(editorDraft = null, isSaving = false) }
  }

  fun saveDraft() {
    val currentState = controls.value
    val draft = currentState.editorDraft ?: return
    if (currentState.isSaving) return

    val validation = InputValidators.validateTaskTitle(draft.title)
    if (validation != null) {
      controls.update {
        it.copy(editorDraft = draft.copy(validationError = validation))
      }
      return
    }

    controls.update { it.copy(isSaving = true) }
    viewModelScope.launch {
      try {
        val now = System.currentTimeMillis()
        val existing = if (draft.isNew) null else taskRepository.getTaskById(draft.id)
        val completedAt = when {
          draft.status == TaskStatus.COMPLETED -> existing?.completedAtUtcMillis ?: now
          else -> null
        }
        val item = TaskItem(
          id = draft.id,
          title = draft.title.trim(),
          description = draft.description.trim(),
          dueAtUtcMillis = draft.dueAtUtcMillis,
          priority = draft.priority,
          status = draft.status,
          category = draft.category.trim().ifEmpty { null },
          recurrenceRule = if (draft.recurrence == RecurrenceRule.NONE) null else draft.recurrence.key,
          reminderMinutesBefore = if (draft.dueAtUtcMillis != null) draft.reminderMinutesBefore else null,
          completedAtUtcMillis = completedAt,
          createdAtUtcMillis = existing?.createdAtUtcMillis ?: draft.createdAtUtcMillis.takeIf { it > 0L } ?: now,
          updatedAtUtcMillis = now,
          syncStatus = SyncStatus.PENDING_UPSERT,
          isDeleted = false
        )
        taskRepository.upsertTask(item)
        controls.update { it.copy(editorDraft = null, isSaving = false) }
      } catch (e: Exception) {
        controls.update {
          it.copy(isSaving = false, snackbarEvent = "save_error:${e.localizedMessage.orEmpty()}")
        }
      }
    }
  }

  fun toggleTaskComplete(task: TaskItem, preferences: UserPreferences) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      if (task.status == TaskStatus.COMPLETED) {
        taskRepository.updateTaskStatus(task, TaskStatus.TODO, now)
      } else {
        val spawnedNext = taskRepository.completeTask(
          task = task,
          nowUtcMillis = now,
          zoneId = preferences.defaultZoneId,
          calendarSystem = preferences.calendarSystem
        )
        if (spawnedNext) {
          controls.update { it.copy(snackbarEvent = "next_occurrence") }
        }
      }
    }
  }

  fun archiveTask(task: TaskItem) {
    viewModelScope.launch {
      taskRepository.updateTaskStatus(task, TaskStatus.ARCHIVED, System.currentTimeMillis())
    }
  }

  fun restoreTask(task: TaskItem) {
    viewModelScope.launch {
      taskRepository.updateTaskStatus(task, TaskStatus.TODO, System.currentTimeMillis())
    }
  }

  fun requestDelete(task: TaskItem) {
    controls.update { it.copy(pendingDeleteTask = task) }
  }

  fun cancelDelete() {
    controls.update { it.copy(pendingDeleteTask = null) }
  }

  fun confirmDelete() {
    val task = controls.value.pendingDeleteTask ?: return
    controls.update { it.copy(pendingDeleteTask = null) }
    viewModelScope.launch {
      taskRepository.softDeleteTask(task.id, System.currentTimeMillis())
    }
  }

  fun consumeSnackbarEvent() {
    controls.update { it.copy(snackbarEvent = null) }
  }

  class Factory(
    private val taskRepository: TaskRepository,
    private val preferencesRepository: PreferencesRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return TasksViewModel(taskRepository, preferencesRepository) as T
    }
  }
}
