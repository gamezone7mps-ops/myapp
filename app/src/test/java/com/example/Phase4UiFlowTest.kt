package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4UiFlowTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `hub opens ai assistant consent gate and settings toggles consent`() {
    composeRule.waitForIdle()

    // Open Hub -> Assistant
    composeRule.onNodeWithTag("nav_hub").performClick()
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("hub_list").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("hub_list")
      .performScrollToNode(hasTestTag("hub_item_assistant"))
    composeRule.onNodeWithTag("hub_item_assistant").performClick()

    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("assistant_consent_card").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("assistant_consent_card").assertIsDisplayed()

    // Grant consent in Assistant screen
    composeRule.onNodeWithTag("assistant_grant_consent_button").performClick()
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag("assistant_consent_card").fetchSemanticsNodes().isEmpty() &&
        composeRule.onAllNodesWithTag("assistant_empty_title").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("assistant_empty_title").assertIsDisplayed()
    composeRule.onNodeWithTag("assistant_input").assertIsDisplayed()

    // Verify synced consent toggle in Settings
    composeRule.onNodeWithTag("top_bar_settings").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("settings_ai_consent_switch"))
    composeRule.onNodeWithTag("settings_ai_consent_switch").assertIsDisplayed()
    composeRule.onNodeWithTag("settings_ai_consent_status").assertIsDisplayed()
  }
}
