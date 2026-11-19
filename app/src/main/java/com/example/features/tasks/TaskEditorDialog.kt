package com.example.features.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.calendar.CalendarDate
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.common.ValidationError
import com.example.domain.model.RecurrenceRule
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus
import com.example.domain.model.UserPreferences
import com.example.features.common.BilingualDatePickerDialog
import com.example.features.common.ReminderPermissionHintBanner
import com.example.features.common.SimpleTimePickerDialog

@Composable
fun TaskEditorDialog(
  draft: TaskEditorDraft,
  preferences: UserPreferences,
  isSaving: Boolean,
  onUpdateDraft: ((TaskEditorDraft) -> TaskEditorDraft) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit
) {
  var showDatePicker by remember { mutableStateOf(false) }
  var showTimePicker by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = {
      Text(
        text = stringResource(
          if (draft.isNew) R.string.tasks_editor_new else R.string.tasks_editor_edit
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
          onValueChange = { value -> onUpdateDraft { it.copy(title = value) } },
          label = { Text(stringResource(R.string.tasks_field_title)) },
          singleLine = true,
          isError = draft.validationError == ValidationError.TASK_TITLE_REQUIRED,
          supportingText = if (draft.validationError == ValidationError.TASK_TITLE_REQUIRED) {
            { Text(stringResource(R.string.tasks_validation_title_required)) }
          } else null,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("task_editor_title_input")
        )

        OutlinedTextField(
          value = draft.description,
          onValueChange = { value -> onUpdateDraft { it.copy(description = value) } },
          label = { Text(stringResource(R.string.tasks_field_description)) },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("task_editor_description_input")
        )

        OutlinedTextField(
          value = draft.category,
          onValueChange = { value -> onUpdateDraft { it.copy(category = value) } },
          label = { Text(stringResource(R.string.tasks_field_category)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("task_editor_category_input")
        )

        Text(
          text = stringResource(R.string.tasks_field_priority),
          style = MaterialTheme.typography.labelLarge
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          TaskPriority.entries.forEach { priority ->
            FilterChip(
              selected = draft.priority == priority,
              onClick = { onUpdateDraft { it.copy(priority = priority) } },
              label = { Text(priorityLabel(priority)) },
              modifier = Modifier.testTag("task_priority_${priority.name.lowercase()}")
            )
          }
        }

        if (!draft.isNew) {
          Text(
            text = stringResource(R.string.tasks_field_status),
            style = MaterialTheme.typography.labelLarge
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            TaskStatus.entries.forEach { status ->
              FilterChip(
                selected = draft.status == status,
                onClick = { onUpdateDraft { it.copy(status = status) } },
                label = { Text(statusLabel(status)) }
              )
            }
          }
        }

        Text(
          text = stringResource(R.string.tasks_field_due_date),
          style = MaterialTheme.typography.labelLarge
        )
        val dueText = draft.dueAtUtcMillis?.let { utc ->
          PersianCalendarEngine.formatDateTime(
            utcMillis = utc,
            zoneId = preferences.defaultZoneId,
            calendarSystem = preferences.calendarSystem,
            language = preferences.language
          )
        } ?: stringResource(R.string.tasks_field_no_due_date)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier
              .weight(1f)
              .testTag("task_editor_pick_date")
          ) {
            Text(text = dueText, maxLines = 1)
          }
          if (draft.dueAtUtcMillis != null) {
            TextButton(
              onClick = { showTimePicker = true },
              modifier = Modifier.testTag("task_editor_pick_time")
            ) {
              Text(stringResource(R.string.picker_select_time))
            }
            TextButton(
              onClick = { onUpdateDraft { it.copy(dueAtUtcMillis = null, reminderMinutesBefore = null) } },
              modifier = Modifier.testTag("task_editor_clear_date")
            ) {
              Text(stringResource(R.string.action_clear))
            }
          }
        }

        if (draft.dueAtUtcMillis != null) {
          Text(
            text = stringResource(R.string.calendar_field_reminder),
            style = MaterialTheme.typography.labelLarge
          )
          val reminderOptions = listOf(
            null to R.string.reminder_none,
            0 to R.string.reminder_at_due_time,
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
                label = { Text(stringResource(resId)) },
                modifier = Modifier.testTag("task_reminder_${minutes ?: "none"}")
              )
            }
          }
          if (draft.reminderMinutesBefore != null) {
            ReminderPermissionHintBanner(
              notificationsPreferenceEnabled = preferences.notificationsEnabled
            )
          }
        }

        Text(
          text = stringResource(R.string.tasks_field_recurrence),
          style = MaterialTheme.typography.labelLarge
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          RecurrenceRule.entries.forEach { rule ->
            FilterChip(
              selected = draft.recurrence == rule,
              onClick = { onUpdateDraft { it.copy(recurrence = rule) } },
              label = { Text(recurrenceLabel(rule)) },
              modifier = Modifier.testTag("task_recurrence_${rule.name.lowercase()}")
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onSave,
        enabled = !isSaving,
        modifier = Modifier.testTag("task_editor_save_button")
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isSaving,
        modifier = Modifier.testTag("task_editor_cancel_button")
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    }
  )

  if (showDatePicker) {
    val baseMillis = draft.dueAtUtcMillis ?: System.currentTimeMillis()
    val parts = PersianCalendarEngine.utcMillisToZonedParts(
      baseMillis,
      preferences.defaultZoneId,
      preferences.calendarSystem
    )
    BilingualDatePickerDialog(
      initialDate = CalendarDate(parts.year, parts.month, parts.day),
      calendarSystem = preferences.calendarSystem,
      language = preferences.language,
      zoneId = preferences.defaultZoneId,
      onDismiss = { showDatePicker = false },
      onConfirm = { chosenDate ->
        val newMillis = PersianCalendarEngine.zonedPartsToUtcMillis(
          year = chosenDate.year,
          month = chosenDate.month,
          day = chosenDate.day,
          hour = if (draft.dueAtUtcMillis != null) parts.hour else 9,
          minute = if (draft.dueAtUtcMillis != null) parts.minute else 0,
          zoneId = preferences.defaultZoneId,
          calendarSystem = preferences.calendarSystem
        )
        onUpdateDraft { it.copy(dueAtUtcMillis = newMillis) }
        showDatePicker = false
      }
    )
  }

  if (showTimePicker && draft.dueAtUtcMillis != null) {
    val parts = PersianCalendarEngine.utcMillisToZonedParts(
      draft.dueAtUtcMillis,
      preferences.defaultZoneId,
      preferences.calendarSystem
    )
    SimpleTimePickerDialog(
      initialHour = parts.hour,
      initialMinute = parts.minute,
      language = preferences.language,
      onDismiss = { showTimePicker = false },
      onConfirm = { h, m ->
        val newMillis = PersianCalendarEngine.zonedPartsToUtcMillis(
          year = parts.year,
          month = parts.month,
          day = parts.day,
          hour = h,
          minute = m,
          zoneId = preferences.defaultZoneId,
          calendarSystem = preferences.calendarSystem
        )
        onUpdateDraft { it.copy(dueAtUtcMillis = newMillis) }
        showTimePicker = false
      }
    )
  }
}

@Composable
fun priorityLabel(priority: TaskPriority): String = stringResource(
  when (priority) {
    TaskPriority.LOW -> R.string.priority_low
    TaskPriority.MEDIUM -> R.string.priority_medium
    TaskPriority.HIGH -> R.string.priority_high
    TaskPriority.URGENT -> R.string.priority_urgent
  }
)

@Composable
fun statusLabel(status: TaskStatus): String = stringResource(
  when (status) {
    TaskStatus.TODO -> R.string.status_todo
    TaskStatus.IN_PROGRESS -> R.string.status_in_progress
    TaskStatus.COMPLETED -> R.string.status_completed
    TaskStatus.ARCHIVED -> R.string.status_archived
  }
)

@Composable
fun recurrenceLabel(rule: RecurrenceRule): String = stringResource(
  when (rule) {
    RecurrenceRule.NONE -> R.string.recurrence_none
    RecurrenceRule.DAILY -> R.string.recurrence_daily
    RecurrenceRule.WEEKLY -> R.string.recurrence_weekly
    RecurrenceRule.MONTHLY -> R.string.recurrence_monthly
  }
)
