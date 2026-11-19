package com.example.core.documents

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.AppDatabase
import com.example.data.local.RoomDocumentRepository
import com.example.domain.model.DocumentRecord
import com.example.domain.model.ExtractionStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDocumentFileStorageTest {

  private lateinit var context: Context
  private lateinit var storage: AppDocumentFileStorage
  private lateinit var db: AppDatabase
  private lateinit var docRepo: RoomDocumentRepository
  private lateinit var tempSourceDir: File

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    storage = AppDocumentFileStorage(context, Dispatchers.Unconfined)
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    docRepo = RoomDocumentRepository(db.documentRecordDao(), storage)
    tempSourceDir = File(context.cacheDir, "test_docs_src").apply { mkdirs() }
  }

  @After
  fun tearDown() {
    db.close()
    tempSourceDir.deleteRecursively()
    File(context.filesDir, AppDocumentFileStorage.DOCUMENTS_DIR_NAME).deleteRecursively()
  }

  @Test
  fun `import text file copies into private storage, previews text, and deletes on softDelete`() = runTest {
    val srcFile = File(tempSourceDir, "meeting_notes.txt").apply {
      writeText("LifeOS Phase 3 specification check")
    }
    val result = storage.importFromUri(Uri.fromFile(srcFile))
    assertTrue(result is DocumentImportResult.Success)
    val imported = (result as DocumentImportResult.Success).file

    assertEquals("meeting_notes", imported.suggestedTitle)
    assertEquals("text/plain", imported.mimeType)
    assertTrue(imported.fileSizeBytes > 0L)

    val copiedFile = storage.resolveLocalFile(imported.localRelativePath)
    assertNotNull(copiedFile)
    assertTrue(copiedFile!!.exists())

    val preview = storage.readTextPreview(imported.localRelativePath, imported.mimeType)
    assertEquals("LifeOS Phase 3 specification check", preview)

    val contentUri = storage.createContentUri(imported.localRelativePath)
    assertNotNull(contentUri)

    val now = 1_795_086_000_000L
    val record = DocumentRecord(
      id = "doc-1",
      title = imported.suggestedTitle,
      mimeType = imported.mimeType,
      localRelativePath = imported.localRelativePath,
      fileSizeBytes = imported.fileSizeBytes,
      documentDateUtcMillis = now,
      tags = listOf("spec"),
      userVerifiedNotes = "Verified manually",
      extractedSummary = null,
      extractionStatus = ExtractionStatus.NOT_ANALYZED,
      createdAtUtcMillis = now,
      updatedAtUtcMillis = now
    )
    docRepo.upsertDocument(record)
    assertEquals(1, docRepo.observeDocuments().first().size)

    docRepo.softDeleteDocument("doc-1", now + 5000L)
    assertTrue(docRepo.observeDocuments().first().isEmpty())
    assertFalse(copiedFile.exists())
  }

  @Test
  fun `import rejects empty file and unsupported file extension`() = runTest {
    val emptyFile = File(tempSourceDir, "empty.txt").apply { createNewFile() }
    val emptyResult = storage.importFromUri(Uri.fromFile(emptyFile))
    assertEquals(
      DocumentImportResult.Failure(DocumentImportError.EMPTY_OR_UNREADABLE),
      emptyResult
    )

    val exeFile = File(tempSourceDir, "payload.exe").apply { writeText("MZ") }
    val unsupportedResult = storage.importFromUri(Uri.fromFile(exeFile))
    assertEquals(
      DocumentImportResult.Failure(DocumentImportError.UNSUPPORTED_TYPE),
      unsupportedResult
    )
  }

  @Test
  fun `resolveLocalFile blocks path traversal outside documents directory`() {
    assertNull(storage.resolveLocalFile("../shared_prefs/secret.xml"))
    assertNull(storage.resolveLocalFile("documents/../../etc/passwd"))
  }
}
