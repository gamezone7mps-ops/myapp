package com.example.features.documents

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.AppDatabase
import com.example.core.datastore.PreferencesRepository
import com.example.core.designsystem.LifeOsTheme
import com.example.core.documents.AppDocumentFileStorage
import com.example.core.localization.ProvideLifeOsLocale
import com.example.data.local.RoomDocumentRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppThemeMode
import com.example.domain.model.CalendarSystem
import com.example.domain.model.UserPreferences
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class TestDocPreferencesRepo : PreferencesRepository {
  private val state = MutableStateFlow(
    UserPreferences(
      language = AppLanguage.ENGLISH,
      calendarSystem = CalendarSystem.GREGORIAN
    )
  )
  override val userPreferences: Flow<UserPreferences> = state
  override suspend fun setLanguage(language: AppLanguage) { state.value = state.value.copy(language = language) }
  override suspend fun setThemeMode(themeMode: AppThemeMode) { state.value = state.value.copy(themeMode = themeMode) }
  override suspend fun setCalendarSystem(calendarSystem: CalendarSystem) { state.value = state.value.copy(calendarSystem = calendarSystem) }
  override suspend fun setNotificationsEnabled(enabled: Boolean) { state.value = state.value.copy(notificationsEnabled = enabled) }
  override suspend fun setAiDataConsentGranted(granted: Boolean) { state.value = state.value.copy(aiDataConsentGranted = granted) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentsUiFlowTest {

  @get:Rule
  val composeRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var fileStorage: AppDocumentFileStorage
  private lateinit var viewModel: DocumentsViewModel
  private lateinit var sampleFile: File

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    fileStorage = AppDocumentFileStorage(context, Dispatchers.Unconfined)
    val repo = RoomDocumentRepository(db.documentRecordDao(), fileStorage)
    viewModel = DocumentsViewModel(repo, fileStorage, TestDocPreferencesRepo())
    sampleFile = File(context.cacheDir, "lease_contract.txt").apply {
      writeText("Monthly lease terms stored locally.")
    }
  }

  @After
  fun tearDown() {
    db.close()
    sampleFile.delete()
    File(context.filesDir, AppDocumentFileStorage.DOCUMENTS_DIR_NAME).deleteRecursively()
  }

  @Test
  fun `import document, edit metadata, inspect verified vs unanalyzed status, and delete`() {
    composeRule.setContent {
      ProvideLifeOsLocale(language = AppLanguage.ENGLISH) {
        LifeOsTheme(themeMode = AppThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
          DocumentsScreen(viewModel = viewModel)
        }
      }
    }

    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("documents_empty_title").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("documents_empty_title").assertIsDisplayed()

    composeRule.runOnUiThread {
      viewModel.importFromUri(Uri.fromFile(sampleFile))
    }
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("document_editor_title_input").fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag("document_editor_title_input").performTextClearance()
    composeRule.onNodeWithTag("document_editor_title_input").performTextInput("Apartment Lease")
    composeRule.onNodeWithTag("document_editor_tags_input").performTextInput("housing, contract")
    composeRule.onNodeWithTag("document_editor_notes_input").performTextInput("Signed for 1 year")
    composeRule.onNodeWithTag("document_editor_save_button").performClick()

    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("document_editor_save_button").fetchSemanticsNodes().isEmpty() &&
        composeRule.onAllNodesWithText("Apartment Lease").fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithText("Apartment Lease").assertIsDisplayed()
    composeRule.onNodeWithText("Not Analyzed Yet").assertIsDisplayed()

    composeRule.onNodeWithText("Apartment Lease").performClick()
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("document_detail_extraction_box").fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag("document_detail_text_preview").assertIsDisplayed()
    composeRule.onNodeWithTag("document_detail_extraction_box")
      .performScrollTo()
      .assertIsDisplayed()

    composeRule.onNodeWithTag("document_detail_delete")
      .performScrollTo()
      .performClick()
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("confirm_delete_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("confirm_delete_button").performClick()

    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("documents_empty_title").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("documents_empty_title").assertIsDisplayed()
  }
}
