package com.example.features.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ai.AiProposedAction
import com.example.core.calendar.PersianCalendarEngine
import com.example.domain.model.MemoryEntryType
import com.example.domain.model.RecurrenceRule
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus
import com.example.domain.model.UserPreferences

@Composable
fun AssistantProposalCard(
  messageId: String,
  action: AiProposedAction,
  status: ProposalStatus,
  errorDetail: String?,
  preferences: UserPreferences,
  onConfirm: () -> Unit,
  onDiscard: () -> Unit,
  modifier: Modifier = Modifier
) {
  val (badgeTitle, icon) = when (action) {
    is AiProposedAction.CreateTaskProposal ->
      stringResource(R.string.assistant_proposal_create_task) to Icons.Outlined.TaskAlt
    is AiProposedAction.UpdateTaskProposal ->
      stringResource(R.string.assistant_proposal_update_task) to Icons.Outlined.TaskAlt
    is AiProposedAction.CreateEventProposal ->
      stringResource(R.string.assistant_proposal_create_event) to Icons.Outlined.CalendarMonth
    is AiProposedAction.UpdateEventProposal ->
      stringResource(R.string.assistant_proposal_update_event) to Icons.Outlined.CalendarMonth
    is AiProposedAction.CreateMemoryProposal ->
      stringResource(R.string.assistant_proposal_create_memory) to Icons.Outlined.Lightbulb
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("assistant_proposal_card_$messageId"),
    colors = CardDefaults.cardColors(
      containerColor = when (status) {
        ProposalStatus.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ProposalStatus.DISCARDED -> MaterialTheme.colorScheme.surfaceContainerLow
        ProposalStatus.PENDING,
        ProposalStatus.SAVING -> MaterialTheme.colorScheme.surface
      }
    ),
    border = BorderStroke(
      width = 1.dp,
      color = when (status) {
        ProposalStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
        ProposalStatus.DISCARDED -> MaterialTheme.colorScheme.outlineVariant
        ProposalStatus.PENDING,
        ProposalStatus.SAVING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
      }
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
        Text(
          text = badgeTitle,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f)
        )
      }

      when (action) {
        is AiProposedAction.CreateTaskProposal -> {
          ProposalFieldRow(stringResource(R.string.tasks_field_title), action.title)
          if (action.description.isNotBlank()) {
            ProposalFieldRow(stringResource(R.string.tasks_field_description), action.description)
          }
          action.dueAtUtcMillis?.let { due ->
            ProposalFieldRow(
              stringResource(R.string.tasks_field_due_date),
              PersianCalendarEngine.formatDateTime(
                due,
                preferences.defaultZoneId,
                preferences.calendarSystem,
                preferences.language
              )
            )
          }
          ProposalFieldRow(
            stringResource(R.string.tasks_field_priority),
            priorityLabel(action.priority)
          )
          action.category?.let {
            ProposalFieldRow(stringResource(R.string.tasks_field_category), it)
          }
          action.recurrenceRule?.let { rule ->
            ProposalFieldRow(stringResource(R.string.tasks_field_recurrence), recurrenceLabel(rule))
          }
        }

        is AiProposedAction.UpdateTaskProposal -> {
          ProposalFieldRow(
            stringResource(R.string.assistant_proposal_target),
            action.targetCurrentTitle
          )
          ProposalFieldRow(stringResource(R.string.tasks_field_title), action.newTitle)
          if (action.newDescription.isNotBlank()) {
            ProposalFieldRow(stringResource(R.string.tasks_field_description), action.newDescription)
          }
          action.newDueAtUtcMillis?.let { due ->
            ProposalFieldRow(
              stringResource(R.string.tasks_field_due_date),
              PersianCalendarEngine.formatDateTime(
                due,
                preferences.defaultZoneId,
                preferences.calendarSystem,
                preferences.language
              )
            )
          }
          ProposalFieldRow(stringResource(R.string.tasks_field_priority), priorityLabel(action.newPriority))
          ProposalFieldRow(stringResource(R.string.tasks_field_status), statusLabel(action.newStatus))
        }

        is AiProposedAction.CreateEventProposal -> {
          ProposalFieldRow(stringResource(R.string.calendar_field_title), action.title)
          val startText = if (action.isAllDay) {
            PersianCalendarEngine.formatFullDate(
              action.startAtUtcMillis,
              action.zoneId,
              preferences.calendarSystem,
              preferences.language
            ) + " · " + stringResource(R.string.calendar_all_day)
          } else {
            PersianCalendarEngine.formatDateTime(
              action.startAtUtcMillis,
              action.zoneId,
              preferences.calendarSystem,
              preferences.language
            )
          }
          ProposalFieldRow(stringResource(R.string.calendar_field_start), startText)
          if (!action.isAllDay) {
            ProposalFieldRow(
              stringResource(R.string.calendar_field_end),
              PersianCalendarEngine.formatDateTime(
                action.endAtUtcMillis,
                action.zoneId,
                preferences.calendarSystem,
                preferences.language
              )
            )
          }
          action.location?.let {
            ProposalFieldRow(stringResource(R.string.calendar_field_location), it)
          }
          if (action.description.isNotBlank()) {
            ProposalFieldRow(stringResource(R.string.calendar_field_description), action.description)
          }
        }

        is AiProposedAction.UpdateEventProposal -> {
          ProposalFieldRow(
            stringResource(R.string.assistant_proposal_target),
            action.targetCurrentTitle
          )
          ProposalFieldRow(stringResource(R.string.calendar_field_title), action.newTitle)
          ProposalFieldRow(
            stringResource(R.string.calendar_field_start),
            PersianCalendarEngine.formatDateTime(
              action.newStartAtUtcMillis,
              action.zoneId,
              preferences.calendarSystem,
              preferences.language
            )
          )
          ProposalFieldRow(
            stringResource(R.string.calendar_field_end),
            PersianCalendarEngine.formatDateTime(
              action.newEndAtUtcMillis,
              action.zoneId,
              preferences.calendarSystem,
              preferences.language
            )
          )
          action.newLocation?.let {
            ProposalFieldRow(stringResource(R.string.calendar_field_location), it)
          }
        }

        is AiProposedAction.CreateMemoryProposal -> {
          ProposalFieldRow(stringResource(R.string.memory_field_title), action.title)
          if (action.content.isNotBlank()) {
            ProposalFieldRow(stringResource(R.string.memory_field_content), action.content)
          }
          ProposalFieldRow(stringResource(R.string.memory_field_type), memoryTypeLabel(action.entryType))
          if (action.tags.isNotEmpty()) {
            ProposalFieldRow(
              stringResource(R.string.memory_field_tags),
              action.tags.joinToString(", ") { "#$it" }
            )
          }
          action.sourceUrl?.let {
            ProposalFieldRow(stringResource(R.string.memory_field_url), it)
          }
        }
      }

      if (!errorDetail.isNullOrBlank()) {
        Text(
          text = errorDetail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error
        )
      }

      Spacer(modifier = Modifier.height(2.dp))

      when (status) {
        ProposalStatus.PENDING,
        ProposalStatus.SAVING -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedButton(
              onClick = onDiscard,
              enabled = status == ProposalStatus.PENDING,
              modifier = Modifier
                .weight(1f)
                .testTag("assistant_proposal_discard_$messageId")
            ) {
              Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.padding(horizontal = 2.dp))
              Text(stringResource(R.string.assistant_action_discard))
            }
            Button(
              onClick = onConfirm,
              enabled = status == ProposalStatus.PENDING,
              modifier = Modifier
                .weight(1f)
                .testTag("assistant_proposal_confirm_$messageId")
            ) {
              Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.padding(horizontal = 2.dp))
              Text(
                stringResource(
                  if (status == ProposalStatus.SAVING) R.string.assistant_action_saving
                  else R.string.assistant_action_confirm_save
                )
              )
            }
          }
        }

        ProposalStatus.CONFIRMED -> {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("assistant_proposal_saved_badge_$messageId")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = stringResource(R.string.assistant_proposal_saved),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
          }
        }

        ProposalStatus.DISCARDED -> {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("assistant_proposal_discarded_badge_$messageId")
          ) {
            Text(
              text = stringResource(R.string.assistant_proposal_discarded),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ProposalFieldRow(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun priorityLabel(priority: TaskPriority): String = stringResource(
  when (priority) {
    TaskPriority.LOW -> R.string.priority_low
    TaskPriority.MEDIUM -> R.string.priority_medium
    TaskPriority.HIGH -> R.string.priority_high
    TaskPriority.URGENT -> R.string.priority_urgent
  }
)

@Composable
private fun statusLabel(status: TaskStatus): String = stringResource(
  when (status) {
    TaskStatus.TODO -> R.string.status_todo
    TaskStatus.IN_PROGRESS -> R.string.status_in_progress
    TaskStatus.COMPLETED -> R.string.status_completed
    TaskStatus.ARCHIVED -> R.string.status_archived
  }
)

@Composable
private fun recurrenceLabel(rule: String): String = stringResource(
  when (rule) {
    RecurrenceRule.DAILY.key -> R.string.recurrence_daily
    RecurrenceRule.WEEKLY.key -> R.string.recurrence_weekly
    RecurrenceRule.MONTHLY.key -> R.string.recurrence_monthly
    else -> R.string.recurrence_none
  }
)

@Composable
private fun memoryTypeLabel(type: MemoryEntryType): String = stringResource(
  when (type) {
    MemoryEntryType.NOTE -> R.string.memory_type_note
    MemoryEntryType.IDEA -> R.string.memory_type_idea
    MemoryEntryType.LINK -> R.string.memory_type_link
    MemoryEntryType.FACT -> R.string.memory_type_fact
    MemoryEntryType.REFERENCE -> R.string.memory_type_reference
  }
)
