package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2UiFlowTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `create task and prevent duplicate creation`() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("nav_tasks").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("tasks_add_fab").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("task_editor_title_input").performTextInput("Prepare Phase 2 report")
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("task_editor_save_button").performClick()
    composeRule.waitForIdle()

    composeRule.onAllNodesWithText("Prepare Phase 2 report").assertCountEquals(1)
  }

  @Test
  fun `create calendar event and switch views`() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("nav_calendar").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("calendar_month_grid").assertIsDisplayed()
    composeRule.onNodeWithTag("calendar_mode_week").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("calendar_week_strip").assertIsDisplayed()

    composeRule.onNodeWithTag("calendar_add_fab").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("event_editor_title_input").performTextInput("Algorithm Exam")
    composeRule.onNodeWithTag("event_editor_save_button").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithText("Algorithm Exam").assertIsDisplayed()
  }

  @Test
  fun `create memory entry and filter via search`() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("nav_memory").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("memory_add_fab").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("memory_editor_title_input").performTextInput("Wifi Key")
    composeRule.onNodeWithTag("memory_editor_content_input").performTextInput("Office guest password")
    composeRule.onNodeWithTag("memory_editor_tags_input").performTextInput("office")
    composeRule.onNodeWithTag("memory_editor_save_button").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithText("Wifi Key").assertIsDisplayed()
    composeRule.onNodeWithTag("memory_search_input").performTextInput("office")
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Wifi Key").assertIsDisplayed()
  }
}
