package com.example.features.assistant

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.ai.AiProposedAction
import com.example.core.ai.AiTurnOutcome
import com.example.core.ai.AssistantGateway
import com.example.core.ai.AssistantMessageTurn
import com.example.core.ai.StaticNetworkStatusProvider
import com.example.core.ai.WorkspaceContextSnapshot
import com.example.core.ai.WorkspaceToolExecutor
import com.example.core.database.AppDatabase
import com.example.core.datastore.PreferencesRepository
import com.example.core.designsystem.LifeOsTheme
import com.example.core.documents.NoOpDocumentFileStorage
import com.example.core.localization.ProvideLifeOsLocale
import com.example.core.reminders.NoOpReminderScheduler
import com.example.data.local.RoomCalendarRepository
import com.example.data.local.RoomDocumentRepository
import com.example.data.local.RoomMemoryRepository
import com.example.data.local.RoomTaskRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppThemeMode
import com.example.domain.model.CalendarSystem
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakePreferencesRepo(
  initialConsent: Boolean = false
) : PreferencesRepository {
  private val flow = MutableStateFlow(
    UserPreferences(
      language = AppLanguage.ENGLISH,
      calendarSystem = CalendarSystem.GREGORIAN,
      aiDataConsentGranted = initialConsent,
      defaultZoneId = "Asia/Tehran"
    )
  )
  override val userPreferences: Flow<UserPreferences> = flow
  override suspend fun setLanguage(language: AppLanguage) { flow.value = flow.value.copy(language = language) }
  override suspend fun setThemeMode(themeMode: AppThemeMode) { flow.value = flow.value.copy(themeMode = themeMode) }
  override suspend fun setCalendarSystem(calendarSystem: CalendarSystem) { flow.value = flow.value.copy(calendarSystem = calendarSystem) }
  override suspend fun setNotificationsEnabled(enabled: Boolean) { flow.value = flow.value.copy(notificationsEnabled = enabled) }
  override suspend fun setAiDataConsentGranted(granted: Boolean) { flow.value = flow.value.copy(aiDataConsentGranted = granted) }
}

private class ScriptedAssistantGateway(
  var handler: (String) -> AiTurnOutcome
) : AssistantGateway {
  var callCount = 0

  override suspend fun processTurn(
    userMessage: String,
    history: List<AssistantMessageTurn>,
    workspaceContext: WorkspaceContextSnapshot,
    preferences: UserPreferences,
    currentUtcMillis: Long,
    onStreamingCharCount: (Int) -> Unit
  ): AiTurnOutcome {
    callCount += 1
    return handler(userMessage)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AssistantUiFlowTest {

  @get:Rule
  val composeRule = createComposeRule()

  private lateinit var db: AppDatabase
  private lateinit var prefsRepo: FakePreferencesRepo
  private lateinit var networkProvider: StaticNetworkStatusProvider
  private lateinit var calendarRepo: RoomCalendarRepository
  private lateinit var taskRepo: RoomTaskRepository
  private lateinit var gateway: ScriptedAssistantGateway
  private lateinit var viewModel: AssistantViewModel

  @Before
  fun setup() {
    val context: Context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    prefsRepo = FakePreferencesRepo(initialConsent = false)
    networkProvider = StaticNetworkStatusProvider(online = true)
    taskRepo = RoomTaskRepository(db.taskDao(), NoOpReminderScheduler)
    calendarRepo = RoomCalendarRepository(db.calendarEventDao(), NoOpReminderScheduler)
    val memoryRepo = RoomMemoryRepository(db.memoryEntryDao())
    val docRepo = RoomDocumentRepository(db.documentRecordDao(), NoOpDocumentFileStorage)
    val executor = WorkspaceToolExecutor(taskRepo, calendarRepo, memoryRepo, docRepo, NoOpDocumentFileStorage)
    gateway = ScriptedAssistantGateway { prompt ->
      if (prompt.contains("exam", ignoreCase = true)) {
        AiTurnOutcome.Proposal(
          explanationText = "Please review this event preview:",
          action = AiProposedAction.CreateEventProposal(
            title = "Exam",
            startAtUtcMillis = 1_800_000_000_000L,
            endAtUtcMillis = 1_800_003_600_000L,
            zoneId = "Asia/Tehran"
          )
        )
      } else {
        AiTurnOutcome.ClarificationNeeded(
          questionText = "Which date and priority should I use?",
          suggestions = listOf("High priority today")
        )
      }
    }
    viewModel = AssistantViewModel(prefsRepo, networkProvider, executor, gateway)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `consent gate blocks AI calls until enabled, and proposal saves only after user confirmation`() {
    composeRule.setContent {
      ProvideLifeOsLocale(language = AppLanguage.ENGLISH) {
        LifeOsTheme(themeMode = AppThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
          AssistantScreen(viewModel = viewModel, onOpenSettings = {})
        }
      }
    }

    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("assistant_consent_card").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("assistant_consent_card").assertIsDisplayed()

    // Grant consent
    composeRule.onNodeWithTag("assistant_grant_consent_button").performClick()
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("assistant_consent_card").fetchSemanticsNodes().isEmpty()
    }

    // Send exam prompt -> shows preview card WITHOUT saving yet
    composeRule.onNodeWithTag("assistant_input").performTextInput("I have an exam tomorrow at 10")
    composeRule.onNodeWithTag("assistant_send_button").performClick()

    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithText("Confirm & Save").fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals(1, gateway.callCount)
    assertEquals(0, runBlocking { calendarRepo.observeEvents().first().size })

    // Confirm -> persists event to Room
    composeRule.onNodeWithText("Confirm & Save")
      .performScrollTo()
      .performClick()
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithText("Saved to your workspace").fetchSemanticsNodes().isNotEmpty()
    }
    val savedEvents = runBlocking { calendarRepo.observeEvents().first() }
    assertEquals(1, savedEvents.size)
    assertEquals("Exam", savedEvents.first().title)
  }

  @Test
  fun `offline state shows honest error without calling gateway, and discard prevents write`() {
    runBlocking { prefsRepo.setAiDataConsentGranted(true) }
    networkProvider.online = false
    viewModel.refreshConnectivity()

    composeRule.setContent {
      ProvideLifeOsLocale(language = AppLanguage.ENGLISH) {
        LifeOsTheme(themeMode = AppThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
          AssistantScreen(viewModel = viewModel, onOpenSettings = {})
        }
      }
    }

    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("assistant_offline_banner").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("assistant_offline_banner").assertIsDisplayed()

    composeRule.onNodeWithTag("assistant_input").performTextInput("I have an exam tomorrow at 10")
    composeRule.onNodeWithTag("assistant_send_button").performClick()

    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithText("Cannot reach Gemini while offline. Connect to the internet and tap Retry.")
        .fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals(0, gateway.callCount)

    // Go online, retry -> discard proposal
    networkProvider.online = true
    composeRule.onNode(hasText("Retry") and hasAnyAncestor(hasTestTag("assistant_messages_list")))
      .performScrollTo()
      .performClick()
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithText("Discard").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText("Discard")
      .performScrollTo()
      .performClick()
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithText("Discarded — no changes saved").fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals(0, runBlocking { calendarRepo.observeEvents().first().size })
  }
}
