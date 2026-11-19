package com.example.features.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.calendar.PersianCalendarEngine
import com.example.domain.model.TaskPriority
import com.example.features.memory.memoryTypeLabel
import com.example.features.tasks.priorityLabel

@Composable
fun InsightsScreen(
  viewModel: InsightsViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  when (val state = uiState) {
    InsightsUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("insights_loading"))
      }
    }

    is InsightsUiState.Error -> {
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

    is InsightsUiState.Success -> {
      val prefs = state.preferences
      val summary = state.summary

      if (!summary.hasAnyData) {
        Box(
          modifier = modifier.fillMaxSize().padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.Insights,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
            Text(
              text = stringResource(R.string.insights_empty_title),
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.testTag("insights_empty_title")
            )
            Text(
              text = stringResource(R.string.insights_empty_body),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }
        return
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
            .testTag("insights_list"),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item { Spacer(modifier = Modifier.height(4.dp)) }

          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("insights_task_summary_card"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Text(
                  text = stringResource(R.string.insights_task_summary_title),
                  style = MaterialTheme.typography.titleMedium
                )
                Text(
                  text = stringResource(
                    R.string.insights_completion_rate,
                    PersianCalendarEngine.formatNumber(summary.completionRatePercent, prefs.language)
                  ),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                  progress = { summary.completionRatePercent / 100f },
                  modifier = Modifier.fillMaxWidth()
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  MetricPill(
                    label = stringResource(R.string.insights_total_tasks),
                    value = PersianCalendarEngine.formatNumber(summary.totalTasks, prefs.language)
                  )
                  MetricPill(
                    label = stringResource(R.string.insights_completed_tasks),
                    value = PersianCalendarEngine.formatNumber(summary.completedTasks, prefs.language)
                  )
                  MetricPill(
                    label = stringResource(R.string.insights_pending_tasks),
                    value = PersianCalendarEngine.formatNumber(summary.activeTasks, prefs.language)
                  )
                  MetricPill(
                    label = stringResource(R.string.insights_overdue_tasks),
                    value = PersianCalendarEngine.formatNumber(summary.overdueTasks, prefs.language)
                  )
                }
              }
            }
          }

          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = stringResource(R.string.insights_priority_breakdown),
                  style = MaterialTheme.typography.titleMedium
                )
                val maxActive = summary.activeTasks.coerceAtLeast(1)
                TaskPriority.entries.reversed().forEach { priority ->
                  val count = summary.activeByPriority[priority] ?: 0
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Text(
                      text = priorityLabel(priority),
                      style = MaterialTheme.typography.bodySmall,
                      modifier = Modifier.weight(0.3f)
                    )
                    LinearProgressIndicator(
                      progress = { count.toFloat() / maxActive.toFloat() },
                      modifier = Modifier.weight(0.55f)
                    )
                    Text(
                      text = PersianCalendarEngine.formatNumber(count, prefs.language),
                      style = MaterialTheme.typography.labelMedium,
                      modifier = Modifier.weight(0.15f),
                      textAlign = TextAlign.End
                    )
                  }
                }
              }
            }
          }

          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = stringResource(R.string.insights_upcoming_7days),
                  style = MaterialTheme.typography.titleMedium
                )
                summary.upcomingSevenDays.forEach { day ->
                  val dateStr = PersianCalendarEngine.formatFullDate(
                    utcMillis = day.dayStartUtcMillis,
                    zoneId = prefs.defaultZoneId,
                    calendarSystem = prefs.calendarSystem,
                    language = prefs.language
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = dateStr,
                      style = MaterialTheme.typography.bodySmall,
                      modifier = Modifier.weight(1f)
                    )
                    Text(
                      text = "${stringResource(R.string.insights_upcoming_events_count, PersianCalendarEngine.formatNumber(day.eventsCount, prefs.language))} · ${stringResource(R.string.insights_upcoming_tasks_count, PersianCalendarEngine.formatNumber(day.dueTasksCount, prefs.language))}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary
                    )
                  }
                }
              }
            }
          }

          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = stringResource(R.string.insights_knowledge_title),
                  style = MaterialTheme.typography.titleMedium
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  MetricPill(
                    label = stringResource(R.string.count_events_label),
                    value = PersianCalendarEngine.formatNumber(summary.totalEvents, prefs.language)
                  )
                  MetricPill(
                    label = stringResource(R.string.count_memories_label),
                    value = PersianCalendarEngine.formatNumber(summary.totalMemories, prefs.language)
                  )
                  MetricPill(
                    label = stringResource(R.string.count_documents_label),
                    value = PersianCalendarEngine.formatNumber(summary.totalDocuments, prefs.language)
                  )
                }
                if (summary.totalMemories > 0) {
                  summary.memoriesByType.filterValues { it > 0 }.forEach { (type, count) ->
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(text = memoryTypeLabel(type), style = MaterialTheme.typography.bodySmall)
                      Text(
                        text = PersianCalendarEngine.formatNumber(count, prefs.language),
                        style = MaterialTheme.typography.labelMedium
                      )
                    }
                  }
                }
              }
            }
          }

          item {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = stringResource(R.string.insights_data_honesty_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
              )
            }
          }

          item { Spacer(modifier = Modifier.height(20.dp)) }
        }
      }
    }
  }
}

@Composable
private fun MetricPill(
  label: String,
  value: String
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
