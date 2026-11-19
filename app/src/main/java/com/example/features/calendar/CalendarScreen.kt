package com.example.features.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.calendar.CalendarDate
import com.example.core.calendar.PersianCalendarEngine
import com.example.domain.model.CalendarEvent
import com.example.domain.model.UserPreferences
import com.example.features.common.ConfirmDeleteDialog

@Composable
fun CalendarScreen(
  viewModel: CalendarViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  when (val state = uiState) {
    CalendarUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("calendar_loading"))
      }
    }

    is CalendarUiState.Error -> {
      Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = state.message,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyLarge
        )
      }
    }

    is CalendarUiState.Success -> {
      val prefs = state.preferences
      val controls = state.controls

      LaunchedEffect(controls.snackbarError) {
        val err = controls.snackbarError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.consumeError()
      }

      Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
          FloatingActionButton(
            onClick = { viewModel.openNewEventEditor(prefs) },
            modifier = Modifier.testTag("calendar_add_fab")
          ) {
            Icon(
              imageVector = Icons.Filled.Add,
              contentDescription = stringResource(R.string.cd_add_event)
            )
          }
        }
      ) { innerPadding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
          contentAlignment = Alignment.TopCenter
        ) {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .widthIn(max = 720.dp)
              .padding(horizontal = 16.dp)
              .testTag("calendar_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                CalendarViewMode.entries.forEach { mode ->
                  FilterChip(
                    selected = controls.viewMode == mode,
                    onClick = { viewModel.setViewMode(mode) },
                    label = { Text(viewModeLabel(mode)) },
                    modifier = Modifier.testTag("calendar_mode_${mode.name.lowercase()}")
                  )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                  onClick = { viewModel.jumpToToday(prefs) },
                  modifier = Modifier.testTag("calendar_today_button")
                ) {
                  Text(stringResource(R.string.action_today))
                }
              }
            }

            item {
              CalendarHeaderBar(
                selectedDate = state.selectedDate,
                viewMode = controls.viewMode,
                preferences = prefs,
                onPrevious = { viewModel.shiftPeriod(-1, prefs) },
                onNext = { viewModel.shiftPeriod(1, prefs) }
              )
            }

            item {
              when (controls.viewMode) {
                CalendarViewMode.MONTH -> MonthGridCard(
                  selectedDate = state.selectedDate,
                  todayDate = state.todayDate,
                  eventEpochDays = state.eventEpochDays,
                  preferences = prefs,
                  onSelectDate = { viewModel.selectDate(it, prefs.calendarSystem) }
                )

                CalendarViewMode.WEEK -> WeekStripCard(
                  selectedDate = state.selectedDate,
                  todayDate = state.todayDate,
                  eventEpochDays = state.eventEpochDays,
                  preferences = prefs,
                  onSelectDate = { viewModel.selectDate(it, prefs.calendarSystem) }
                )

                CalendarViewMode.DAY -> DayBannerCard(
                  selectedDate = state.selectedDate,
                  preferences = prefs
                )
              }
            }

            if (state.selectedDayEvents.isEmpty()) {
              item {
                Surface(
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calendar_empty_day"),
                  shape = MaterialTheme.shapes.medium,
                  color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                  Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.CalendarToday,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                      text = stringResource(R.string.calendar_empty_day_title),
                      style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                      text = stringResource(R.string.calendar_empty_day_body),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      textAlign = TextAlign.Center
                    )
                  }
                }
              }
            } else {
              items(state.selectedDayEvents, key = { it.id }) { event ->
                EventCard(
                  event = event,
                  preferences = prefs,
                  onEdit = { viewModel.openEditEventEditor(event) },
                  onDelete = { viewModel.requestDelete(event) }
                )
              }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
          }
        }
      }

      controls.editorDraft?.let { draft ->
        EventEditorDialog(
          draft = draft,
          preferences = prefs,
          isSaving = controls.isSaving,
          onUpdateDraft = { viewModel.updateDraft(it) },
          onSave = { viewModel.saveDraft() },
          onDismiss = { viewModel.dismissEditor() }
        )
      }

      controls.pendingDeleteEvent?.let { pending ->
        ConfirmDeleteDialog(
          itemTitle = pending.title,
          onConfirm = { viewModel.confirmDelete() },
          onDismiss = { viewModel.cancelDelete() }
        )
      }
    }
  }
}

@Composable
private fun CalendarHeaderBar(
  selectedDate: CalendarDate,
  viewMode: CalendarViewMode,
  preferences: UserPreferences,
  onPrevious: () -> Unit,
  onNext: () -> Unit
) {
  val title = when (viewMode) {
    CalendarViewMode.MONTH, CalendarViewMode.WEEK ->
      PersianCalendarEngine.formatMonthYear(
        selectedDate.year,
        selectedDate.month,
        preferences.calendarSystem,
        preferences.language
      )
    CalendarViewMode.DAY -> {
      val utc = PersianCalendarEngine.zonedPartsToUtcMillis(
        selectedDate.year,
        selectedDate.month,
        selectedDate.day,
        12,
        0,
        preferences.defaultZoneId,
        preferences.calendarSystem
      )
      PersianCalendarEngine.formatFullDate(
        utc,
        preferences.defaultZoneId,
        preferences.calendarSystem,
        preferences.language
      )
    }
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(onClick = onPrevious, modifier = Modifier.testTag("calendar_prev_button")) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        contentDescription = stringResource(R.string.cd_previous_period)
      )
    }
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.testTag("calendar_header_title")
    )
    IconButton(onClick = onNext, modifier = Modifier.testTag("calendar_next_button")) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = stringResource(R.string.cd_next_period)
      )
    }
  }
}

@Composable
private fun MonthGridCard(
  selectedDate: CalendarDate,
  todayDate: CalendarDate,
  eventEpochDays: Set<Long>,
  preferences: UserPreferences,
  onSelectDate: (CalendarDate) -> Unit
) {
  val headers = PersianCalendarEngine.weekdayShortHeaders(preferences.calendarSystem, preferences.language)
  val firstOffset = PersianCalendarEngine.dayOfWeekGridIndex(
    CalendarDate(selectedDate.year, selectedDate.month, 1),
    preferences.defaultZoneId,
    preferences.calendarSystem
  )
  val daysInMonth = PersianCalendarEngine.monthLength(
    selectedDate.year,
    selectedDate.month,
    preferences.calendarSystem
  )

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("calendar_month_grid"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(modifier = Modifier.fillMaxWidth()) {
        headers.forEach { header ->
          Text(
            text = header,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      val totalCells = firstOffset + daysInMonth
      val rows = (totalCells + 6) / 7
      for (row in 0 until rows) {
        Row(modifier = Modifier.fillMaxWidth()) {
          for (col in 0..6) {
            val cellIndex = row * 7 + col
            val dayNum = cellIndex - firstOffset + 1
            if (dayNum in 1..daysInMonth) {
              val cellDate = CalendarDate(selectedDate.year, selectedDate.month, dayNum)
              val epochDay = PersianCalendarEngine.dateToEpochDay(cellDate, preferences.calendarSystem)
              DayCell(
                dayNum = dayNum,
                isSelected = cellDate == selectedDate,
                isToday = cellDate == todayDate,
                hasEvents = epochDay in eventEpochDays,
                preferences = preferences,
                onClick = { onSelectDate(cellDate) },
                modifier = Modifier.weight(1f)
              )
            } else {
              Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WeekStripCard(
  selectedDate: CalendarDate,
  todayDate: CalendarDate,
  eventEpochDays: Set<Long>,
  preferences: UserPreferences,
  onSelectDate: (CalendarDate) -> Unit
) {
  val headers = PersianCalendarEngine.weekdayShortHeaders(preferences.calendarSystem, preferences.language)
  val weekDates = PersianCalendarEngine.weekDatesAround(
    selectedDate,
    preferences.defaultZoneId,
    preferences.calendarSystem
  )

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("calendar_week_strip"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(modifier = Modifier.fillMaxWidth()) {
        headers.forEach { h ->
          Text(
            text = h,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Row(modifier = Modifier.fillMaxWidth()) {
        weekDates.forEach { date ->
          val epochDay = PersianCalendarEngine.dateToEpochDay(date, preferences.calendarSystem)
          DayCell(
            dayNum = date.day,
            isSelected = date == selectedDate,
            isToday = date == todayDate,
            hasEvents = epochDay in eventEpochDays,
            preferences = preferences,
            onClick = { onSelectDate(date) },
            modifier = Modifier.weight(1f)
          )
        }
      }
    }
  }
}

@Composable
private fun DayBannerCard(
  selectedDate: CalendarDate,
  preferences: UserPreferences
) {
  val utc = PersianCalendarEngine.zonedPartsToUtcMillis(
    selectedDate.year,
    selectedDate.month,
    selectedDate.day,
    12,
    0,
    preferences.defaultZoneId,
    preferences.calendarSystem
  )
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("calendar_day_banner"),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.primaryContainer
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = PersianCalendarEngine.formatFullDate(
          utc,
          preferences.defaultZoneId,
          preferences.calendarSystem,
          preferences.language
        ),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
      Text(
        text = stringResource(R.string.calendar_field_timezone, preferences.defaultZoneId),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
      )
    }
  }
}

@Composable
private fun DayCell(
  dayNum: Int,
  isSelected: Boolean,
  isToday: Boolean,
  hasEvents: Boolean,
  preferences: UserPreferences,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .aspectRatio(1f)
      .padding(2.dp)
      .clip(CircleShape)
      .background(
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface
      )
      .then(
        if (isToday && !isSelected) {
          Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
        } else Modifier
      )
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = PersianCalendarEngine.formatNumber(dayNum, preferences.language),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
      )
      if (hasEvents) {
        Box(
          modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(
              if (isSelected) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.tertiary
            )
        )
      }
    }
  }
}

@Composable
private fun EventCard(
  event: CalendarEvent,
  preferences: UserPreferences,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onEdit)
      .testTag("event_card_${event.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = event.title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )

        val timeText = if (event.isAllDay) {
          stringResource(R.string.calendar_all_day)
        } else {
          val start = PersianCalendarEngine.formatTime(event.startAtUtcMillis, event.zoneId, preferences.language)
          val end = PersianCalendarEngine.formatTime(event.endAtUtcMillis, event.zoneId, preferences.language)
          "$start – $end (${event.zoneId})"
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = timeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        if (!event.location.isNullOrBlank()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.Place,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = event.location,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        if (event.description.isNotBlank()) {
          Text(
            text = event.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.testTag("event_delete_${event.id}")
      ) {
        Icon(
          imageVector = Icons.Outlined.DeleteOutline,
          contentDescription = stringResource(R.string.cd_delete_item, event.title),
          tint = MaterialTheme.colorScheme.error
        )
      }
    }
  }
}

@Composable
private fun viewModeLabel(mode: CalendarViewMode): String = stringResource(
  when (mode) {
    CalendarViewMode.MONTH -> R.string.calendar_view_month
    CalendarViewMode.WEEK -> R.string.calendar_view_week
    CalendarViewMode.DAY -> R.string.calendar_view_day
  }
)
