package com.example.features.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.documents.DocumentSupport
import com.example.domain.model.DocumentRecord
import com.example.domain.model.ExtractionStatus
import com.example.domain.model.UserPreferences
import com.example.features.common.ConfirmDeleteDialog

@Composable
fun DocumentsScreen(
  viewModel: DocumentsViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    viewModel.importFromUri(uri)
  }

  val documentPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    viewModel.importFromUri(uri)
  }

  when (val state = uiState) {
    DocumentsUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("documents_loading"))
      }
    }

    is DocumentsUiState.Error -> {
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

    is DocumentsUiState.Success -> {
      val controls = state.controls

      val msgSaved = stringResource(R.string.documents_msg_imported_saved)
      val msgUpdated = stringResource(R.string.documents_msg_updated)
      val msgUnsupported = stringResource(R.string.documents_error_unsupported)
      val msgUnreadable = stringResource(R.string.documents_error_unreadable)
      val msgTooLarge = stringResource(R.string.documents_error_too_large)
      val msgCopyFailed = stringResource(R.string.documents_error_copy_failed)
      val msgNoViewer = stringResource(R.string.documents_error_no_viewer)
      val msgSaveFailed = stringResource(R.string.error_save_failed)
      val msgAnalyzeDone = stringResource(R.string.documents_msg_analyze_done)
      val msgAnalyzeConsent = stringResource(R.string.documents_msg_analyze_consent_required)
      val msgAnalyzeOffline = stringResource(R.string.documents_msg_analyze_offline)
      val msgAnalyzeUnsupported = stringResource(R.string.documents_msg_analyze_unsupported)
      val msgAnalyzeTooLarge = stringResource(R.string.documents_msg_analyze_too_large)
      val msgAnalyzeFailed = stringResource(R.string.documents_extraction_failed_body)

      LaunchedEffect(controls.snackbarEvent) {
        val ev = controls.snackbarEvent ?: return@LaunchedEffect
        val message = when {
          ev == "imported_saved" -> msgSaved
          ev == "updated_saved" -> msgUpdated
          ev == "import_error:unsupported" -> msgUnsupported
          ev == "import_error:unreadable" -> msgUnreadable
          ev == "import_error:too_large" -> msgTooLarge
          ev == "import_error:copy_failed" -> msgCopyFailed
          ev == "open_error:no_app" -> msgNoViewer
          ev == "analyze_completed" -> msgAnalyzeDone
          ev == "analyze_error:consent_required" -> msgAnalyzeConsent
          ev == "analyze_error:offline" -> msgAnalyzeOffline
          ev == "analyze_error:unsupported" -> msgAnalyzeUnsupported
          ev == "analyze_error:too_large" -> msgAnalyzeTooLarge
          ev.startsWith("analyze_error:") -> ev.removePrefix("analyze_error:").ifBlank { msgAnalyzeFailed }
          ev.startsWith("save_error:") -> msgSaveFailed
          else -> ev
        }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbarEvent()
      }

      Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
      ) { innerPadding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
          contentAlignment = Alignment.TopCenter
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .widthIn(max = 720.dp)
              .padding(horizontal = 16.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              FilledTonalButton(
                onClick = {
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                enabled = !controls.isImporting,
                modifier = Modifier
                  .weight(1f)
                  .testTag("documents_import_photo_button")
              ) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                  text = " ${stringResource(R.string.documents_import_photo)}",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              FilledTonalButton(
                onClick = {
                  documentPickerLauncher.launch(DocumentSupport.pickerMimeTypes)
                },
                enabled = !controls.isImporting,
                modifier = Modifier
                  .weight(1f)
                  .testTag("documents_import_file_button")
              ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                  text = " ${stringResource(R.string.documents_import_file)}",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            if (controls.isImporting) {
              Spacer(modifier = Modifier.height(6.dp))
              LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
              value = controls.searchQuery,
              onValueChange = { viewModel.onSearchQueryChanged(it) },
              placeholder = { Text(stringResource(R.string.documents_search_hint)) },
              leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
              trailingIcon = {
                if (controls.searchQuery.isNotEmpty()) {
                  IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                    Icon(
                      Icons.Outlined.Clear,
                      contentDescription = stringResource(R.string.cd_clear_search)
                    )
                  }
                }
              },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("documents_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              DocumentFilter.entries.forEach { filter ->
                FilterChip(
                  selected = controls.filter == filter,
                  onClick = { viewModel.onFilterSelected(filter) },
                  label = { Text(documentFilterLabel(filter)) },
                  modifier = Modifier.testTag("documents_filter_${filter.name.lowercase()}")
                )
              }
            }

            if (state.availableTags.isNotEmpty()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                state.availableTags.forEach { tag ->
                  FilterChip(
                    selected = controls.selectedTag == tag,
                    onClick = {
                      viewModel.onTagSelected(if (controls.selectedTag == tag) null else tag)
                    },
                    label = { Text("#$tag") }
                  )
                }
              }
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              DocumentSort.entries.forEach { sort ->
                FilterChip(
                  selected = controls.sort == sort,
                  onClick = { viewModel.onSortSelected(sort) },
                  label = { Text(documentSortLabel(sort)) }
                )
              }
              if (state.totalBytesStored > 0L) {
                Text(
                  text = DocumentSupport.formatFileSize(state.totalBytesStored),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(start = 6.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.visibleDocuments.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(24.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text = stringResource(R.string.documents_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("documents_empty_title")
                  )
                  Text(
                    text = stringResource(R.string.documents_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                  )
                }
              }
            } else {
              LazyColumn(
                modifier = Modifier
                  .fillMaxSize()
                  .testTag("documents_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                items(state.visibleDocuments, key = { it.id }) { doc ->
                  DocumentCard(
                    doc = doc,
                    preferences = state.preferences,
                    onClick = { viewModel.openDocumentDetail(doc) },
                    onEdit = { viewModel.openEditDocument(doc) },
                    onDelete = { viewModel.requestDelete(doc) }
                  )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
              }
            }
          }
        }
      }

      controls.detailState?.let { detail ->
        DocumentDetailDialog(
          detail = detail,
          preferences = state.preferences,
          previewFile = viewModel.resolveFileForPreview(detail.record.localRelativePath),
          onOpenExternal = { record ->
            val intent = viewModel.createExternalViewIntent(record)
            if (intent == null) {
              viewModel.notifyOpenViewerUnavailable()
            } else {
              runCatching { context.startActivity(intent) }
                .onFailure { viewModel.notifyOpenViewerUnavailable() }
            }
          },
          onEdit = { viewModel.openEditDocument(it) },
          onDelete = { viewModel.requestDelete(it) },
          onDismiss = { viewModel.dismissDetail() },
          isAnalyzing = controls.isAnalyzing,
          onAnalyzeWithAi = { viewModel.analyzeDocumentWithAi(it) }
        )
      }

      controls.editorDraft?.let { draft ->
        DocumentEditorDialog(
          draft = draft,
          preferences = state.preferences,
          isSaving = controls.isSaving,
          onUpdateDraft = { viewModel.updateDraft(it) },
          onSave = { viewModel.saveDraft() },
          onDismiss = { viewModel.dismissEditor() }
        )
      }

      controls.pendingDeleteDocument?.let { pending ->
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
private fun DocumentCard(
  doc: DocumentRecord,
  preferences: UserPreferences,
  onClick: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  val typeIcon = when {
    DocumentSupport.isImageMimeType(doc.mimeType) -> Icons.Outlined.Image
    DocumentSupport.isPdfMimeType(doc.mimeType) -> Icons.Outlined.PictureAsPdf
    else -> Icons.Outlined.Description
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("document_card_${doc.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = typeIcon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = doc.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          val datePart = doc.documentDateUtcMillis?.let {
            PersianCalendarEngine.formatShortDate(
              it,
              preferences.defaultZoneId,
              preferences.calendarSystem,
              preferences.language
            )
          }
          val subtitle = listOfNotNull(
            DocumentSupport.formatFileSize(doc.fileSizeBytes),
            datePart
          ).joinToString(" · ")
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(
          onClick = onEdit,
          modifier = Modifier.testTag("document_edit_${doc.id}")
        ) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = stringResource(R.string.cd_edit_item, doc.title)
          )
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier.testTag("document_delete_${doc.id}")
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete_item, doc.title),
            tint = MaterialTheme.colorScheme.error
          )
        }
      }

      if (doc.userVerifiedNotes.isNotBlank()) {
        Text(
          text = doc.userVerifiedNotes,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          color = when (doc.extractionStatus) {
            ExtractionStatus.NOT_ANALYZED -> MaterialTheme.colorScheme.surfaceContainerHigh
            ExtractionStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
            ExtractionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
          },
          shape = MaterialTheme.shapes.small
        ) {
          Text(
            text = stringResource(extractionStatusLabelRes(doc.extractionStatus)),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
      }
    }
  }
}

@Composable
private fun documentFilterLabel(filter: DocumentFilter): String = stringResource(
  when (filter) {
    DocumentFilter.ALL -> R.string.documents_filter_all
    DocumentFilter.IMAGES -> R.string.documents_filter_images
    DocumentFilter.PDF -> R.string.documents_filter_pdf
    DocumentFilter.TEXT_OTHER -> R.string.documents_filter_other
  }
)

@Composable
private fun documentSortLabel(sort: DocumentSort): String = stringResource(
  when (sort) {
    DocumentSort.UPDATED -> R.string.documents_sort_updated
    DocumentSort.DOCUMENT_DATE -> R.string.documents_sort_date
    DocumentSort.TITLE -> R.string.documents_sort_title
  }
)
