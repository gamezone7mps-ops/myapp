package com.example.core.ai

import android.util.Log
import com.example.core.calendar.PersianCalendarEngine
import com.example.domain.model.AppLanguage
import com.example.domain.model.CalendarSystem
import com.example.domain.model.UserPreferences
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiAssistantGateway"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val AI_TIMEOUT_MS = 60_000L

interface AssistantGateway {
  suspend fun processTurn(
    userMessage: String,
    history: List<AssistantMessageTurn>,
    workspaceContext: WorkspaceContextSnapshot,
    preferences: UserPreferences,
    currentUtcMillis: Long,
    onStreamingCharCount: (Int) -> Unit = {}
  ): AiTurnOutcome
}

class GeminiAssistantGateway : AssistantGateway {

  private val structuredSchema: Schema by lazy {
    Schema.obj(
      properties = mapOf(
        "mode" to Schema.enumeration(
          values = listOf(
            "REPLY",
            "CLARIFY",
            "PROPOSE_CREATE_TASK",
            "PROPOSE_UPDATE_TASK",
            "PROPOSE_CREATE_EVENT",
            "PROPOSE_UPDATE_EVENT",
            "PROPOSE_CREATE_MEMORY"
          ),
          description = "Action mode: REPLY to answer questions, CLARIFY when info is missing/ambiguous, or PROPOSE_* to show a preview before saving."
        ),
        "replyText" to Schema.string(
          description = "Natural conversational message, answer, clarification question, or explanation of the proposal in the user's language."
        ),
        "clarificationSuggestions" to Schema.array(
          items = Schema.string(),
          description = "Up to 3 short quick-reply options if mode is CLARIFY.",
          nullable = true
        ),
        "referencedTitles" to Schema.array(
          items = Schema.string(),
          description = "Titles of workspace tasks, events, memories, or documents referenced in this response.",
          nullable = true
        ),
        "targetId" to Schema.string(
          description = "Exact ID of existing task or event when mode is PROPOSE_UPDATE_TASK or PROPOSE_UPDATE_EVENT.",
          nullable = true
        ),
        "title" to Schema.string(
          description = "Title for task, event, or memory proposal.",
          nullable = true
        ),
        "description" to Schema.string(
          description = "Description or body content for task, event, or memory.",
          nullable = true
        ),
        "dateIso" to Schema.string(
          description = "Gregorian ISO date YYYY-MM-DD in the user's local timezone.",
          nullable = true
        ),
        "startTime" to Schema.string(
          description = "Event start time in 24-hour HH:mm format.",
          nullable = true
        ),
        "endTime" to Schema.string(
          description = "Event end time in 24-hour HH:mm format.",
          nullable = true
        ),
        "dueTime" to Schema.string(
          description = "Task due time in 24-hour HH:mm format.",
          nullable = true
        ),
        "allDay" to Schema.boolean(
          description = "True if calendar event is all-day.",
          nullable = true
        ),
        "priority" to Schema.enumeration(
          values = listOf("LOW", "MEDIUM", "HIGH", "URGENT"),
          description = "Task priority."
        ),
        "status" to Schema.enumeration(
          values = listOf("TODO", "IN_PROGRESS", "COMPLETED", "ARCHIVED"),
          description = "Task status."
        ),
        "category" to Schema.string(
          description = "Optional task category.",
          nullable = true
        ),
        "location" to Schema.string(
          description = "Optional calendar event location.",
          nullable = true
        ),
        "recurrence" to Schema.enumeration(
          values = listOf("NONE", "DAILY", "WEEKLY", "MONTHLY"),
          description = "Optional recurrence frequency."
        ),
        "reminderMinutesBefore" to Schema.integer(
          description = "Optional reminder offset in minutes (0, 10, 30, 60, or 1440).",
          nullable = true
        ),
        "entryType" to Schema.enumeration(
          values = listOf("NOTE", "IDEA", "LINK", "FACT", "REFERENCE"),
          description = "Smart Memory entry type."
        ),
        "tags" to Schema.array(
          items = Schema.string(),
          description = "Optional tags for memory entry.",
          nullable = true
        ),
        "sourceUrl" to Schema.string(
          description = "Optional http:// or https:// URL for memory entry.",
          nullable = true
        )
      ),
      optionalProperties = listOf(
        "clarificationSuggestions",
        "referencedTitles",
        "targetId",
        "title",
        "description",
        "dateIso",
        "startTime",
        "endTime",
        "dueTime",
        "allDay",
        "priority",
        "status",
        "category",
        "location",
        "recurrence",
        "reminderMinutesBefore",
        "entryType",
        "tags",
        "sourceUrl"
      )
    )
  }

  private val model by lazy {
    Firebase.ai.generativeModel(
      modelName = MODEL_NAME,
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = structuredSchema
        temperature = 0.2f
      }
    )
  }

  override suspend fun processTurn(
    userMessage: String,
    history: List<AssistantMessageTurn>,
    workspaceContext: WorkspaceContextSnapshot,
    preferences: UserPreferences,
    currentUtcMillis: Long,
    onStreamingCharCount: (Int) -> Unit
  ): AiTurnOutcome {
    val promptText = buildPrompt(
      userMessage = userMessage,
      history = history,
      snapshot = workspaceContext,
      preferences = preferences,
      currentUtcMillis = currentUtcMillis
    )

    val accumulated = StringBuilder()
    return try {
      withTimeout(AI_TIMEOUT_MS) {
        model.generateContentStream(content { text(promptText) }).collect { chunk ->
          val chunkText = chunk.text.orEmpty()
          if (chunkText.isNotEmpty()) {
            accumulated.append(chunkText)
            onStreamingCharCount(accumulated.length)
          }
        }
      }

      val rawJson = accumulated.toString().trim()
      if (rawJson.isEmpty()) {
        AiTurnOutcome.Unavailable(
          reason = AiUnavailableReason.SERVICE_ERROR,
          detail = "Gemini returned an empty response."
        )
      } else {
        AiProposalValidator.parseAndValidate(
          rawJson = rawJson,
          snapshot = workspaceContext,
          preferences = preferences
        )
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini assistant generation timed out", e)
      AiTurnOutcome.Unavailable(
        reason = AiUnavailableReason.TIMEOUT,
        detail = e.localizedMessage
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini assistant generation failed", e)
      AiTurnOutcome.Unavailable(
        reason = AiUnavailableReason.SERVICE_ERROR,
        detail = e.localizedMessage ?: "AI service error."
      )
    }
  }

  private fun buildPrompt(
    userMessage: String,
    history: List<AssistantMessageTurn>,
    snapshot: WorkspaceContextSnapshot,
    preferences: UserPreferences,
    currentUtcMillis: Long
  ): String {
    val gNow = PersianCalendarEngine.utcMillisToZonedParts(
      currentUtcMillis,
      preferences.defaultZoneId,
      CalendarSystem.GREGORIAN
    )
    val jNow = PersianCalendarEngine.utcMillisToZonedParts(
      currentUtcMillis,
      preferences.defaultZoneId,
      CalendarSystem.JALALI
    )
    val gDateIso = "%04d-%02d-%02d".format(gNow.year, gNow.month, gNow.day)
    val jDateIso = "%04d/%02d/%02d".format(jNow.year, jNow.month, jNow.day)
    val nowHm = "%02d:%02d".format(gNow.hour, gNow.minute)
    val langLabel = if (preferences.language == AppLanguage.PERSIAN) "Persian (fa)" else "English (en)"

    return buildString {
      appendLine("You are the LifeOS Personal Assistant.")
      appendLine("Available controlled tools: searchTasks, searchCalendar, searchMemory, searchDocuments (results already provided below), and write proposals createTask, updateTask, createCalendarEvent, updateCalendarEvent, createMemory.")
      appendLine("Strict rules:")
      appendLine("1. Current time: Gregorian $gDateIso $nowHm | Jalali $jDateIso | Zone: ${preferences.defaultZoneId} | App language: $langLabel.")
      appendLine("2. Reply in the same language as the user's message (default to $langLabel if unclear).")
      appendLine("3. Always convert relative dates ('today', 'tomorrow', 'فردا', 'پس‌فردا') or Jalali dates into Gregorian YYYY-MM-DD in 'dateIso', and 24-hour HH:mm in 'startTime'/'endTime'/'dueTime'.")
      appendLine("4. Never claim you saved or modified data—PROPOSE_* modes only create a user confirmation preview card.")
      appendLine("5. If required info is missing or ambiguous (for instance an event with no date or time, or an update matching multiple/zero existing IDs), set mode to CLARIFY and ask a concise clarifying question.")
      appendLine("6. Never invent workspace records. Only cite items that appear in the scoped search results below.")
      appendLine()
      appendLine("=== SCOPED WORKSPACE SEARCH RESULTS ===")
      appendLine("Tasks (${snapshot.matchedTasks.size}):")
      snapshot.matchedTasks.forEach { task ->
        val dueLabel = task.dueAtUtcMillis?.let {
          val p = PersianCalendarEngine.utcMillisToZonedParts(it, preferences.defaultZoneId, CalendarSystem.GREGORIAN)
          "%04d-%02d-%02d %02d:%02d".format(p.year, p.month, p.day, p.hour, p.minute)
        } ?: "none"
        appendLine("- [id=${task.id}] ${task.title} | status=${task.status.key} | priority=${task.priority.name} | due=$dueLabel | cat=${task.category ?: "-"}")
      }
      appendLine("Calendar Events (${snapshot.matchedEvents.size}):")
      snapshot.matchedEvents.forEach { ev ->
        val sp = PersianCalendarEngine.utcMillisToZonedParts(ev.startAtUtcMillis, ev.zoneId, CalendarSystem.GREGORIAN)
        val ep = PersianCalendarEngine.utcMillisToZonedParts(ev.endAtUtcMillis, ev.zoneId, CalendarSystem.GREGORIAN)
        appendLine("- [id=${ev.id}] ${ev.title} | %04d-%02d-%02d %02d:%02d..%02d:%02d | loc=${ev.location ?: "-"}".format(sp.year, sp.month, sp.day, sp.hour, sp.minute, ep.hour, ep.minute))
      }
      appendLine("Smart Memories (${snapshot.matchedMemories.size}):")
      snapshot.matchedMemories.forEach { mem ->
        appendLine("- [id=${mem.id}] (${mem.entryType.key}) ${mem.title}: ${mem.content.take(240)} | tags=${mem.tags.joinToString(",")}")
      }
      appendLine("Documents (${snapshot.matchedDocuments.size}):")
      snapshot.matchedDocuments.forEach { item ->
        val doc = item.record
        appendLine("- [id=${doc.id}] ${doc.title} (${doc.mimeType}) | verifiedNotes=${doc.userVerifiedNotes.take(200)} | extracted=${doc.extractedSummary?.take(200) ?: "NOT_ANALYZED"} | textExcerpt=${item.textExcerpt?.take(350) ?: "-"}")
      }
      appendLine()
      if (history.isNotEmpty()) {
        appendLine("=== RECENT CONVERSATION ===")
        history.takeLast(6).forEach { turn ->
          appendLine(if (turn.isUser) "User: ${turn.text}" else "Assistant: ${turn.text}")
        }
        appendLine()
      }
      appendLine("Current User Message: $userMessage")
    }
  }
}
