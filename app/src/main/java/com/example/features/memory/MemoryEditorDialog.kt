package com.example.features.memory

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.ValidationError
import com.example.domain.model.MemoryEntryType

@Composable
fun MemoryEditorDialog(
  draft: MemoryEditorDraft,
  isSaving: Boolean,
  onUpdateDraft: ((MemoryEditorDraft) -> MemoryEditorDraft) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = {
      Text(
        text = stringResource(
          if (draft.isNew) R.string.memory_editor_new else R.string.memory_editor_edit
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
        Text(
          text = stringResource(R.string.memory_field_type),
          style = MaterialTheme.typography.labelLarge
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          MemoryEntryType.entries.forEach { type ->
            FilterChip(
              selected = draft.entryType == type,
              onClick = { onUpdateDraft { it.copy(entryType = type) } },
              label = { Text(memoryTypeLabel(type)) },
              modifier = Modifier.testTag("memory_type_chip_${type.name.lowercase()}")
            )
          }
        }

        OutlinedTextField(
          value = draft.title,
          onValueChange = { v -> onUpdateDraft { it.copy(title = v) } },
          label = { Text(stringResource(R.string.memory_field_title)) },
          singleLine = true,
          isError = draft.validationError == ValidationError.MEMORY_EMPTY,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_editor_title_input")
        )

        OutlinedTextField(
          value = draft.content,
          onValueChange = { v -> onUpdateDraft { it.copy(content = v) } },
          label = { Text(stringResource(R.string.memory_field_content)) },
          minLines = 3,
          maxLines = 6,
          isError = draft.validationError == ValidationError.MEMORY_EMPTY,
          supportingText = if (draft.validationError == ValidationError.MEMORY_EMPTY) {
            { Text(stringResource(R.string.memory_validation_title_or_content)) }
          } else null,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_editor_content_input")
        )

        OutlinedTextField(
          value = draft.tagsText,
          onValueChange = { v -> onUpdateDraft { it.copy(tagsText = v) } },
          label = { Text(stringResource(R.string.memory_field_tags)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_editor_tags_input")
        )

        OutlinedTextField(
          value = draft.sourceUrl,
          onValueChange = { v -> onUpdateDraft { it.copy(sourceUrl = v) } },
          label = { Text(stringResource(R.string.memory_field_url)) },
          singleLine = true,
          isError = draft.validationError == ValidationError.MEMORY_INVALID_URL,
          supportingText = if (draft.validationError == ValidationError.MEMORY_INVALID_URL) {
            { Text(stringResource(R.string.memory_validation_invalid_url)) }
          } else null,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_editor_url_input")
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(R.string.memory_field_pinned),
            style = MaterialTheme.typography.titleSmall
          )
          Switch(
            checked = draft.isPinned,
            onCheckedChange = { pinned -> onUpdateDraft { it.copy(isPinned = pinned) } },
            modifier = Modifier.testTag("memory_editor_pin_switch")
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onSave,
        enabled = !isSaving,
        modifier = Modifier.testTag("memory_editor_save_button")
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isSaving,
        modifier = Modifier.testTag("memory_editor_cancel_button")
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    }
  )
}

@Composable
fun memoryTypeLabel(type: MemoryEntryType): String = stringResource(
  when (type) {
    MemoryEntryType.NOTE -> R.string.memory_type_note
    MemoryEntryType.IDEA -> R.string.memory_type_idea
    MemoryEntryType.LINK -> R.string.memory_type_link
    MemoryEntryType.FACT -> R.string.memory_type_fact
    MemoryEntryType.REFERENCE -> R.string.memory_type_reference
  }
)
