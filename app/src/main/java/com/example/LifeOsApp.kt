package com.example

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.common.LifeOsContainer
import com.example.domain.model.UserPreferences
import com.example.features.assistant.AssistantScreen
import com.example.features.assistant.AssistantViewModel
import com.example.features.calendar.CalendarScreen
import com.example.features.calendar.CalendarViewModel
import com.example.features.dashboard.DashboardScreen
import com.example.features.dashboard.DashboardViewModel
import com.example.features.documents.DocumentsScreen
import com.example.features.documents.DocumentsViewModel
import com.example.features.hub.WorkspaceHubScreen
import com.example.features.insights.InsightsScreen
import com.example.features.insights.InsightsViewModel
import com.example.features.memory.MemoryScreen
import com.example.features.memory.MemoryViewModel
import com.example.features.navigation.LifeOsDestination
import com.example.features.settings.SettingsScreen
import com.example.features.settings.SettingsViewModel
import com.example.features.tasks.TasksScreen
import com.example.features.tasks.TasksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeOsApp(
  container: LifeOsContainer,
  preferences: UserPreferences
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route
  val currentDestination = LifeOsDestination.fromRoute(currentRoute)

  val navigateTo: (LifeOsDestination) -> Unit = { dest ->
    navController.navigate(dest.route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  val shortToday = remember(preferences.calendarSystem, preferences.language, preferences.defaultZoneId) {
    PersianCalendarEngine.formatShortDate(
      utcMillis = System.currentTimeMillis(),
      zoneId = preferences.defaultZoneId,
      calendarSystem = preferences.calendarSystem,
      language = preferences.language
    )
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (useNavigationRail) {
        NavigationRail(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.testTag("navigation_rail")
        ) {
          LifeOsDestination.railDestinations.forEach { dest ->
            val selected = currentDestination == dest
            NavigationRailItem(
              selected = selected,
              onClick = { navigateTo(dest) },
              icon = {
                Icon(
                  imageVector = destinationIcon(dest, selected),
                  contentDescription = stringResource(dest.labelRes)
                )
              },
              label = {
                Text(
                  text = stringResource(dest.labelRes),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              },
              modifier = Modifier.testTag("rail_${dest.route}")
            )
          }
        }
      }

      Scaffold(
        modifier = Modifier.weight(1f),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
          TopAppBar(
            title = {
              Text(
                text = stringResource(currentDestination.labelRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            navigationIcon = {
              val showBack = !useNavigationRail &&
                currentDestination !in LifeOsDestination.primaryBarDestinations
              if (showBack) {
                IconButton(
                  onClick = { navController.popBackStack() },
                  modifier = Modifier.testTag("top_bar_back")
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_navigate_back)
                  )
                }
              }
            },
            actions = {
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                  .padding(end = 6.dp)
                  .testTag("top_bar_date_chip")
              ) {
                Text(
                  text = shortToday,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
              }
              if (currentDestination != LifeOsDestination.SETTINGS) {
                IconButton(
                  onClick = { navigateTo(LifeOsDestination.SETTINGS) },
                  modifier = Modifier.testTag("top_bar_settings")
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.cd_open_settings)
                  )
                }
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background,
              titleContentColor = MaterialTheme.colorScheme.onBackground
            )
          )
        },
        bottomBar = {
          if (!useNavigationRail) {
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surface,
              modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
              LifeOsDestination.primaryBarDestinations.forEach { dest ->
                val isHubChild = dest == LifeOsDestination.HUB &&
                  currentDestination !in LifeOsDestination.primaryBarDestinations
                val selected = currentDestination == dest || isHubChild
                NavigationBarItem(
                  selected = selected,
                  onClick = { navigateTo(dest) },
                  icon = {
                    Icon(
                      imageVector = destinationIcon(dest, selected),
                      contentDescription = stringResource(dest.labelRes)
                    )
                  },
                  label = {
                    Text(
                      text = stringResource(dest.labelRes),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  },
                  modifier = Modifier.testTag("nav_${dest.route}")
                )
              }
            }
          }
        }
      ) { innerPadding ->
        NavHost(
          navController = navController,
          startDestination = LifeOsDestination.DASHBOARD.route,
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
          composable(LifeOsDestination.DASHBOARD.route) {
            val vm: DashboardViewModel = viewModel(
              factory = DashboardViewModel.Factory(
                preferencesRepository = container.preferencesRepository,
                vaultSummaryRepository = container.vaultSummaryRepository,
                taskRepository = container.taskRepository,
                calendarRepository = container.calendarRepository,
                memoryRepository = container.memoryRepository
              )
            )
            DashboardScreen(
              viewModel = vm,
              onNavigate = navigateTo
            )
          }

          composable(LifeOsDestination.TASKS.route) {
            val vm: TasksViewModel = viewModel(
              factory = TasksViewModel.Factory(
                taskRepository = container.taskRepository,
                preferencesRepository = container.preferencesRepository
              )
            )
            TasksScreen(viewModel = vm)
          }

          composable(LifeOsDestination.CALENDAR.route) {
            val vm: CalendarViewModel = viewModel(
              factory = CalendarViewModel.Factory(
                calendarRepository = container.calendarRepository,
                preferencesRepository = container.preferencesRepository
              )
            )
            CalendarScreen(viewModel = vm)
          }

          composable(LifeOsDestination.MEMORY.route) {
            val vm: MemoryViewModel = viewModel(
              factory = MemoryViewModel.Factory(
                memoryRepository = container.memoryRepository,
                preferencesRepository = container.preferencesRepository
              )
            )
            MemoryScreen(viewModel = vm)
          }

          composable(LifeOsDestination.HUB.route) {
            WorkspaceHubScreen(onNavigate = navigateTo)
          }

          composable(LifeOsDestination.DOCUMENTS.route) {
            val vm: DocumentsViewModel = viewModel(
              factory = DocumentsViewModel.Factory(
                documentRepository = container.documentRepository,
                documentFileStorage = container.documentFileStorage,
                preferencesRepository = container.preferencesRepository,
                documentAnalyzer = container.documentAnalyzer
              )
            )
            DocumentsScreen(viewModel = vm)
          }

          composable(LifeOsDestination.INSIGHTS.route) {
            val vm: InsightsViewModel = viewModel(
              factory = InsightsViewModel.Factory(
                taskRepository = container.taskRepository,
                calendarRepository = container.calendarRepository,
                memoryRepository = container.memoryRepository,
                documentRepository = container.documentRepository,
                preferencesRepository = container.preferencesRepository
              )
            )
            InsightsScreen(viewModel = vm)
          }

          composable(LifeOsDestination.ASSISTANT.route) {
            val vm: AssistantViewModel = viewModel(
              factory = AssistantViewModel.Factory(
                preferencesRepository = container.preferencesRepository,
                networkStatusProvider = container.networkStatusProvider,
                workspaceToolExecutor = container.workspaceToolExecutor,
                assistantGateway = container.assistantGateway
              )
            )
            AssistantScreen(
              viewModel = vm,
              onOpenSettings = { navigateTo(LifeOsDestination.SETTINGS) }
            )
          }

          composable(LifeOsDestination.SETTINGS.route) {
            val vm: SettingsViewModel = viewModel(
              factory = SettingsViewModel.Factory(
                preferencesRepository = container.preferencesRepository,
                reminderScheduler = container.reminderScheduler
              )
            )
            SettingsScreen(viewModel = vm)
          }
        }
      }
    }
  }
}

private fun destinationIcon(destination: LifeOsDestination, selected: Boolean): ImageVector {
  return when (destination) {
    LifeOsDestination.DASHBOARD -> if (selected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
    LifeOsDestination.TASKS -> if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle
    LifeOsDestination.CALENDAR -> if (selected) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth
    LifeOsDestination.MEMORY -> if (selected) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb
    LifeOsDestination.HUB -> if (selected) Icons.Filled.Apps else Icons.Outlined.Apps
    LifeOsDestination.DOCUMENTS -> if (selected) Icons.Filled.Description else Icons.Outlined.Description
    LifeOsDestination.INSIGHTS -> if (selected) Icons.Filled.Insights else Icons.Outlined.Insights
    LifeOsDestination.ASSISTANT -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
    LifeOsDestination.SETTINGS -> if (selected) Icons.Filled.Tune else Icons.Outlined.Tune
  }
}
