package com.example.features.documents

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.calendar.CalendarDate
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.common.ValidationError
import com.example.core.documents.DocumentSupport
import com.example.domain.model.DocumentRecord
import com.example.domain.model.ExtractionStatus
import com.example.domain.model.UserPreferences
import com.example.features.common.BilingualDatePickerDialog
import java.io.File

@Composable
fun DocumentEditorDialog(
  draft: DocumentEditorDraft,
  preferences: UserPreferences,
  isSaving: Boolean,
  onUpdateDraft: ((DocumentEditorDraft) -> DocumentEditorDraft) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit
) {
  var showDatePicker by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = {
      Text(
        text = stringResource(
          if (draft.isNew) R.string.documents_editor_new else R.string.documents_editor_edit
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
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "${draft.mimeType} · ${DocumentSupport.formatFileSize(draft.fileSizeBytes)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
          )
        }

        OutlinedTextField(
          value = draft.title,
          onValueChange = { v -> onUpdateDraft { it.copy(title = v) } },
          label = { Text(stringResource(R.string.documents_field_title)) },
          singleLine = true,
          isError = draft.validationError == ValidationError.DOCUMENT_TITLE_REQUIRED,
          supportingText = if (draft.validationError == ValidationError.DOCUMENT_TITLE_REQUIRED) {
            { Text(stringResource(R.string.documents_validation_title_required)) }
          } else null,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("document_editor_title_input")
        )

        Text(
          text = stringResource(R.string.documents_field_date),
          style = MaterialTheme.typography.labelLarge
        )
        val dateText = draft.documentDateUtcMillis?.let { utc ->
          PersianCalendarEngine.formatShortDate(
            utcMillis = utc,
            zoneId = preferences.defaultZoneId,
            calendarSystem = preferences.calendarSystem,
            language = preferences.language
          )
        } ?: stringResource(R.string.documents_field_no_date)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier
              .weight(1f)
              .testTag("document_editor_pick_date")
          ) {
            Text(dateText)
          }
          if (draft.documentDateUtcMillis != null) {
            TextButton(
              onClick = { onUpdateDraft { it.copy(documentDateUtcMillis = null) } },
              modifier = Modifier.testTag("document_editor_clear_date")
            ) {
              Text(stringResource(R.string.action_clear))
            }
          }
        }

        OutlinedTextField(
          value = draft.tagsText,
          onValueChange = { v -> onUpdateDraft { it.copy(tagsText = v) } },
          label = { Text(stringResource(R.string.documents_field_tags)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("document_editor_tags_input")
        )

        OutlinedTextField(
          value = draft.userVerifiedNotes,
          onValueChange = { v -> onUpdateDraft { it.copy(userVerifiedNotes = v) } },
          label = { Text(stringResource(R.string.documents_field_verified_notes)) },
          minLines = 3,
          maxLines = 5,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("document_editor_notes_input")
        )

        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = stringResource(R.string.documents_extraction_honesty_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(10.dp)
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onSave,
        enabled = !isSaving,
        modifier = Modifier.testTag("document_editor_save_button")
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isSaving,
        modifier = Modifier.testTag("document_editor_cancel_button")
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    }
  )

  if (showDatePicker) {
    val baseMillis = draft.documentDateUtcMillis ?: System.currentTimeMillis()
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
      onConfirm = { chosen ->
        val utcMillis = PersianCalendarEngine.zonedPartsToUtcMillis(
          year = chosen.year,
          month = chosen.month,
          day = chosen.day,
          hour = 12,
          minute = 0,
          zoneId = preferences.defaultZoneId,
          calendarSystem = preferences.calendarSystem
        )
        onUpdateDraft { it.copy(documentDateUtcMillis = utcMillis) }
        showDatePicker = false
      }
    )
  }
}

@Composable
fun DocumentDetailDialog(
  detail: DocumentDetailState,
  preferences: UserPreferences,
  previewFile: File?,
  onOpenExternal: (DocumentRecord) -> Unit,
  onEdit: (DocumentRecord) -> Unit,
  onDelete: (DocumentRecord) -> Unit,
  onDismiss: () -> Unit,
  isAnalyzing: Boolean = false,
  onAnalyzeWithAi: ((DocumentRecord) -> Unit)? = null
) {
  val doc = detail.record

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(text = doc.title)
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "${doc.mimeType} · ${DocumentSupport.formatFileSize(doc.fileSizeBytes)}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        doc.documentDateUtcMillis?.let { docDate ->
          Text(
            text = stringResource(
              R.string.documents_detail_date_label,
              PersianCalendarEngine.formatFullDate(
                utcMillis = docDate,
                zoneId = preferences.defaultZoneId,
                calendarSystem = preferences.calendarSystem,
                language = preferences.language
              )
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
          )
        }

        if (doc.tags.isNotEmpty()) {
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            doc.tags.forEach { tag ->
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
              ) {
                Text(
                  text = "#$tag",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
              }
            }
          }
        }

        if (!detail.fileExists) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = stringResource(R.string.documents_file_missing_error),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(10.dp)
            )
          }
        } else if (DocumentSupport.isImageMimeType(doc.mimeType) && previewFile != null) {
          AsyncImage(
            model = previewFile,
            contentDescription = doc.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .clip(MaterialTheme.shapes.medium)
              .testTag("document_detail_image_preview")
          )
        } else if (detail.textPreview != null) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 180.dp)
          ) {
            Text(
              text = detail.textPreview.ifBlank { stringResource(R.string.documents_empty_text_file) },
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
                .testTag("document_detail_text_preview")
            )
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
              Text(
                text = stringResource(R.string.documents_section_verified_notes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
              )
            }
            Text(
              text = doc.userVerifiedNotes.ifBlank {
                stringResource(R.string.documents_no_verified_notes)
              },
              style = MaterialTheme.typography.bodySmall
            )
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("document_detail_extraction_box")
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = stringResource(R.string.documents_section_extracted_info),
              style = MaterialTheme.typography.labelLarge
            )
            Text(
              text = stringResource(extractionStatusLabelRes(doc.extractionStatus)),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary
            )
            val bodyText = when (doc.extractionStatus) {
              ExtractionStatus.NOT_ANALYZED ->
                stringResource(R.string.documents_extraction_not_analyzed_body)
              ExtractionStatus.COMPLETED ->
                doc.extractedSummary?.takeIf { it.isNotBlank() }
                  ?: stringResource(R.string.documents_extraction_empty_result)
              ExtractionStatus.FAILED ->
                stringResource(R.string.documents_extraction_failed_body)
            }
            Text(
              text = bodyText,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (onAnalyzeWithAi != null && detail.fileExists) {
              Spacer(modifier = Modifier.height(6.dp))
              if (isAnalyzing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                  text = stringResource(R.string.documents_analyzing_progress),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary
                )
              } else {
                OutlinedButton(
                  onClick = { onAnalyzeWithAi(doc) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("document_detail_analyze_button")
                ) {
                  Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                  Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                  Text(
                    stringResource(
                      if (preferences.aiDataConsentGranted) {
                        R.string.documents_analyze_with_ai
                      } else {
                        R.string.documents_analyze_consent_required
                      }
                    )
                  )
                }
              }
            }
          }
        }

        if (detail.fileExists) {
          OutlinedButton(
            onClick = { onOpenExternal(doc) },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("document_detail_open_external")
          ) {
            Icon(Icons.Outlined.OpenInNew, contentDescription = null)
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text(stringResource(R.string.documents_open_external))
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          TextButton(
            onClick = { onDelete(doc) },
            modifier = Modifier.testTag("document_detail_delete")
          ) {
            Text(
              text = stringResource(R.string.action_delete),
              color = MaterialTheme.colorScheme.error
            )
          }
          TextButton(
            onClick = { onEdit(doc) },
            modifier = Modifier.testTag("document_detail_edit")
          ) {
            Text(stringResource(R.string.action_edit))
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss, modifier = Modifier.testTag("document_detail_close")) {
        Text(stringResource(R.string.action_close))
      }
    }
  )
}

fun extractionStatusLabelRes(status: ExtractionStatus): Int = when (status) {
  ExtractionStatus.NOT_ANALYZED -> R.string.documents_status_not_analyzed
  ExtractionStatus.COMPLETED -> R.string.documents_status_analyzed_unverified
  ExtractionStatus.FAILED -> R.string.documents_status_analysis_failed
}
