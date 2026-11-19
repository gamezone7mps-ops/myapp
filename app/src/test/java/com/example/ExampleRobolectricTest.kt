package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `app name matches LifeOS`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LifeOS", appName)
  }

  @Test
  fun `dashboard renders and settings language and calendar switch cleanly`() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("dashboard_hero_card").assertIsDisplayed()
    composeRule.onNodeWithTag("top_bar_settings").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("settings_language_english").assertIsDisplayed().performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("settings_calendar_preview"))
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("settings_calendar_gregorian").assertIsDisplayed().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("settings_calendar_preview").assertIsDisplayed()
  }
}
