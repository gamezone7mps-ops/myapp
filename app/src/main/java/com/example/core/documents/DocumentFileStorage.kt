package com.example.core.documents

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.domain.model.DocumentRecord
import com.example.domain.model.ExtractionStatus
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DocumentImportError {
  UNSUPPORTED_TYPE,
  EMPTY_OR_UNREADABLE,
  TOO_LARGE,
  COPY_FAILED
}

data class ImportedDocumentFile(
  val suggestedTitle: String,
  val mimeType: String,
  val localRelativePath: String,
  val fileSizeBytes: Long
)

sealed interface DocumentImportResult {
  data class Success(val file: ImportedDocumentFile) : DocumentImportResult
  data class Failure(val reason: DocumentImportError) : DocumentImportResult
}

data class DocumentAnalysisResult(
  val extractedSummary: String?,
  val status: ExtractionStatus
)

interface DocumentAnalyzer {
  suspend fun analyze(record: DocumentRecord): DocumentAnalysisResult
}

object OfflineNoOpDocumentAnalyzer : DocumentAnalyzer {
  override suspend fun analyze(record: DocumentRecord): DocumentAnalysisResult =
    DocumentAnalysisResult(
      extractedSummary = null,
      status = ExtractionStatus.NOT_ANALYZED
    )
}

interface DocumentFileStorage {
  suspend fun importFromUri(uri: Uri): DocumentImportResult
  fun resolveLocalFile(localRelativePath: String): File?
  fun deleteLocalFile(localRelativePath: String): Boolean
  suspend fun readTextPreview(localRelativePath: String, mimeType: String, maxChars: Int = 4000): String?
  fun createContentUri(localRelativePath: String): Uri?
}

object NoOpDocumentFileStorage : DocumentFileStorage {
  override suspend fun importFromUri(uri: Uri): DocumentImportResult =
    DocumentImportResult.Failure(DocumentImportError.COPY_FAILED)

  override fun resolveLocalFile(localRelativePath: String): File? = null
  override fun deleteLocalFile(localRelativePath: String): Boolean = false
  override suspend fun readTextPreview(
    localRelativePath: String,
    mimeType: String,
    maxChars: Int
  ): String? = null

  override fun createContentUri(localRelativePath: String): Uri? = null
}

class AppDocumentFileStorage(
  private val context: Context,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DocumentFileStorage {

  private val documentsRootDir: File
    get() = File(context.filesDir, DOCUMENTS_DIR_NAME).apply { mkdirs() }

  override suspend fun importFromUri(uri: Uri): DocumentImportResult = withContext(ioDispatcher) {
    val resolver = context.contentResolver
    var displayName: String? = null
    var reportedSize: Long = -1L

    runCatching {
      resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) reportedSize = cursor.getLong(sizeIdx)
          }
        }
    }

    if (reportedSize > MAX_FILE_SIZE_BYTES) {
      return@withContext DocumentImportResult.Failure(DocumentImportError.TOO_LARGE)
    }

    val rawName = displayName?.takeIf { it.isNotBlank() }
      ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
      ?: "document_${UUID.randomUUID().toString().take(8)}"

    val resolvedMime = resolveMimeType(uri, rawName)
    if (!DocumentSupport.isSupportedMimeType(resolvedMime)) {
      return@withContext DocumentImportResult.Failure(DocumentImportError.UNSUPPORTED_TYPE)
    }

    val safeFileName = sanitizeFileName(rawName, resolvedMime)
    val storedFileName = "${UUID.randomUUID()}_$safeFileName"
    val destFile = File(documentsRootDir, storedFileName)

    return@withContext try {
      val inputStream = resolver.openInputStream(uri)
        ?: return@withContext DocumentImportResult.Failure(DocumentImportError.EMPTY_OR_UNREADABLE)

      var totalBytes = 0L
      inputStream.use { input ->
        destFile.outputStream().use { output ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            totalBytes += read
            if (totalBytes > MAX_FILE_SIZE_BYTES) {
              destFile.delete()
              return@withContext DocumentImportResult.Failure(DocumentImportError.TOO_LARGE)
            }
            output.write(buffer, 0, read)
          }
        }
      }

      if (totalBytes <= 0L) {
        destFile.delete()
        return@withContext DocumentImportResult.Failure(DocumentImportError.EMPTY_OR_UNREADABLE)
      }

      DocumentImportResult.Success(
        ImportedDocumentFile(
          suggestedTitle = rawName.substringBeforeLast('.').ifBlank { rawName },
          mimeType = resolvedMime,
          localRelativePath = "$DOCUMENTS_DIR_NAME/$storedFileName",
          fileSizeBytes = totalBytes
        )
      )
    } catch (_: SecurityException) {
      destFile.delete()
      DocumentImportResult.Failure(DocumentImportError.EMPTY_OR_UNREADABLE)
    } catch (_: Exception) {
      destFile.delete()
      DocumentImportResult.Failure(DocumentImportError.COPY_FAILED)
    }
  }

  override fun resolveLocalFile(localRelativePath: String): File? {
    if (localRelativePath.isBlank()) return null
    return try {
      val rootCanonical = documentsRootDir.canonicalFile
      val candidate = File(context.filesDir, localRelativePath).canonicalFile
      if (!candidate.path.startsWith(rootCanonical.path + File.separator)) {
        null
      } else {
        candidate
      }
    } catch (_: Exception) {
      null
    }
  }

  override fun deleteLocalFile(localRelativePath: String): Boolean {
    val file = resolveLocalFile(localRelativePath) ?: return false
    return if (file.exists()) file.delete() else false
  }

  override suspend fun readTextPreview(
    localRelativePath: String,
    mimeType: String,
    maxChars: Int
  ): String? = withContext(ioDispatcher) {
    if (!DocumentSupport.isTextMimeType(mimeType)) return@withContext null
    val file = resolveLocalFile(localRelativePath) ?: return@withContext null
    if (!file.exists() || !file.canRead()) return@withContext null
    return@withContext runCatching {
      file.bufferedReader(Charsets.UTF_8).use { reader ->
        val buf = CharArray(maxChars)
        val count = reader.read(buf)
        if (count <= 0) "" else String(buf, 0, count)
      }
    }.getOrNull()
  }

  override fun createContentUri(localRelativePath: String): Uri? {
    val file = resolveLocalFile(localRelativePath) ?: return null
    if (!file.exists()) return null
    return runCatching {
      FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )
    }.getOrNull()
  }

  private fun resolveMimeType(uri: Uri, rawName: String): String {
    val fromResolver = context.contentResolver.getType(uri)?.lowercase(Locale.US)
    if (!fromResolver.isNullOrBlank() && fromResolver != "application/octet-stream") {
      return fromResolver
    }
    val ext = rawName.substringAfterLast('.', "").lowercase(Locale.US)
    if (ext.isNotEmpty()) {
      when (ext) {
        "md", "markdown" -> return "text/markdown"
        "csv" -> return "text/csv"
        "json" -> return "application/json"
        "txt", "log" -> return "text/plain"
        "pdf" -> return "application/pdf"
        "png" -> return "image/png"
        "jpg", "jpeg" -> return "image/jpeg"
        "webp" -> return "image/webp"
        "heic" -> return "image/heic"
        "doc" -> return "application/msword"
        "docx" -> return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      }
      MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
    }
    return fromResolver ?: "application/octet-stream"
  }

  private fun sanitizeFileName(rawName: String, mimeType: String): String {
    val cleaned = rawName
      .replace(Regex("[^A-Za-z0-9._-]"), "_")
      .trim('_')
      .take(64)
      .ifBlank { "document" }
    if (cleaned.contains('.')) return cleaned
    val fallbackExt = when {
      mimeType == "application/pdf" -> "pdf"
      mimeType == "text/plain" -> "txt"
      mimeType == "text/markdown" -> "md"
      mimeType == "text/csv" -> "csv"
      mimeType == "application/json" -> "json"
      mimeType.startsWith("image/") -> mimeType.substringAfter('/').substringBefore(';').ifBlank { "jpg" }
      else -> "bin"
    }
    return "$cleaned.$fallbackExt"
  }

  companion object {
    const val DOCUMENTS_DIR_NAME = "documents"
    const val MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L
  }
}

object DocumentSupport {
  val pickerMimeTypes = arrayOf(
    "application/pdf",
    "text/plain",
    "text/markdown",
    "text/csv",
    "application/json",
    "image/*",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  )

  fun isSupportedMimeType(mimeType: String): Boolean {
    val normalized = mimeType.lowercase(Locale.US).substringBefore(';').trim()
    return normalized.startsWith("image/") ||
      normalized.startsWith("text/") ||
      normalized == "application/pdf" ||
      normalized == "application/json" ||
      normalized == "application/msword" ||
      normalized == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  }

  fun isImageMimeType(mimeType: String): Boolean =
    mimeType.lowercase(Locale.US).startsWith("image/")

  fun isPdfMimeType(mimeType: String): Boolean =
    mimeType.lowercase(Locale.US) == "application/pdf"

  fun isTextMimeType(mimeType: String): Boolean {
    val normalized = mimeType.lowercase(Locale.US).substringBefore(';').trim()
    return normalized.startsWith("text/") || normalized == "application/json"
  }

  fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
  }
}
