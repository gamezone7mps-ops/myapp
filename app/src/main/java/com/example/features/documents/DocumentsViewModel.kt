package com.example.features.documents

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.InputValidators
import com.example.core.common.ValidationError
import com.example.core.datastore.PreferencesRepository
import com.example.core.ai.DetailedDocumentAnalysisOutcome
import com.example.core.ai.DetailedDocumentAnalyzer
import com.example.core.documents.DocumentFileStorage
import com.example.core.documents.DocumentImportError
import com.example.core.documents.DocumentImportResult
import com.example.core.documents.DocumentSupport
import com.example.domain.model.DocumentRecord
import com.example.domain.model.ExtractionStatus
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserPreferences
import com.example.domain.repository.DocumentRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DocumentFilter { ALL, IMAGES, PDF, TEXT_OTHER }
enum class DocumentSort { UPDATED, DOCUMENT_DATE, TITLE }

data class DocumentEditorDraft(
  val id: String,
  val isNew: Boolean,
  val title: String,
  val mimeType: String,
  val localRelativePath: String,
  val fileSizeBytes: Long,
  val documentDateUtcMillis: Long?,
  val tagsText: String = "",
  val userVerifiedNotes: String = "",
  val extractedSummary: String? = null,
  val extractionStatus: ExtractionStatus = ExtractionStatus.NOT_ANALYZED,
  val createdAtUtcMillis: Long = 0L,
  val validationError: ValidationError? = null
)

data class DocumentDetailState(
  val record: DocumentRecord,
  val textPreview: String?,
  val fileExists: Boolean
)

data class DocumentsControlState(
  val searchQuery: String = "",
  val filter: DocumentFilter = DocumentFilter.ALL,
  val selectedTag: String? = null,
  val sort: DocumentSort = DocumentSort.UPDATED,
  val isImporting: Boolean = false,
  val editorDraft: DocumentEditorDraft? = null,
  val detailState: DocumentDetailState? = null,
  val isSaving: Boolean = false,
  val isAnalyzing: Boolean = false,
  val pendingDeleteDocument: DocumentRecord? = null,
  val snackbarEvent: String? = null
)

sealed interface DocumentsUiState {
  data object Loading : DocumentsUiState
  data class Success(
    val preferences: UserPreferences,
    val visibleDocuments: List<DocumentRecord>,
    val availableTags: List<String>,
    val totalBytesStored: Long,
    val controls: DocumentsControlState
  ) : DocumentsUiState
  data class Error(val message: String) : DocumentsUiState
}

class DocumentsViewModel(
  private val documentRepository: DocumentRepository,
  private val documentFileStorage: DocumentFileStorage,
  preferencesRepository: PreferencesRepository,
  private val documentAnalyzer: DetailedDocumentAnalyzer? = null
) : ViewModel() {

  private val controls = MutableStateFlow(DocumentsControlState())

  val uiState: StateFlow<DocumentsUiState> = combine(
    documentRepository.observeDocuments(),
    preferencesRepository.userPreferences,
    controls
  ) { allDocs, prefs, ctrl ->
    val allTags = allDocs.flatMap { it.tags }
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .distinct()
      .sorted()

    val filtered = allDocs.filter { doc ->
      val matchesFilter = when (ctrl.filter) {
        DocumentFilter.ALL -> true
        DocumentFilter.IMAGES -> DocumentSupport.isImageMimeType(doc.mimeType)
        DocumentFilter.PDF -> DocumentSupport.isPdfMimeType(doc.mimeType)
        DocumentFilter.TEXT_OTHER ->
          !DocumentSupport.isImageMimeType(doc.mimeType) && !DocumentSupport.isPdfMimeType(doc.mimeType)
      }
      val matchesTag = ctrl.selectedTag == null ||
        doc.tags.any { it.equals(ctrl.selectedTag, ignoreCase = true) }
      val q = ctrl.searchQuery.trim()
      val matchesQuery = q.isEmpty() ||
        doc.title.contains(q, ignoreCase = true) ||
        doc.userVerifiedNotes.contains(q, ignoreCase = true) ||
        doc.mimeType.contains(q, ignoreCase = true) ||
        doc.tags.any { it.contains(q, ignoreCase = true) }
      matchesFilter && matchesTag && matchesQuery
    }

    val sorted = when (ctrl.sort) {
      DocumentSort.UPDATED -> filtered.sortedByDescending { it.updatedAtUtcMillis }
      DocumentSort.DOCUMENT_DATE -> filtered.sortedWith(
        compareBy<DocumentRecord> { it.documentDateUtcMillis == null }
          .thenByDescending { it.documentDateUtcMillis ?: Long.MIN_VALUE }
          .thenByDescending { it.updatedAtUtcMillis }
      )
      DocumentSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
    }

    DocumentsUiState.Success(
      preferences = prefs,
      visibleDocuments = sorted,
      availableTags = allTags,
      totalBytesStored = allDocs.sumOf { it.fileSizeBytes },
      controls = ctrl
    )
  }
    .catch<DocumentsUiState> { emit(DocumentsUiState.Error(it.localizedMessage ?: "Documents load failed")) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = DocumentsUiState.Loading
    )

  fun onSearchQueryChanged(query: String) {
    controls.update { it.copy(searchQuery = query) }
  }

  fun onFilterSelected(filter: DocumentFilter) {
    controls.update { it.copy(filter = filter) }
  }

  fun onTagSelected(tag: String?) {
    controls.update { it.copy(selectedTag = tag) }
  }

  fun onSortSelected(sort: DocumentSort) {
    controls.update { it.copy(sort = sort) }
  }

  fun importFromUri(uri: Uri?) {
    if (uri == null) return
    if (controls.value.isImporting) return

    controls.update { it.copy(isImporting = true) }
    viewModelScope.launch {
      when (val result = documentFileStorage.importFromUri(uri)) {
        is DocumentImportResult.Success -> {
          val now = System.currentTimeMillis()
          controls.update {
            it.copy(
              isImporting = false,
              editorDraft = DocumentEditorDraft(
                id = UUID.randomUUID().toString(),
                isNew = true,
                title = result.file.suggestedTitle,
                mimeType = result.file.mimeType,
                localRelativePath = result.file.localRelativePath,
                fileSizeBytes = result.file.fileSizeBytes,
                documentDateUtcMillis = now,
                extractionStatus = ExtractionStatus.NOT_ANALYZED,
                createdAtUtcMillis = now
              )
            )
          }
        }

        is DocumentImportResult.Failure -> {
          val key = when (result.reason) {
            DocumentImportError.UNSUPPORTED_TYPE -> "import_error:unsupported"
            DocumentImportError.EMPTY_OR_UNREADABLE -> "import_error:unreadable"
            DocumentImportError.TOO_LARGE -> "import_error:too_large"
            DocumentImportError.COPY_FAILED -> "import_error:copy_failed"
          }
          controls.update { it.copy(isImporting = false, snackbarEvent = key) }
        }
      }
    }
  }

  fun openDocumentDetail(record: DocumentRecord) {
    val fileExists = documentFileStorage.resolveLocalFile(record.localRelativePath)?.exists() == true
    controls.update {
      it.copy(
        detailState = DocumentDetailState(
          record = record,
          textPreview = null,
          fileExists = fileExists
        )
      )
    }
    if (fileExists && DocumentSupport.isTextMimeType(record.mimeType)) {
      viewModelScope.launch {
        val preview = documentFileStorage.readTextPreview(record.localRelativePath, record.mimeType)
        controls.update { state ->
          val current = state.detailState
          if (current != null && current.record.id == record.id) {
            state.copy(detailState = current.copy(textPreview = preview))
          } else {
            state
          }
        }
      }
    }
  }

  fun dismissDetail() {
    controls.update { it.copy(detailState = null) }
  }

  fun openEditDocument(record: DocumentRecord) {
    controls.update {
      it.copy(
        detailState = null,
        editorDraft = DocumentEditorDraft(
          id = record.id,
          isNew = false,
          title = record.title,
          mimeType = record.mimeType,
          localRelativePath = record.localRelativePath,
          fileSizeBytes = record.fileSizeBytes,
          documentDateUtcMillis = record.documentDateUtcMillis,
          tagsText = record.tags.joinToString(", "),
          userVerifiedNotes = record.userVerifiedNotes,
          extractedSummary = record.extractedSummary,
          extractionStatus = record.extractionStatus,
          createdAtUtcMillis = record.createdAtUtcMillis
        )
      )
    }
  }

  fun updateDraft(transform: (DocumentEditorDraft) -> DocumentEditorDraft) {
    controls.update { state ->
      val current = state.editorDraft ?: return@update state
      state.copy(editorDraft = transform(current).copy(validationError = null))
    }
  }

  fun dismissEditor() {
    val currentDraft = controls.value.editorDraft
    controls.update { it.copy(editorDraft = null, isSaving = false) }
    if (currentDraft != null && currentDraft.isNew) {
      documentFileStorage.deleteLocalFile(currentDraft.localRelativePath)
    }
  }

  fun saveDraft() {
    val state = controls.value
    val draft = state.editorDraft ?: return
    if (state.isSaving) return

    val validation = InputValidators.validateDocumentTitle(draft.title)
    if (validation != null) {
      controls.update { it.copy(editorDraft = draft.copy(validationError = validation)) }
      return
    }

    controls.update { it.copy(isSaving = true) }
    viewModelScope.launch {
      try {
        val now = System.currentTimeMillis()
        val existing = if (draft.isNew) null else documentRepository.getDocumentById(draft.id)
        val parsedTags = draft.tagsText
          .split(",", "،")
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .distinct()

        val record = DocumentRecord(
          id = draft.id,
          title = draft.title.trim(),
          mimeType = draft.mimeType,
          localRelativePath = draft.localRelativePath,
          fileSizeBytes = draft.fileSizeBytes,
          documentDateUtcMillis = draft.documentDateUtcMillis,
          tags = parsedTags,
          userVerifiedNotes = draft.userVerifiedNotes.trim(),
          extractedSummary = existing?.extractedSummary ?: draft.extractedSummary,
          extractionStatus = existing?.extractionStatus ?: draft.extractionStatus,
          createdAtUtcMillis = existing?.createdAtUtcMillis ?: draft.createdAtUtcMillis.takeIf { it > 0L } ?: now,
          updatedAtUtcMillis = now,
          syncStatus = SyncStatus.PENDING_UPSERT,
          isDeleted = false
        )
        documentRepository.upsertDocument(record)
        controls.update {
          it.copy(
            editorDraft = null,
            isSaving = false,
            snackbarEvent = if (draft.isNew) "imported_saved" else "updated_saved"
          )
        }
      } catch (e: Exception) {
        controls.update {
          it.copy(isSaving = false, snackbarEvent = "save_error:${e.localizedMessage.orEmpty()}")
        }
      }
    }
  }

  fun createExternalViewIntent(record: DocumentRecord): Intent? {
    val uri = documentFileStorage.createContentUri(record.localRelativePath) ?: return null
    return Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, record.mimeType)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  fun resolveFileForPreview(localRelativePath: String) =
    documentFileStorage.resolveLocalFile(localRelativePath)

  fun notifyOpenViewerUnavailable() {
    controls.update { it.copy(snackbarEvent = "open_error:no_app") }
  }

  fun requestDelete(record: DocumentRecord) {
    controls.update { it.copy(detailState = null, pendingDeleteDocument = record) }
  }

  fun cancelDelete() {
    controls.update { it.copy(pendingDeleteDocument = null) }
  }

  fun confirmDelete() {
    val pending = controls.value.pendingDeleteDocument ?: return
    controls.update { it.copy(pendingDeleteDocument = null) }
    viewModelScope.launch {
      documentRepository.softDeleteDocument(pending.id, System.currentTimeMillis())
    }
  }

  fun analyzeDocumentWithAi(record: DocumentRecord) {
    val analyzer = documentAnalyzer ?: return
    if (controls.value.isAnalyzing) return
    controls.update { it.copy(isAnalyzing = true) }

    viewModelScope.launch {
      try {
        when (val outcome = analyzer.analyzeDetailed(record)) {
          is DetailedDocumentAnalysisOutcome.Completed -> {
            val updated = record.copy(
              extractedSummary = outcome.summary,
              extractionStatus = ExtractionStatus.COMPLETED,
              updatedAtUtcMillis = System.currentTimeMillis(),
              syncStatus = SyncStatus.PENDING_UPSERT
            )
            documentRepository.upsertDocument(updated)
            controls.update { state ->
              val refreshedDetail = state.detailState?.takeIf { it.record.id == record.id }
                ?.copy(record = updated)
              state.copy(
                isAnalyzing = false,
                detailState = refreshedDetail,
                snackbarEvent = "analyze_completed"
              )
            }
          }

          DetailedDocumentAnalysisOutcome.ConsentRequired -> {
            controls.update {
              it.copy(isAnalyzing = false, snackbarEvent = "analyze_error:consent_required")
            }
          }

          DetailedDocumentAnalysisOutcome.Offline -> {
            controls.update {
              it.copy(isAnalyzing = false, snackbarEvent = "analyze_error:offline")
            }
          }

          DetailedDocumentAnalysisOutcome.TooLargeForAi -> {
            val failed = record.copy(
              extractionStatus = ExtractionStatus.FAILED,
              updatedAtUtcMillis = System.currentTimeMillis(),
              syncStatus = SyncStatus.PENDING_UPSERT
            )
            documentRepository.upsertDocument(failed)
            controls.update { state ->
              state.copy(
                isAnalyzing = false,
                detailState = state.detailState?.takeIf { it.record.id == record.id }?.copy(record = failed),
                snackbarEvent = "analyze_error:too_large"
              )
            }
          }

          DetailedDocumentAnalysisOutcome.UnsupportedForAnalysis -> {
            val failed = record.copy(
              extractionStatus = ExtractionStatus.FAILED,
              updatedAtUtcMillis = System.currentTimeMillis(),
              syncStatus = SyncStatus.PENDING_UPSERT
            )
            documentRepository.upsertDocument(failed)
            controls.update { state ->
              state.copy(
                isAnalyzing = false,
                detailState = state.detailState?.takeIf { it.record.id == record.id }?.copy(record = failed),
                snackbarEvent = "analyze_error:unsupported"
              )
            }
          }

          is DetailedDocumentAnalysisOutcome.Failed -> {
            val failed = record.copy(
              extractionStatus = ExtractionStatus.FAILED,
              updatedAtUtcMillis = System.currentTimeMillis(),
              syncStatus = SyncStatus.PENDING_UPSERT
            )
            documentRepository.upsertDocument(failed)
            controls.update { state ->
              state.copy(
                isAnalyzing = false,
                detailState = state.detailState?.takeIf { it.record.id == record.id }?.copy(record = failed),
                snackbarEvent = "analyze_error:${outcome.message}"
              )
            }
          }
        }
      } catch (e: Exception) {
        controls.update {
          it.copy(isAnalyzing = false, snackbarEvent = "analyze_error:${e.localizedMessage.orEmpty()}")
        }
      }
    }
  }

  fun consumeSnackbarEvent() {
    controls.update { it.copy(snackbarEvent = null) }
  }

  class Factory(
    private val documentRepository: DocumentRepository,
    private val documentFileStorage: DocumentFileStorage,
    private val preferencesRepository: PreferencesRepository,
    private val documentAnalyzer: DetailedDocumentAnalyzer? = null
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DocumentsViewModel(
        documentRepository = documentRepository,
        documentFileStorage = documentFileStorage,
        preferencesRepository = preferencesRepository,
        documentAnalyzer = documentAnalyzer
      ) as T
    }
  }
}
