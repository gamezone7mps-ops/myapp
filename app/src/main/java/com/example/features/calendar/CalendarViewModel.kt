package com.example.features.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.calendar.CalendarDate
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.common.InputValidators
import com.example.core.common.ValidationError
import com.example.core.datastore.PreferencesRepository
import com.example.domain.model.CalendarEvent
import com.example.domain.model.CalendarSystem
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserPreferences
import com.example.domain.repository.CalendarRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CalendarViewMode { MONTH, WEEK, DAY }

data class EventEditorDraft(
  val id: String,
  val isNew: Boolean,
  val title: String = "",
  val description: String = "",
  val location: String = "",
  val startAtUtcMillis: Long,
  val endAtUtcMillis: Long,
  val zoneId: String,
  val isAllDay: Boolean = false,
  val reminderMinutesBefore: Int? = 30,
  val createdAtUtcMillis: Long = 0L,
  val validationError: ValidationError? = null
)

data class CalendarControlState(
  val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
  val selectedEpochDay: Long,
  val editorDraft: EventEditorDraft? = null,
  val isSaving: Boolean = false,
  val pendingDeleteEvent: CalendarEvent? = null,
  val snackbarError: String? = null
)

sealed interface CalendarUiState {
  data object Loading : CalendarUiState
  data class Success(
    val preferences: UserPreferences,
    val selectedDate: CalendarDate,
    val todayDate: CalendarDate,
    val allEvents: List<CalendarEvent>,
    val selectedDayEvents: List<CalendarEvent>,
    val eventEpochDays: Set<Long>,
    val controls: CalendarControlState
  ) : CalendarUiState
  data class Error(val message: String) : CalendarUiState
}

class CalendarViewModel(
  private val calendarRepository: CalendarRepository,
  private val preferencesRepository: PreferencesRepository
) : ViewModel() {

  private val controls = MutableStateFlow(
    CalendarControlState(
      selectedEpochDay = computeTodayEpochDay("Asia/Tehran", CalendarSystem.JALALI)
    )
  )

  init {
    viewModelScope.launch {
      val prefs = preferencesRepository.userPreferences.first()
      controls.update {
        it.copy(selectedEpochDay = computeTodayEpochDay(prefs.defaultZoneId, prefs.calendarSystem))
      }
    }
  }

  val uiState: StateFlow<CalendarUiState> = combine(
    calendarRepository.observeEvents(),
    preferencesRepository.userPreferences,
    controls
  ) { events, prefs, ctrl ->
    val todayEpochDay = computeTodayEpochDay(prefs.defaultZoneId, prefs.calendarSystem)
    val todayDate = PersianCalendarEngine.epochDayToDate(todayEpochDay, prefs.calendarSystem)
    val selectedDate = PersianCalendarEngine.epochDayToDate(ctrl.selectedEpochDay, prefs.calendarSystem)

    val dayStart = PersianCalendarEngine.startOfDayUtcMillis(
      selectedDate,
      prefs.defaultZoneId,
      prefs.calendarSystem
    )
    val dayEnd = PersianCalendarEngine.endOfDayUtcMillis(
      selectedDate,
      prefs.defaultZoneId,
      prefs.calendarSystem
    )

    val selectedDayEvents = events.filter { ev ->
      ev.startAtUtcMillis <= dayEnd && ev.endAtUtcMillis >= dayStart
    }.sortedBy { it.startAtUtcMillis }

    val eventDays = buildSet {
      events.forEach { ev ->
        val startParts = PersianCalendarEngine.utcMillisToZonedParts(
          ev.startAtUtcMillis,
          prefs.defaultZoneId,
          prefs.calendarSystem
        )
        val endParts = PersianCalendarEngine.utcMillisToZonedParts(
          ev.endAtUtcMillis,
          prefs.defaultZoneId,
          prefs.calendarSystem
        )
        val startDay = PersianCalendarEngine.dateToEpochDay(
          CalendarDate(startParts.year, startParts.month, startParts.day),
          prefs.calendarSystem
        )
        val endDay = PersianCalendarEngine.dateToEpochDay(
          CalendarDate(endParts.year, endParts.month, endParts.day),
          prefs.calendarSystem
        ).coerceAtMost(startDay + 60)
        for (d in startDay..endDay) add(d)
      }
    }

    CalendarUiState.Success(
      preferences = prefs,
      selectedDate = selectedDate,
      todayDate = todayDate,
      allEvents = events,
      selectedDayEvents = selectedDayEvents,
      eventEpochDays = eventDays,
      controls = ctrl
    )
  }
    .catch<CalendarUiState> { emit(CalendarUiState.Error(it.localizedMessage ?: "Calendar load failed")) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = CalendarUiState.Loading
    )

  fun setViewMode(mode: CalendarViewMode) {
    controls.update { it.copy(viewMode = mode) }
  }

  fun selectDate(date: CalendarDate, calendarSystem: CalendarSystem) {
    val epochDay = PersianCalendarEngine.dateToEpochDay(date, calendarSystem)
    controls.update { it.copy(selectedEpochDay = epochDay) }
  }

  fun jumpToToday(preferences: UserPreferences) {
    val todayEpochDay = computeTodayEpochDay(preferences.defaultZoneId, preferences.calendarSystem)
    controls.update { it.copy(selectedEpochDay = todayEpochDay) }
  }

  fun shiftPeriod(delta: Int, preferences: UserPreferences) {
    val currentEpochDay = controls.value.selectedEpochDay
    val newEpochDay = when (controls.value.viewMode) {
      CalendarViewMode.DAY -> currentEpochDay + delta
      CalendarViewMode.WEEK -> currentEpochDay + (delta * 7L)
      CalendarViewMode.MONTH -> {
        val current = PersianCalendarEngine.epochDayToDate(currentEpochDay, preferences.calendarSystem)
        val (ny, nm) = PersianCalendarEngine.shiftMonth(current.year, current.month, delta)
        val maxD = PersianCalendarEngine.monthLength(ny, nm, preferences.calendarSystem)
        val clamped = CalendarDate(ny, nm, current.day.coerceAtMost(maxD))
        PersianCalendarEngine.dateToEpochDay(clamped, preferences.calendarSystem)
      }
    }
    controls.update { it.copy(selectedEpochDay = newEpochDay) }
  }

  fun openNewEventEditor(preferences: UserPreferences) {
    val selectedDate = PersianCalendarEngine.epochDayToDate(
      controls.value.selectedEpochDay,
      preferences.calendarSystem
    )
    val startMillis = PersianCalendarEngine.zonedPartsToUtcMillis(
      year = selectedDate.year,
      month = selectedDate.month,
      day = selectedDate.day,
      hour = 10,
      minute = 0,
      zoneId = preferences.defaultZoneId,
      calendarSystem = preferences.calendarSystem
    )
    val endMillis = startMillis + 60L * 60L * 1000L
    controls.update {
      it.copy(
        editorDraft = EventEditorDraft(
          id = UUID.randomUUID().toString(),
          isNew = true,
          startAtUtcMillis = startMillis,
          endAtUtcMillis = endMillis,
          zoneId = preferences.defaultZoneId,
          createdAtUtcMillis = System.currentTimeMillis()
        )
      )
    }
  }

  fun openEditEventEditor(event: CalendarEvent) {
    controls.update {
      it.copy(
        editorDraft = EventEditorDraft(
          id = event.id,
          isNew = false,
          title = event.title,
          description = event.description,
          location = event.location.orEmpty(),
          startAtUtcMillis = event.startAtUtcMillis,
          endAtUtcMillis = event.endAtUtcMillis,
          zoneId = event.zoneId,
          isAllDay = event.isAllDay,
          reminderMinutesBefore = event.reminderMinutesBefore,
          createdAtUtcMillis = event.createdAtUtcMillis
        )
      )
    }
  }

  fun updateDraft(transform: (EventEditorDraft) -> EventEditorDraft) {
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

    val validation = InputValidators.validateEvent(
      title = draft.title,
      startAtUtcMillis = draft.startAtUtcMillis,
      endAtUtcMillis = draft.endAtUtcMillis
    )
    if (validation != null) {
      controls.update { it.copy(editorDraft = draft.copy(validationError = validation)) }
      return
    }

    controls.update { it.copy(isSaving = true) }
    viewModelScope.launch {
      try {
        val now = System.currentTimeMillis()
        val existing = if (draft.isNew) null else calendarRepository.getEventById(draft.id)
        val event = CalendarEvent(
          id = draft.id,
          title = draft.title.trim(),
          description = draft.description.trim(),
          startAtUtcMillis = draft.startAtUtcMillis,
          endAtUtcMillis = draft.endAtUtcMillis,
          zoneId = draft.zoneId,
          location = draft.location.trim().ifEmpty { null },
          isAllDay = draft.isAllDay,
          reminderMinutesBefore = draft.reminderMinutesBefore,
          recurrenceRule = existing?.recurrenceRule,
          createdAtUtcMillis = existing?.createdAtUtcMillis ?: draft.createdAtUtcMillis.takeIf { it > 0L } ?: now,
          updatedAtUtcMillis = now,
          syncStatus = SyncStatus.PENDING_UPSERT,
          isDeleted = false
        )
        calendarRepository.upsertEvent(event)
        controls.update { it.copy(editorDraft = null, isSaving = false) }
      } catch (e: Exception) {
        controls.update {
          it.copy(isSaving = false, snackbarError = e.localizedMessage ?: "Failed to save event")
        }
      }
    }
  }

  fun requestDelete(event: CalendarEvent) {
    controls.update { it.copy(pendingDeleteEvent = event) }
  }

  fun cancelDelete() {
    controls.update { it.copy(pendingDeleteEvent = null) }
  }

  fun confirmDelete() {
    val ev = controls.value.pendingDeleteEvent ?: return
    controls.update { it.copy(pendingDeleteEvent = null) }
    viewModelScope.launch {
      calendarRepository.softDeleteEvent(ev.id, System.currentTimeMillis())
    }
  }

  fun consumeError() {
    controls.update { it.copy(snackbarError = null) }
  }

  private fun computeTodayEpochDay(zoneId: String, calendarSystem: CalendarSystem): Long {
    val parts = PersianCalendarEngine.utcMillisToZonedParts(
      System.currentTimeMillis(),
      zoneId,
      calendarSystem
    )
    return PersianCalendarEngine.dateToEpochDay(
      CalendarDate(parts.year, parts.month, parts.day),
      calendarSystem
    )
  }

  class Factory(
    private val calendarRepository: CalendarRepository,
    private val preferencesRepository: PreferencesRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return CalendarViewModel(calendarRepository, preferencesRepository) as T
    }
  }
}
