package com.example.core.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.core.datastore.PreferencesRepository
import com.example.core.documents.DocumentAnalysisResult
import com.example.core.documents.DocumentAnalyzer
import com.example.core.documents.DocumentFileStorage
import com.example.core.documents.DocumentSupport
import com.example.domain.model.AppLanguage
import com.example.domain.model.DocumentRecord
import com.example.domain.model.ExtractionStatus
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiDocAnalyzer"
private const val DOC_MODEL_NAME = "gemini-3.8-flash"
private const val DOC_TIMEOUT_MS = 60_000L
private const val MAX_INLINE_BYTES = 18L * 1024L * 1024L

sealed interface DetailedDocumentAnalysisOutcome {
  data class Completed(val summary: String) : DetailedDocumentAnalysisOutcome
  data object ConsentRequired : DetailedDocumentAnalysisOutcome
  data object Offline : DetailedDocumentAnalysisOutcome
  data object UnsupportedForAnalysis : DetailedDocumentAnalysisOutcome
  data object TooLargeForAi : DetailedDocumentAnalysisOutcome
  data class Failed(val message: String) : DetailedDocumentAnalysisOutcome
}

interface DetailedDocumentAnalyzer : DocumentAnalyzer {
  suspend fun analyzeDetailed(record: DocumentRecord): DetailedDocumentAnalysisOutcome
}

class GeminiDocumentAnalyzer(
  private val documentFileStorage: DocumentFileStorage,
  private val preferencesRepository: PreferencesRepository,
  private val networkStatusProvider: NetworkStatusProvider,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DetailedDocumentAnalyzer {

  private val model by lazy {
    Firebase.ai.generativeModel(modelName = DOC_MODEL_NAME)
  }

  override suspend fun analyze(record: DocumentRecord): DocumentAnalysisResult {
    return when (val outcome = analyzeDetailed(record)) {
      is DetailedDocumentAnalysisOutcome.Completed ->
        DocumentAnalysisResult(extractedSummary = outcome.summary, status = ExtractionStatus.COMPLETED)
      DetailedDocumentAnalysisOutcome.ConsentRequired,
      DetailedDocumentAnalysisOutcome.Offline ->
        DocumentAnalysisResult(extractedSummary = record.extractedSummary, status = record.extractionStatus)
      DetailedDocumentAnalysisOutcome.UnsupportedForAnalysis,
      DetailedDocumentAnalysisOutcome.TooLargeForAi,
      is DetailedDocumentAnalysisOutcome.Failed ->
        DocumentAnalysisResult(extractedSummary = null, status = ExtractionStatus.FAILED)
    }
  }

  override suspend fun analyzeDetailed(record: DocumentRecord): DetailedDocumentAnalysisOutcome {
    val prefs = preferencesRepository.userPreferences.first()
    if (!prefs.aiDataConsentGranted) {
      return DetailedDocumentAnalysisOutcome.ConsentRequired
    }
    if (!networkStatusProvider.isOnline()) {
      return DetailedDocumentAnalysisOutcome.Offline
    }

    val file = documentFileStorage.resolveLocalFile(record.localRelativePath)
      ?: return DetailedDocumentAnalysisOutcome.Failed("Local file is missing.")
    if (!file.exists() || !file.canRead()) {
      return DetailedDocumentAnalysisOutcome.Failed("Local file cannot be read.")
    }
    if (file.length() > MAX_INLINE_BYTES) {
      return DetailedDocumentAnalysisOutcome.TooLargeForAi
    }

    val promptHeader = if (prefs.language == AppLanguage.PERSIAN) {
      "این سند («${record.title}») را به فارسی خلاصه کن و فقط نکات مهم، تاریخ‌ها و مبالغ قابل مشاهده را به صورت بولت‌پوینت بنویس. اگر چیزی خوانا نیست، حدس نزن و صادقانه بگو."
    } else {
      "Summarize this document (\"${record.title}\") concisely with bullet points for key facts, dates, and numbers visible in the content. Do not invent anything unreadable."
    }

    return try {
      when {
        DocumentSupport.isImageMimeType(record.mimeType) -> {
          val bitmap = withContext(ioDispatcher) { decodeScaledBitmap(file) }
            ?: return DetailedDocumentAnalysisOutcome.Failed("Unable to decode image.")
          try {
            val response = withTimeout(DOC_TIMEOUT_MS) {
              model.generateContent(
                content {
                  text(promptHeader)
                  image(bitmap)
                }
              )
            }
            val summary = response.text?.trim().orEmpty()
            if (summary.isEmpty()) {
              DetailedDocumentAnalysisOutcome.Failed("Model returned an empty summary.")
            } else {
              DetailedDocumentAnalysisOutcome.Completed(summary)
            }
          } finally {
            bitmap.recycle()
          }
        }

        DocumentSupport.isTextMimeType(record.mimeType) -> {
          val preview = documentFileStorage.readTextPreview(
            record.localRelativePath,
            record.mimeType,
            maxChars = 12_000
          ).orEmpty()
          if (preview.isBlank()) {
            return DetailedDocumentAnalysisOutcome.Failed("Document text is empty.")
          }
          val response = withTimeout(DOC_TIMEOUT_MS) {
            model.generateContent(
              content {
                text("$promptHeader\n\n---\n$preview")
              }
            )
          }
          val summary = response.text?.trim().orEmpty()
          if (summary.isEmpty()) {
            DetailedDocumentAnalysisOutcome.Failed("Model returned an empty summary.")
          } else {
            DetailedDocumentAnalysisOutcome.Completed(summary)
          }
        }

        DocumentSupport.isPdfMimeType(record.mimeType) -> {
          val pdfBytes = withContext(ioDispatcher) { file.readBytes() }
          val response = withTimeout(DOC_TIMEOUT_MS) {
            model.generateContent(
              content {
                text(promptHeader)
                inlineData(pdfBytes, "application/pdf")
              }
            )
          }
          val summary = response.text?.trim().orEmpty()
          if (summary.isEmpty()) {
            DetailedDocumentAnalysisOutcome.Failed("Model returned an empty summary.")
          } else {
            DetailedDocumentAnalysisOutcome.Completed(summary)
          }
        }

        else -> DetailedDocumentAnalysisOutcome.UnsupportedForAnalysis
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Document analysis timed out", e)
      DetailedDocumentAnalysisOutcome.Failed("Timed out after 60 seconds.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Document analysis failed", e)
      DetailedDocumentAnalysisOutcome.Failed(e.localizedMessage ?: "Analysis failed.")
    }
  }

  private fun decodeScaledBitmap(file: File): Bitmap? {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
    val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
    var sampleSize = 1
    while (maxDim / sampleSize > 1600) {
      sampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
  }
}
