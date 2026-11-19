package com.example.core.ai

import com.example.core.calendar.PersianCalendarEngine
import com.example.domain.model.AppLanguage
import com.example.domain.model.CalendarSystem
import com.example.domain.model.MemoryEntryType
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus
import com.example.domain.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiProposalValidatorTest {

  private val prefs = UserPreferences(
    language = AppLanguage.ENGLISH,
    calendarSystem = CalendarSystem.GREGORIAN,
    defaultZoneId = "Asia/Tehran"
  )

  @Test
  fun `validates create event proposal and defaults 1 hour duration`() {
    val json = """
      {
        "mode": "PROPOSE_CREATE_EVENT",
        "replyText": "Exam scheduled for tomorrow morning. Please confirm.",
        "title": "Math Exam",
        "dateIso": "2026-11-20",
        "startTime": "10:00",
        "allDay": false,
        "location": "Hall B"
      }
    """.trimIndent()

    val outcome = AiProposalValidator.parseAndValidate(json, WorkspaceContextSnapshot(), prefs)
    assertTrue(outcome is AiTurnOutcome.Proposal)
    val proposal = (outcome as AiTurnOutcome.Proposal).action as AiProposedAction.CreateEventProposal

    assertEquals("Math Exam", proposal.title)
    assertEquals("Hall B", proposal.location)
    assertEquals(3_600_000L, proposal.endAtUtcMillis - proposal.startAtUtcMillis)
    val parts = PersianCalendarEngine.utcMillisToZonedParts(
      proposal.startAtUtcMillis,
      "Asia/Tehran",
      CalendarSystem.GREGORIAN
    )
    assertEquals(2026, parts.year)
    assertEquals(11, parts.month)
    assertEquals(20, parts.day)
    assertEquals(10, parts.hour)
    assertEquals(0, parts.minute)
  }

  @Test
  fun `downgrades event proposal without start time to clarification`() {
    val json = """
      {
        "mode": "PROPOSE_CREATE_EVENT",
        "replyText": "Adding exam.",
        "title": "Physics Exam",
        "dateIso": "2026-11-20",
        "allDay": false
      }
    """.trimIndent()

    val outcome = AiProposalValidator.parseAndValidate(json, WorkspaceContextSnapshot(), prefs)
    assertTrue(outcome is AiTurnOutcome.ClarificationNeeded)
  }

  @Test
  fun `downgrades event proposal with end before start to clarification`() {
    val json = """
      {
        "mode": "PROPOSE_CREATE_EVENT",
        "replyText": "Adding meeting.",
        "title": "Team Sync",
        "dateIso": "2026-11-20",
        "startTime": "14:00",
        "endTime": "13:00",
        "allDay": false
      }
    """.trimIndent()

    val outcome = AiProposalValidator.parseAndValidate(json, WorkspaceContextSnapshot(), prefs)
    assertTrue(outcome is AiTurnOutcome.ClarificationNeeded)
  }

  @Test
  fun `downgrades update task proposal when targetId does not exist in snapshot`() {
    val json = """
      {
        "mode": "PROPOSE_UPDATE_TASK",
        "replyText": "Updating task.",
        "targetId": "non-existent-id",
        "title": "Renamed Task"
      }
    """.trimIndent()

    val outcome = AiProposalValidator.parseAndValidate(json, WorkspaceContextSnapshot(), prefs)
    assertTrue(outcome is AiTurnOutcome.ClarificationNeeded)
  }

  @Test
  fun `accepts update task proposal when targetId exists`() {
    val existing = TaskItem(
      id = "task-123",
      title = "Read Chapter 4",
      description = "Pages 40-60",
      priority = TaskPriority.MEDIUM,
      status = TaskStatus.TODO,
      createdAtUtcMillis = 1_000L,
      updatedAtUtcMillis = 1_000L
    )
    val snapshot = WorkspaceContextSnapshot(allTasks = listOf(existing), matchedTasks = listOf(existing))
    val json = """
      {
        "mode": "PROPOSE_UPDATE_TASK",
        "replyText": "Marked high priority.",
        "targetId": "task-123",
        "priority": "URGENT",
        "status": "IN_PROGRESS"
      }
    """.trimIndent()

    val outcome = AiProposalValidator.parseAndValidate(json, snapshot, prefs)
    assertTrue(outcome is AiTurnOutcome.Proposal)
    val proposal = (outcome as AiTurnOutcome.Proposal).action as AiProposedAction.UpdateTaskProposal
    assertEquals("task-123", proposal.targetTaskId)
    assertEquals("Read Chapter 4", proposal.newTitle)
    assertEquals("Pages 40-60", proposal.newDescription)
    assertEquals(TaskPriority.URGENT, proposal.newPriority)
    assertEquals(TaskStatus.IN_PROGRESS, proposal.newStatus)
  }

  @Test
  fun `strips invalid non-http URL on create memory proposal`() {
    val json = """
      {
        "mode": "PROPOSE_CREATE_MEMORY",
        "replyText": "Saving server backup note.",
        "title": "Backup Schedule",
        "description": "Every Friday night",
        "entryType": "FACT",
        "tags": ["ops", "#server"],
        "sourceUrl": "javascript:alert(1)"
      }
    """.trimIndent()

    val outcome = AiProposalValidator.parseAndValidate(json, WorkspaceContextSnapshot(), prefs)
    assertTrue(outcome is AiTurnOutcome.Proposal)
    val proposal = (outcome as AiTurnOutcome.Proposal).action as AiProposedAction.CreateMemoryProposal
    assertEquals(MemoryEntryType.FACT, proposal.entryType)
    assertEquals(listOf("ops", "server"), proposal.tags)
    assertNull(proposal.sourceUrl)
  }

  @Test
  fun `returns service error outcome on malformed JSON`() {
    val outcome = AiProposalValidator.parseAndValidate("{not valid json", WorkspaceContextSnapshot(), prefs)
    assertTrue(outcome is AiTurnOutcome.Unavailable)
    assertEquals(AiUnavailableReason.SERVICE_ERROR, (outcome as AiTurnOutcome.Unavailable).reason)
  }
}
