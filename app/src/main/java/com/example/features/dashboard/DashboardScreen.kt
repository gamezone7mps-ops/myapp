package com.example.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.calendar.PersianCalendarEngine
import com.example.features.calendar.EventEditorDialog
import com.example.features.memory.MemoryEditorDialog
import com.example.features.navigation.LifeOsDestination
import com.example.features.tasks.TaskEditorDialog
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

@Composable
fun DashboardScreen(
  viewModel: DashboardViewModel,
  onNavigate: (LifeOsDestination) -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  when (val state = uiState) {
    DashboardUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("dashboard_loading"))
      }
    }

    is DashboardUiState.Error -> {
      Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = state.message,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyLarge
        )
      }
    }

    is DashboardUiState.Success -> {
      val prefs = state.preferences
      val counts = state.counts
      val nowUtc = remember { System.currentTimeMillis() }

      val greetingRes = remember(prefs.defaultZoneId) {
        val hour = GregorianCalendar(TimeZone.getTimeZone(prefs.defaultZoneId)).apply {
          timeInMillis = nowUtc
        }.get(Calendar.HOUR_OF_DAY)
        when {
          hour < 12 -> R.string.dashboard_greeting_morning
          hour < 17 -> R.string.dashboard_greeting_afternoon
          else -> R.string.dashboard_greeting_evening
        }
      }

      val formattedToday = remember(prefs.calendarSystem, prefs.language, prefs.defaultZoneId) {
        PersianCalendarEngine.formatFullDate(
          utcMillis = nowUtc,
          zoneId = prefs.defaultZoneId,
          calendarSystem = prefs.calendarSystem,
          language = prefs.language
        )
      }

      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
      ) {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
            .padding(horizontal = 16.dp)
            .testTag("dashboard_list"),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item { Spacer(modifier = Modifier.height(4.dp)) }

          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_hero_card"),
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(
                    Brush.linearGradient(
                      colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                      )
                    )
                  )
                  .padding(20.dp)
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.OfflinePin,
                      contentDescription = stringResource(R.string.cd_offline_badge),
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp)
                    )
                    Text(
                      text = stringResource(R.string.status_offline_ready),
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary
                    )
                  }

                  Text(
                    text = stringResource(greetingRes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                  )

                  Text(
                    text = formattedToday,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("dashboard_today_date")
                  )

                  Text(
                    text = stringResource(R.string.dashboard_timezone_label, prefs.defaultZoneId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          item {
            Text(
              text = stringResource(R.string.dashboard_quick_actions),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              FilledTonalButton(
                onClick = { viewModel.openQuickTask() },
                modifier = Modifier
                  .weight(1f)
                  .testTag("dashboard_quick_task")
              ) {
                Icon(Icons.Outlined.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                  text = " ${stringResource(R.string.dashboard_quick_add_task)}",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              FilledTonalButton(
                onClick = { viewModel.openQuickEvent(prefs) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("dashboard_quick_event")
              ) {
                Icon(Icons.Outlined.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                  text = " ${stringResource(R.string.dashboard_quick_add_event)}",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              FilledTonalButton(
                onClick = { viewModel.openQuickMemory() },
                modifier = Modifier
                  .weight(1f)
                  .testTag("dashboard_quick_memory")
              ) {
                Icon(Icons.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                  text = " ${stringResource(R.string.dashboard_quick_add_memory)}",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }

          if (state.dueTasks.isEmpty() && state.upcomingEvents.isEmpty()) {
            item {
              Surface(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("dashboard_empty_state"),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(
                    text = stringResource(R.string.dashboard_empty_title),
                    style = MaterialTheme.typography.titleSmall
                  )
                  Text(
                    text = stringResource(R.string.dashboard_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          if (state.upcomingEvents.isNotEmpty()) {
            item {
              SectionHeader(
                title = stringResource(R.string.dashboard_upcoming_events_section),
                onSeeAll = { onNavigate(LifeOsDestination.CALENDAR) }
              )
              Spacer(modifier = Modifier.height(6.dp))
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.upcomingEvents.forEach { ev ->
                  Card(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { onNavigate(LifeOsDestination.CALENDAR) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                  ) {
                    Column(
                      modifier = Modifier.padding(14.dp),
                      verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                      Text(text = ev.title, style = MaterialTheme.typography.titleSmall)
                      val dateText = if (ev.isAllDay) {
                        PersianCalendarEngine.formatShortDate(
                          ev.startAtUtcMillis,
                          ev.zoneId,
                          prefs.calendarSystem,
                          prefs.language
                        ) + " · " + stringResource(R.string.calendar_all_day)
                      } else {
                        PersianCalendarEngine.formatDateTime(
                          ev.startAtUtcMillis,
                          ev.zoneId,
                          prefs.calendarSystem,
                          prefs.language
                        )
                      }
                      Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                      )
                    }
                  }
                }
              }
            }
          }

          if (state.dueTasks.isNotEmpty()) {
            item {
              SectionHeader(
                title = stringResource(R.string.dashboard_due_tasks_section),
                onSeeAll = { onNavigate(LifeOsDestination.TASKS) }
              )
              Spacer(modifier = Modifier.height(6.dp))
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.dueTasks.forEach { task ->
                  Card(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { onNavigate(LifeOsDestination.TASKS) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Checkbox(
                        checked = false,
                        onCheckedChange = { viewModel.toggleTaskComplete(task, prefs) }
                      )
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = task.title,
                          style = MaterialTheme.typography.titleSmall,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        task.dueAtUtcMillis?.let { due ->
                          Text(
                            text = PersianCalendarEngine.formatDateTime(
                              due,
                              prefs.defaultZoneId,
                              prefs.calendarSystem,
                              prefs.language
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          if (state.pinnedMemories.isNotEmpty()) {
            item {
              SectionHeader(
                title = stringResource(R.string.dashboard_pinned_memories_section),
                onSeeAll = { onNavigate(LifeOsDestination.MEMORY) }
              )
              Spacer(modifier = Modifier.height(6.dp))
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.pinnedMemories.forEach { mem ->
                  Card(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { onNavigate(LifeOsDestination.MEMORY) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                  ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                      Text(
                        text = mem.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      if (mem.content.isNotBlank()) {
                        Text(
                          text = mem.content,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          item {
            Text(
              text = stringResource(R.string.dashboard_storage_section),
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                VaultStatCard(
                  label = stringResource(R.string.count_tasks_label),
                  value = PersianCalendarEngine.formatNumber(counts.activeTasksCount, prefs.language),
                  icon = Icons.Outlined.CheckCircle,
                  onClick = { onNavigate(LifeOsDestination.TASKS) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_stat_tasks")
                )
                VaultStatCard(
                  label = stringResource(R.string.count_events_label),
                  value = PersianCalendarEngine.formatNumber(counts.eventsCount, prefs.language),
                  icon = Icons.Outlined.CalendarMonth,
                  onClick = { onNavigate(LifeOsDestination.CALENDAR) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_stat_events")
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                VaultStatCard(
                  label = stringResource(R.string.count_memories_label),
                  value = PersianCalendarEngine.formatNumber(counts.memoriesCount, prefs.language),
                  icon = Icons.Outlined.Lightbulb,
                  onClick = { onNavigate(LifeOsDestination.MEMORY) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_stat_memories")
                )
                VaultStatCard(
                  label = stringResource(R.string.count_documents_label),
                  value = PersianCalendarEngine.formatNumber(counts.documentsCount, prefs.language),
                  icon = Icons.Outlined.Description,
                  onClick = { onNavigate(LifeOsDestination.DOCUMENTS) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_stat_documents")
                )
              }
            }
          }

          item {
            Text(
              text = stringResource(R.string.dashboard_modules_section),
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              DashboardModuleRow(
                title = stringResource(R.string.nav_insights),
                description = stringResource(R.string.desc_insights),
                icon = Icons.Outlined.Insights,
                onClick = { onNavigate(LifeOsDestination.INSIGHTS) },
                testTag = "dashboard_nav_insights"
              )
              DashboardModuleRow(
                title = stringResource(R.string.nav_assistant),
                description = stringResource(R.string.desc_assistant),
                icon = Icons.Outlined.AutoAwesome,
                onClick = { onNavigate(LifeOsDestination.ASSISTANT) },
                testTag = "dashboard_nav_assistant"
              )
            }
          }

          item { Spacer(modifier = Modifier.height(20.dp)) }
        }
      }

      when (val quick = state.controls.quickDraft) {
        is QuickCaptureDraft.Task -> TaskEditorDialog(
          draft = quick.draft,
          preferences = prefs,
          isSaving = state.controls.isSaving,
          onUpdateDraft = { viewModel.updateTaskDraft(it) },
          onSave = { viewModel.saveQuickDraft() },
          onDismiss = { viewModel.dismissQuickDraft() }
        )

        is QuickCaptureDraft.Event -> EventEditorDialog(
          draft = quick.draft,
          preferences = prefs,
          isSaving = state.controls.isSaving,
          onUpdateDraft = { viewModel.updateEventDraft(it) },
          onSave = { viewModel.saveQuickDraft() },
          onDismiss = { viewModel.dismissQuickDraft() }
        )

        is QuickCaptureDraft.Memory -> MemoryEditorDialog(
          draft = quick.draft,
          isSaving = state.controls.isSaving,
          onUpdateDraft = { viewModel.updateMemoryDraft(it) },
          onSave = { viewModel.saveQuickDraft() },
          onDismiss = { viewModel.dismissQuickDraft() }
        )

        null -> Unit
      }
    }
  }
}

@Composable
private fun SectionHeader(
  title: String,
  onSeeAll: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(text = title, style = MaterialTheme.typography.titleMedium)
    TextButton(onClick = onSeeAll) {
      Text(stringResource(R.string.dashboard_see_all))
    }
  }
}

@Composable
private fun VaultStatCard(
  label: String,
  value: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .clip(MaterialTheme.shapes.medium)
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp)
      )
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun DashboardModuleRow(
  title: String,
  description: String,
  icon: ImageVector,
  onClick: () -> Unit,
  testTag: String
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .testTag(testTag),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = stringResource(R.string.cd_module_icon, title),
        tint = MaterialTheme.colorScheme.primary
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
