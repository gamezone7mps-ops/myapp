package com.example.features.navigation

import androidx.annotation.StringRes
import com.example.R

enum class LifeOsDestination(
  val route: String,
  @StringRes val labelRes: Int
) {
  DASHBOARD("dashboard", R.string.nav_dashboard),
  TASKS("tasks", R.string.nav_tasks),
  CALENDAR("calendar", R.string.nav_calendar),
  MEMORY("memory", R.string.nav_memory),
  HUB("hub", R.string.nav_more),
  DOCUMENTS("documents", R.string.nav_documents),
  INSIGHTS("insights", R.string.nav_insights),
  ASSISTANT("assistant", R.string.nav_assistant),
  SETTINGS("settings", R.string.nav_settings);

  companion object {
    val primaryBarDestinations = listOf(DASHBOARD, TASKS, CALENDAR, MEMORY, HUB)
    val railDestinations = listOf(
      DASHBOARD,
      TASKS,
      CALENDAR,
      MEMORY,
      DOCUMENTS,
      INSIGHTS,
      ASSISTANT,
      SETTINGS
    )

    fun fromRoute(route: String?): LifeOsDestination =
      entries.firstOrNull { it.route == route } ?: DASHBOARD
  }
}
