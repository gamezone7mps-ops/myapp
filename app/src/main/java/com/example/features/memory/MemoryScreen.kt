package com.example.features.memory

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.common.InputValidators
import com.example.domain.model.MemoryEntry
import com.example.domain.model.MemoryEntryType
import com.example.domain.model.UserPreferences
import com.example.features.common.ConfirmDeleteDialog

@Composable
fun MemoryScreen(
  viewModel: MemoryViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  when (val state = uiState) {
    MemoryUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("memory_loading"))
      }
    }

    is MemoryUiState.Error -> {
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

    is MemoryUiState.Success -> {
      val controls = state.controls

      LaunchedEffect(controls.snackbarError) {
        val err = controls.snackbarError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.consumeError()
      }

      Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
          FloatingActionButton(
            onClick = { viewModel.openNewMemoryEditor() },
            modifier = Modifier.testTag("memory_add_fab")
          ) {
            Icon(
              imageVector = Icons.Filled.Add,
              contentDescription = stringResource(R.string.cd_add_memory)
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
            OutlinedTextField(
              value = controls.searchQuery,
              onValueChange = { viewModel.onSearchQueryChanged(it) },
              placeholder = { Text(stringResource(R.string.memory_search_hint)) },
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
                .fillMaxWidth()
                .testTag("memory_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              FilterChip(
                selected = controls.selectedType == null,
                onClick = { viewModel.onTypeSelected(null) },
                label = { Text(stringResource(R.string.memory_filter_all)) },
                modifier = Modifier.testTag("memory_filter_all")
              )
              MemoryEntryType.entries.forEach { type ->
                FilterChip(
                  selected = controls.selectedType == type,
                  onClick = {
                    viewModel.onTypeSelected(if (controls.selectedType == type) null else type)
                  },
                  label = { Text(memoryTypeLabel(type)) },
                  modifier = Modifier.testTag("memory_filter_${type.name.lowercase()}")
                )
              }
            }

            if (state.availableTags.isNotEmpty()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                state.availableTags.forEach { tag ->
                  FilterChip(
                    selected = controls.selectedTag == tag,
                    onClick = {
                      viewModel.onTagSelected(if (controls.selectedTag == tag) null else tag)
                    },
                    label = { Text("#$tag") }
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.visibleMemories.isEmpty()) {
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
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text = stringResource(R.string.memory_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("memory_empty_title")
                  )
                  Text(
                    text = stringResource(R.string.memory_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                  )
                }
              }
            } else {
              LazyColumn(
                modifier = Modifier
                  .fillMaxSize()
                  .testTag("memory_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                items(state.visibleMemories, key = { it.id }) { entry ->
                  MemoryCard(
                    entry = entry,
                    preferences = state.preferences,
                    onEdit = { viewModel.openEditMemoryEditor(entry) },
                    onTogglePin = { viewModel.togglePin(entry) },
                    onDelete = { viewModel.requestDelete(entry) }
                  )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
              }
            }
          }
        }
      }

      controls.editorDraft?.let { draft ->
        MemoryEditorDialog(
          draft = draft,
          isSaving = controls.isSaving,
          onUpdateDraft = { viewModel.updateDraft(it) },
          onSave = { viewModel.saveDraft() },
          onDismiss = { viewModel.dismissEditor() }
        )
      }

      controls.pendingDeleteMemory?.let { pending ->
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
private fun MemoryCard(
  entry: MemoryEntry,
  preferences: UserPreferences,
  onEdit: () -> Unit,
  onTogglePin: () -> Unit,
  onDelete: () -> Unit
) {
  val context = LocalContext.current

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onEdit)
      .testTag("memory_card_${entry.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = MaterialTheme.shapes.small
        ) {
          Text(
            text = memoryTypeLabel(entry.entryType),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
          )
        }
        Text(
          text = PersianCalendarEngine.formatShortDate(
            entry.updatedAtUtcMillis,
            preferences.defaultZoneId,
            preferences.calendarSystem,
            preferences.language
          ),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f)
        )
        IconButton(
          onClick = onTogglePin,
          modifier = Modifier.testTag("memory_pin_${entry.id}")
        ) {
          Icon(
            imageVector = if (entry.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            contentDescription = stringResource(R.string.cd_pin_memory, entry.title),
            tint = if (entry.isPinned) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier.testTag("memory_delete_${entry.id}")
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete_item, entry.title),
            tint = MaterialTheme.colorScheme.error
          )
        }
      }

      if (entry.title.isNotBlank()) {
        Text(
          text = entry.title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }

      if (entry.content.isNotBlank()) {
        Text(
          text = entry.content,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 4,
          overflow = TextOverflow.Ellipsis
        )
      }

      if (entry.tags.isNotEmpty()) {
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          entry.tags.forEach { tag ->
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = MaterialTheme.shapes.small
            ) {
              Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
              )
            }
          }
        }
      }

      val url = entry.sourceUrl
      if (!url.isNullOrBlank() && InputValidators.isValidHttpUrl(url)) {
        TextButton(
          onClick = {
            runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
          }
        ) {
          Icon(
            imageVector = Icons.Outlined.OpenInNew,
            contentDescription = null
          )
          Text(
            text = " ${stringResource(R.string.action_open_link)}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}
