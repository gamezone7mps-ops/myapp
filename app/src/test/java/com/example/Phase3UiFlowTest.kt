package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class Phase3UiFlowTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `hub opens functional documents vault and settings shows notification status`() {
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("nav_hub").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("hub_item_documents").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("documents_import_photo_button").assertIsDisplayed()
    composeRule.onNodeWithTag("documents_import_file_button").assertIsDisplayed()
    composeRule.onNodeWithTag("documents_empty_title").assertIsDisplayed()

    composeRule.onNodeWithTag("top_bar_settings").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("settings_notification_status_badge"))
    composeRule.onNodeWithTag("settings_notification_status_badge").assertIsDisplayed()
    composeRule.onNodeWithTag("settings_notifications_switch").assertIsDisplayed()
  }
}
