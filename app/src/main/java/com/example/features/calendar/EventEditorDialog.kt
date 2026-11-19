package com.example.features.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.calendar.CalendarDate
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.common.ValidationError
import com.example.domain.model.UserPreferences
import com.example.features.common.BilingualDatePickerDialog
import com.example.features.common.ReminderPermissionHintBanner
import com.example.features.common.SimpleTimePickerDialog

private enum class PickerTarget { START_DATE, START_TIME, END_DATE, END_TIME }

@Composable
fun EventEditorDialog(
  draft: EventEditorDraft,
  preferences: UserPreferences,
  isSaving: Boolean,
  onUpdateDraft: ((EventEditorDraft) -> EventEditorDraft) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit
) {
  var activePicker by remember { mutableStateOf<PickerTarget?>(null) }

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = {
      Text(
        text = stringResource(
          if (draft.isNew) R.string.calendar_editor_new else R.string.calendar_editor_edit
        )
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = draft.title,
          onValueChange = { v -> onUpdateDraft { it.copy(title = v) } },
          label = { Text(stringResource(R.string.calendar_field_title)) },
          singleLine = true,
          isError = draft.validationError == ValidationError.EVENT_TITLE_REQUIRED,
          supportingText = if (draft.validationError == ValidationError.EVENT_TITLE_REQUIRED) {
            { Text(stringResource(R.string.calendar_validation_title_required)) }
          } else null,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("event_editor_title_input")
        )

        OutlinedTextField(
          value = draft.location,
          onValueChange = { v -> onUpdateDraft { it.copy(location = v) } },
          label = { Text(stringResource(R.string.calendar_field_location)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("event_editor_location_input")
        )

        OutlinedTextField(
          value = draft.description,
          onValueChange = { v -> onUpdateDraft { it.copy(description = v) } },
          label = { Text(stringResource(R.string.calendar_field_description)) },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("event_editor_description_input")
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(R.string.calendar_field_all_day),
            style = MaterialTheme.typography.titleSmall
          )
          Switch(
            checked = draft.isAllDay,
            onCheckedChange = { allDay ->
              onUpdateDraft { current ->
                if (allDay) {
                  val sParts = PersianCalendarEngine.utcMillisToZonedParts(
                    current.startAtUtcMillis,
                    current.zoneId,
                    preferences.calendarSystem
                  )
                  val eParts = PersianCalendarEngine.utcMillisToZonedParts(
                    current.endAtUtcMillis,
                    current.zoneId,
                    preferences.calendarSystem
                  )
                  val newStart = PersianCalendarEngine.startOfDayUtcMillis(
                    CalendarDate(sParts.year, sParts.month, sParts.day),
                    current.zoneId,
                    preferences.calendarSystem
                  )
                  val newEnd = PersianCalendarEngine.endOfDayUtcMillis(
                    CalendarDate(eParts.year, eParts.month, eParts.day),
                    current.zoneId,
                    preferences.calendarSystem
                  ).coerceAtLeast(newStart)
                  current.copy(isAllDay = true, startAtUtcMillis = newStart, endAtUtcMillis = newEnd)
                } else {
                  current.copy(isAllDay = false)
                }
              }
            },
            modifier = Modifier.testTag("event_editor_all_day_switch")
          )
        }

        Text(
          text = stringResource(R.string.calendar_field_start),
          style = MaterialTheme.typography.labelLarge
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = { activePicker = PickerTarget.START_DATE },
            modifier = Modifier
              .weight(1f)
              .testTag("event_editor_start_date")
          ) {
            Text(
              PersianCalendarEngine.formatShortDate(
                draft.startAtUtcMillis,
                draft.zoneId,
                preferences.calendarSystem,
                preferences.language
              )
            )
          }
          if (!draft.isAllDay) {
            OutlinedButton(
              onClick = { activePicker = PickerTarget.START_TIME },
              modifier = Modifier.testTag("event_editor_start_time")
            ) {
              Text(
                PersianCalendarEngine.formatTime(
                  draft.startAtUtcMillis,
                  draft.zoneId,
                  preferences.language
                )
              )
            }
          }
        }

        Text(
          text = stringResource(R.string.calendar_field_end),
          style = MaterialTheme.typography.labelLarge
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = { activePicker = PickerTarget.END_DATE },
            modifier = Modifier
              .weight(1f)
              .testTag("event_editor_end_date")
          ) {
            Text(
              PersianCalendarEngine.formatShortDate(
                draft.endAtUtcMillis,
                draft.zoneId,
                preferences.calendarSystem,
                preferences.language
              )
            )
          }
          if (!draft.isAllDay) {
            OutlinedButton(
              onClick = { activePicker = PickerTarget.END_TIME },
              modifier = Modifier.testTag("event_editor_end_time")
            ) {
              Text(
                PersianCalendarEngine.formatTime(
                  draft.endAtUtcMillis,
                  draft.zoneId,
                  preferences.language
                )
              )
            }
          }
        }

        if (draft.validationError == ValidationError.EVENT_END_BEFORE_START) {
          Text(
            text = stringResource(R.string.calendar_validation_end_before_start),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
          )
        }

        Text(
          text = stringResource(R.string.calendar_field_timezone, draft.zoneId),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
          text = stringResource(R.string.calendar_field_reminder),
          style = MaterialTheme.typography.labelLarge
        )
        val reminderOptions = listOf(
          null to R.string.reminder_none,
          0 to R.string.reminder_at_time,
          10 to R.string.reminder_10_min,
          30 to R.string.reminder_30_min,
          60 to R.string.reminder_1_hour,
          1440 to R.string.reminder_1_day
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          reminderOptions.forEach { (minutes, resId) ->
            FilterChip(
              selected = draft.reminderMinutesBefore == minutes,
              onClick = { onUpdateDraft { it.copy(reminderMinutesBefore = minutes) } },
              label = { Text(stringResource(resId)) }
            )
          }
        }
        if (draft.reminderMinutesBefore != null) {
          ReminderPermissionHintBanner(
            notificationsPreferenceEnabled = preferences.notificationsEnabled
          )
        }
        Text(
          text = stringResource(R.string.calendar_reminder_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onSave,
        enabled = !isSaving,
        modifier = Modifier.testTag("event_editor_save_button")
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isSaving,
        modifier = Modifier.testTag("event_editor_cancel_button")
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    }
  )

  when (activePicker) {
    PickerTarget.START_DATE, PickerTarget.END_DATE -> {
      val isStart = activePicker == PickerTarget.START_DATE
      val sourceMillis = if (isStart) draft.startAtUtcMillis else draft.endAtUtcMillis
      val parts = PersianCalendarEngine.utcMillisToZonedParts(
        sourceMillis,
        draft.zoneId,
        preferences.calendarSystem
      )
      BilingualDatePickerDialog(
        initialDate = CalendarDate(parts.year, parts.month, parts.day),
        calendarSystem = preferences.calendarSystem,
        language = preferences.language,
        zoneId = draft.zoneId,
        onDismiss = { activePicker = null },
        onConfirm = { chosen ->
          val newMillis = PersianCalendarEngine.zonedPartsToUtcMillis(
            year = chosen.year,
            month = chosen.month,
            day = chosen.day,
            hour = parts.hour,
            minute = parts.minute,
            zoneId = draft.zoneId,
            calendarSystem = preferences.calendarSystem
          )
          onUpdateDraft { cur ->
            if (isStart) {
              val duration = (cur.endAtUtcMillis - cur.startAtUtcMillis).coerceAtLeast(3_600_000L)
              cur.copy(startAtUtcMillis = newMillis, endAtUtcMillis = newMillis + duration)
            } else {
              cur.copy(endAtUtcMillis = newMillis)
            }
          }
          activePicker = null
        }
      )
    }

    PickerTarget.START_TIME, PickerTarget.END_TIME -> {
      val isStart = activePicker == PickerTarget.START_TIME
      val sourceMillis = if (isStart) draft.startAtUtcMillis else draft.endAtUtcMillis
      val parts = PersianCalendarEngine.utcMillisToZonedParts(
        sourceMillis,
        draft.zoneId,
        preferences.calendarSystem
      )
      SimpleTimePickerDialog(
        initialHour = parts.hour,
        initialMinute = parts.minute,
        language = preferences.language,
        onDismiss = { activePicker = null },
        onConfirm = { h, m ->
          val newMillis = PersianCalendarEngine.zonedPartsToUtcMillis(
            year = parts.year,
            month = parts.month,
            day = parts.day,
            hour = h,
            minute = m,
            zoneId = draft.zoneId,
            calendarSystem = preferences.calendarSystem
          )
          onUpdateDraft { cur ->
            if (isStart) {
              val duration = (cur.endAtUtcMillis - cur.startAtUtcMillis).coerceAtLeast(3_600_000L)
              cur.copy(startAtUtcMillis = newMillis, endAtUtcMillis = newMillis + duration)
            } else {
              cur.copy(endAtUtcMillis = newMillis)
            }
          }
          activePicker = null
        }
      )
    }

    null -> Unit
  }
}
