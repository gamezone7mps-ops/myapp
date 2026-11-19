package com.example.features.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.InputValidators
import com.example.core.common.ValidationError
import com.example.core.datastore.PreferencesRepository
import com.example.domain.model.MemoryEntry
import com.example.domain.model.MemoryEntryType
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserPreferences
import com.example.domain.repository.MemoryRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryEditorDraft(
  val id: String,
  val isNew: Boolean,
  val title: String = "",
  val content: String = "",
  val entryType: MemoryEntryType = MemoryEntryType.NOTE,
  val tagsText: String = "",
  val sourceUrl: String = "",
  val isPinned: Boolean = false,
  val createdAtUtcMillis: Long = 0L,
  val validationError: ValidationError? = null
)

data class MemoryControlState(
  val searchQuery: String = "",
  val selectedType: MemoryEntryType? = null,
  val selectedTag: String? = null,
  val editorDraft: MemoryEditorDraft? = null,
  val isSaving: Boolean = false,
  val pendingDeleteMemory: MemoryEntry? = null,
  val snackbarError: String? = null
)

sealed interface MemoryUiState {
  data object Loading : MemoryUiState
  data class Success(
    val preferences: UserPreferences,
    val visibleMemories: List<MemoryEntry>,
    val availableTags: List<String>,
    val controls: MemoryControlState
  ) : MemoryUiState
  data class Error(val message: String) : MemoryUiState
}

class MemoryViewModel(
  private val memoryRepository: MemoryRepository,
  preferencesRepository: PreferencesRepository
) : ViewModel() {

  private val controls = MutableStateFlow(MemoryControlState())

  val uiState: StateFlow<MemoryUiState> = combine(
    memoryRepository.observeMemories(),
    preferencesRepository.userPreferences,
    controls
  ) { allMemories, prefs, ctrl ->
    val tags = allMemories.flatMap { it.tags }
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .distinct()
      .sorted()

    val q = ctrl.searchQuery.trim()
    val filtered = allMemories.filter { entry ->
      val matchesType = ctrl.selectedType == null || entry.entryType == ctrl.selectedType
      val matchesTag = ctrl.selectedTag == null ||
        entry.tags.any { it.equals(ctrl.selectedTag, ignoreCase = true) }
      val matchesQuery = q.isEmpty() ||
        entry.title.contains(q, ignoreCase = true) ||
        entry.content.contains(q, ignoreCase = true) ||
        entry.tags.any { it.contains(q, ignoreCase = true) } ||
        (entry.sourceUrl?.contains(q, ignoreCase = true) == true)
      matchesType && matchesTag && matchesQuery
    }

    MemoryUiState.Success(
      preferences = prefs,
      visibleMemories = filtered,
      availableTags = tags,
      controls = ctrl
    )
  }
    .catch<MemoryUiState> { emit(MemoryUiState.Error(it.localizedMessage ?: "Failed to load memories")) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = MemoryUiState.Loading
    )

  fun onSearchQueryChanged(query: String) {
    controls.update { it.copy(searchQuery = query) }
  }

  fun onTypeSelected(type: MemoryEntryType?) {
    controls.update { it.copy(selectedType = type) }
  }

  fun onTagSelected(tag: String?) {
    controls.update { it.copy(selectedTag = tag) }
  }

  fun openNewMemoryEditor() {
    controls.update {
      it.copy(
        editorDraft = MemoryEditorDraft(
          id = UUID.randomUUID().toString(),
          isNew = true,
          createdAtUtcMillis = System.currentTimeMillis()
        )
      )
    }
  }

  fun openEditMemoryEditor(entry: MemoryEntry) {
    controls.update {
      it.copy(
        editorDraft = MemoryEditorDraft(
          id = entry.id,
          isNew = false,
          title = entry.title,
          content = entry.content,
          entryType = entry.entryType,
          tagsText = entry.tags.joinToString(", "),
          sourceUrl = entry.sourceUrl.orEmpty(),
          isPinned = entry.isPinned,
          createdAtUtcMillis = entry.createdAtUtcMillis
        )
      )
    }
  }

  fun updateDraft(transform: (MemoryEditorDraft) -> MemoryEditorDraft) {
    controls.update { state ->
      val current = state.editorDraft ?: return@update state
      state.copy(editorDraft = transform(current).copy(validationError = null))
    }
  }

  fun dismissEditor() {
    controls.update { it.copy(editorDraft = null, isSaving = false) }
  }

  fun saveDraft() {
    val state = controls.value
    val draft = state.editorDraft ?: return
    if (state.isSaving) return

    val validation = InputValidators.validateMemory(
      title = draft.title,
      content = draft.content,
      sourceUrl = draft.sourceUrl
    )
    if (validation != null) {
      controls.update { it.copy(editorDraft = draft.copy(validationError = validation)) }
      return
    }

    controls.update { it.copy(isSaving = true) }
    viewModelScope.launch {
      try {
        val now = System.currentTimeMillis()
        val existing = if (draft.isNew) null else memoryRepository.getMemoryById(draft.id)
        val parsedTags = draft.tagsText
          .split(",", "،")
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .distinct()
        val resolvedTitle = draft.title.trim().ifEmpty {
          draft.content.trim().lineSequence().firstOrNull()?.take(48).orEmpty()
        }
        val entry = MemoryEntry(
          id = draft.id,
          title = resolvedTitle,
          content = draft.content.trim(),
          entryType = draft.entryType,
          tags = parsedTags,
          sourceUrl = draft.sourceUrl.trim().ifEmpty { null },
          isPinned = draft.isPinned,
          createdAtUtcMillis = existing?.createdAtUtcMillis ?: draft.createdAtUtcMillis.takeIf { it > 0L } ?: now,
          updatedAtUtcMillis = now,
          syncStatus = SyncStatus.PENDING_UPSERT,
          isDeleted = false
        )
        memoryRepository.upsertMemory(entry)
        controls.update { it.copy(editorDraft = null, isSaving = false) }
      } catch (e: Exception) {
        controls.update {
          it.copy(isSaving = false, snackbarError = e.localizedMessage ?: "Failed to save memory")
        }
      }
    }
  }

  fun togglePin(entry: MemoryEntry) {
    viewModelScope.launch {
      memoryRepository.upsertMemory(
        entry.copy(
          isPinned = !entry.isPinned,
          updatedAtUtcMillis = System.currentTimeMillis(),
          syncStatus = SyncStatus.PENDING_UPSERT
        )
      )
    }
  }

  fun requestDelete(entry: MemoryEntry) {
    controls.update { it.copy(pendingDeleteMemory = entry) }
  }

  fun cancelDelete() {
    controls.update { it.copy(pendingDeleteMemory = null) }
  }

  fun confirmDelete() {
    val entry = controls.value.pendingDeleteMemory ?: return
    controls.update { it.copy(pendingDeleteMemory = null) }
    viewModelScope.launch {
      memoryRepository.softDeleteMemory(entry.id, System.currentTimeMillis())
    }
  }

  fun consumeError() {
    controls.update { it.copy(snackbarError = null) }
  }

  class Factory(
    private val memoryRepository: MemoryRepository,
    private val preferencesRepository: PreferencesRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MemoryViewModel(memoryRepository, preferencesRepository) as T
    }
  }
}
