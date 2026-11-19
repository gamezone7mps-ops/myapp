package com.example.features.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.PreferencesRepository
import com.example.domain.model.UserPreferences
import com.example.domain.repository.CalendarRepository
import com.example.domain.repository.DocumentRepository
import com.example.domain.repository.MemoryRepository
import com.example.domain.repository.TaskRepository
import com.example.domain.usecase.ComputeInsightsUseCase
import com.example.domain.usecase.InsightsSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface InsightsUiState {
  data object Loading : InsightsUiState
  data class Success(
    val preferences: UserPreferences,
    val summary: InsightsSummary
  ) : InsightsUiState
  data class Error(val message: String) : InsightsUiState
}

class InsightsViewModel(
  taskRepository: TaskRepository,
  calendarRepository: CalendarRepository,
  memoryRepository: MemoryRepository,
  documentRepository: DocumentRepository,
  preferencesRepository: PreferencesRepository,
  private val computeInsightsUseCase: ComputeInsightsUseCase = ComputeInsightsUseCase()
) : ViewModel() {

  val uiState: StateFlow<InsightsUiState> = combine(
    taskRepository.observeTasks(),
    calendarRepository.observeEvents(),
    memoryRepository.observeMemories(),
    documentRepository.observeDocuments(),
    preferencesRepository.userPreferences
  ) { tasks, events, memories, documents, prefs ->
    val summary = computeInsightsUseCase(
      tasks = tasks,
      events = events,
      memories = memories,
      documents = documents,
      nowUtcMillis = System.currentTimeMillis(),
      zoneId = prefs.defaultZoneId,
      calendarSystem = prefs.calendarSystem
    )
    InsightsUiState.Success(preferences = prefs, summary = summary)
  }
    .catch<InsightsUiState> { emit(InsightsUiState.Error(it.localizedMessage ?: "Insights error")) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = InsightsUiState.Loading
    )

  class Factory(
    private val taskRepository: TaskRepository,
    private val calendarRepository: CalendarRepository,
    private val memoryRepository: MemoryRepository,
    private val documentRepository: DocumentRepository,
    private val preferencesRepository: PreferencesRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return InsightsViewModel(
        taskRepository = taskRepository,
        calendarRepository = calendarRepository,
        memoryRepository = memoryRepository,
        documentRepository = documentRepository,
        preferencesRepository = preferencesRepository
      ) as T
    }
  }
}
