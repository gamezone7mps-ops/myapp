package com.example.features.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.calendar.PersianCalendarEngine
import com.example.domain.model.RecurrenceRule
import com.example.domain.model.TaskItem
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus
import com.example.domain.model.UserPreferences
import com.example.features.common.ConfirmDeleteDialog

@Composable
fun TasksScreen(
  viewModel: TasksViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  when (val state = uiState) {
    TasksUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("tasks_loading"))
      }
    }

    is TasksUiState.Error -> {
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

    is TasksUiState.Success -> {
      val controls = state.controls
      val nextOccurrenceMessage = stringResource(R.string.tasks_msg_next_occurrence)
      val saveErrorMessage = stringResource(R.string.error_save_failed)

      LaunchedEffect(controls.snackbarEvent) {
        val ev = controls.snackbarEvent ?: return@LaunchedEffect
        val text = if (ev == "next_occurrence") nextOccurrenceMessage else saveErrorMessage
        snackbarHostState.showSnackbar(text)
        viewModel.consumeSnackbarEvent()
      }

      Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
          FloatingActionButton(
            onClick = { viewModel.openNewTaskEditor() },
            modifier = Modifier.testTag("tasks_add_fab")
          ) {
            Icon(
              imageVector = Icons.Filled.Add,
              contentDescription = stringResource(R.string.cd_add_task)
            )
          }
        }
      ) { innerPadding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
          contentAlignment = Alignment.TopCenter
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .widthIn(max = 720.dp)
              .padding(horizontal = 16.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedTextField(
                value = controls.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text(stringResource(R.string.tasks_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                  if (controls.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                      Icon(
                        Icons.Outlined.Clear,
                        contentDescription = stringResource(R.string.cd_clear_search)
                      )
                    }
                  }
                },
                singleLine = true,
                modifier = Modifier
                  .weight(1f)
                  .testTag("tasks_search_input")
              )

              var showSortMenu by remember { mutableStateOf(false) }
              Box {
                IconButton(
                  onClick = { showSortMenu = true },
                  modifier = Modifier.testTag("tasks_sort_button")
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Sort,
                    contentDescription = sortLabel(controls.sort)
                  )
                }
                DropdownMenu(
                  expanded = showSortMenu,
                  onDismissRequest = { showSortMenu = false }
                ) {
                  TaskSort.entries.forEach { sort ->
                    DropdownMenuItem(
                      text = { Text(sortLabel(sort)) },
                      onClick = {
                        viewModel.onSortSelected(sort)
                        showSortMenu = false
                      }
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              TaskFilter.entries.forEach { filter ->
                FilterChip(
                  selected = controls.filter == filter,
                  onClick = { viewModel.onFilterSelected(filter) },
                  label = { Text(filterLabel(filter)) },
                  modifier = Modifier.testTag("tasks_filter_${filter.name.lowercase()}")
                )
              }
              state.availableCategories.forEach { category ->
                FilterChip(
                  selected = controls.selectedCategory == category,
                  onClick = {
                    viewModel.onCategorySelected(
                      if (controls.selectedCategory == category) null else category
                    )
                  },
                  label = { Text(category) }
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.visibleTasks.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(24.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Outlined.CheckCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text = stringResource(R.string.tasks_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("tasks_empty_title")
                  )
                  Text(
                    text = stringResource(R.string.tasks_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            } else {
              LazyColumn(
                modifier = Modifier
                  .fillMaxSize()
                  .testTag("tasks_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                items(state.visibleTasks, key = { it.id }) { task ->
                  TaskRowCard(
                    task = task,
                    preferences = state.preferences,
                    onToggleComplete = { viewModel.toggleTaskComplete(task, state.preferences) },
                    onEdit = { viewModel.openEditTaskEditor(task) },
                    onArchiveToggle = {
                      if (task.status == TaskStatus.ARCHIVED) viewModel.restoreTask(task)
                      else viewModel.archiveTask(task)
                    },
                    onDelete = { viewModel.requestDelete(task) }
                  )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
              }
            }
          }
        }
      }

      controls.editorDraft?.let { draft ->
        TaskEditorDialog(
          draft = draft,
          preferences = state.preferences,
          isSaving = controls.isSaving,
          onUpdateDraft = { viewModel.updateDraft(it) },
          onSave = { viewModel.saveDraft() },
          onDismiss = { viewModel.dismissEditor() }
        )
      }

      controls.pendingDeleteTask?.let { pending ->
        ConfirmDeleteDialog(
          itemTitle = pending.title,
          onConfirm = { viewModel.confirmDelete() },
          onDismiss = { viewModel.cancelDelete() }
        )
      }
    }
  }
}

@Composable
private fun TaskRowCard(
  task: TaskItem,
  preferences: UserPreferences,
  onToggleComplete: () -> Unit,
  onEdit: () -> Unit,
  onArchiveToggle: () -> Unit,
  onDelete: () -> Unit
) {
  val isCompleted = task.status == TaskStatus.COMPLETED
  val isOverdue = !isCompleted && task.status != TaskStatus.ARCHIVED &&
    task.dueAtUtcMillis != null && task.dueAtUtcMillis < System.currentTimeMillis()
  val recurrence = RecurrenceRule.fromKey(task.recurrenceRule)

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onEdit)
      .testTag("task_card_${task.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Checkbox(
        checked = isCompleted,
        onCheckedChange = { onToggleComplete() },
        modifier = Modifier.testTag("task_checkbox_${task.id}")
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = task.title,
          style = MaterialTheme.typography.titleSmall.copy(
            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
          ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )

        if (task.description.isNotBlank()) {
          Text(
            text = task.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          PriorityBadge(priority = task.priority)

          if (task.category != null) {
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = MaterialTheme.shapes.small
            ) {
              Text(
                text = task.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
              )
            }
          }

          if (task.dueAtUtcMillis != null) {
            val formattedDue = PersianCalendarEngine.formatDateTime(
              utcMillis = task.dueAtUtcMillis,
              zoneId = preferences.defaultZoneId,
              calendarSystem = preferences.calendarSystem,
              language = preferences.language
            )
            Surface(
              color = if (isOverdue) MaterialTheme.colorScheme.errorContainer
              else MaterialTheme.colorScheme.surfaceContainerLow,
              shape = MaterialTheme.shapes.small
            ) {
              Text(
                text = if (isOverdue) {
                  "$formattedDue · ${stringResource(R.string.tasks_overdue_badge)}"
                } else {
                  formattedDue
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
              )
            }
          }

          if (recurrence != RecurrenceRule.NONE) {
            Icon(
              imageVector = Icons.Outlined.Repeat,
              contentDescription = recurrenceLabel(recurrence),
              tint = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      IconButton(
        onClick = onArchiveToggle,
        modifier = Modifier.testTag("task_archive_${task.id}")
      ) {
        Icon(
          imageVector = if (task.status == TaskStatus.ARCHIVED) {
            Icons.Outlined.Unarchive
          } else {
            Icons.Outlined.Archive
          },
          contentDescription = stringResource(
            if (task.status == TaskStatus.ARCHIVED) R.string.action_restore
            else R.string.action_archive
          )
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.testTag("task_delete_${task.id}")
      ) {
        Icon(
          imageVector = Icons.Outlined.DeleteOutline,
          contentDescription = stringResource(R.string.cd_delete_item, task.title),
          tint = MaterialTheme.colorScheme.error
        )
      }
    }
  }
}

@Composable
private fun PriorityBadge(priority: TaskPriority) {
  val container = when (priority) {
    TaskPriority.LOW -> MaterialTheme.colorScheme.surfaceContainerLow
    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primaryContainer
    TaskPriority.HIGH -> MaterialTheme.colorScheme.tertiaryContainer
    TaskPriority.URGENT -> MaterialTheme.colorScheme.errorContainer
  }
  val content = when (priority) {
    TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.onPrimaryContainer
    TaskPriority.HIGH -> MaterialTheme.colorScheme.onTertiaryContainer
    TaskPriority.URGENT -> MaterialTheme.colorScheme.onErrorContainer
  }
  Surface(color = container, shape = MaterialTheme.shapes.small) {
    Text(
      text = priorityLabel(priority),
      style = MaterialTheme.typography.labelSmall,
      color = content,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    )
  }
}

@Composable
private fun filterLabel(filter: TaskFilter): String = stringResource(
  when (filter) {
    TaskFilter.ACTIVE -> R.string.tasks_filter_active
    TaskFilter.COMPLETED -> R.string.tasks_filter_completed
    TaskFilter.ARCHIVED -> R.string.tasks_filter_archived
    TaskFilter.ALL -> R.string.tasks_filter_all
  }
)

@Composable
private fun sortLabel(sort: TaskSort): String = stringResource(
  when (sort) {
    TaskSort.DUE_DATE -> R.string.tasks_sort_due
    TaskSort.PRIORITY -> R.string.tasks_sort_priority
    TaskSort.UPDATED -> R.string.tasks_sort_updated
  }
)
