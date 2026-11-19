package com.example.features.assistant

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ai.AiUnavailableReason
import com.example.domain.model.UserPreferences

@Composable
fun AssistantScreen(
  viewModel: AssistantViewModel,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  when (val state = uiState) {
    AssistantUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("assistant_loading"))
      }
    }

    is AssistantUiState.FatalError -> {
      Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = state.message,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.error
        )
      }
    }

    is AssistantUiState.Ready -> {
      AssistantReadyContent(
        state = state,
        onInputChanged = viewModel::onInputChanged,
        onSend = { viewModel.sendMessage() },
        onQuickPrompt = { viewModel.sendMessage(it) },
        onGrantConsent = viewModel::grantAiConsent,
        onRefreshConnectivity = viewModel::refreshConnectivity,
        onClearConversation = viewModel::clearConversation,
        onConfirmProposal = viewModel::confirmProposal,
        onDiscardProposal = viewModel::discardProposal,
        onRetryMessage = viewModel::retryFailedMessage,
        onOpenSettings = onOpenSettings,
        modifier = modifier
      )
    }
  }
}

@Composable
private fun AssistantReadyContent(
  state: AssistantUiState.Ready,
  onInputChanged: (String) -> Unit,
  onSend: () -> Unit,
  onQuickPrompt: (String) -> Unit,
  onGrantConsent: () -> Unit,
  onRefreshConnectivity: () -> Unit,
  onClearConversation: () -> Unit,
  onConfirmProposal: (String) -> Unit,
  onDiscardProposal: (String) -> Unit,
  onRetryMessage: (String) -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()
  val lifecycleOwner = LocalLifecycleOwner.current

  LaunchedEffect(Unit) {
    onRefreshConnectivity()
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        onRefreshConnectivity()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(state.messages.size) {
    if (state.messages.isNotEmpty()) {
      listState.scrollToItem(state.messages.lastIndex)
    }
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 760.dp)
        .padding(horizontal = 16.dp)
    ) {
      AssistantStatusHeader(
        isOnline = state.isOnline,
        consentGranted = state.preferences.aiDataConsentGranted,
        hasMessages = state.messages.isNotEmpty(),
        onRefreshConnectivity = onRefreshConnectivity,
        onClearConversation = onClearConversation
      )

      Spacer(modifier = Modifier.height(8.dp))

      if (!state.preferences.aiDataConsentGranted) {
        ConsentRequiredCard(
          onGrantConsent = onGrantConsent,
          onOpenSettings = onOpenSettings
        )
        Spacer(modifier = Modifier.height(10.dp))
      }

      if (!state.isOnline) {
        OfflineWarningBanner(onRetryConnection = onRefreshConnectivity)
        Spacer(modifier = Modifier.height(8.dp))
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        if (state.messages.isEmpty()) {
          AssistantEmptyState(
            consentGranted = state.preferences.aiDataConsentGranted,
            isOnline = state.isOnline,
            onQuickPrompt = onQuickPrompt
          )
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxSize()
              .testTag("assistant_messages_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(state.messages, key = { it.id }) { msg ->
              AssistantMessageItem(
                message = msg,
                preferences = state.preferences,
                onConfirmProposal = { onConfirmProposal(msg.id) },
                onDiscardProposal = { onDiscardProposal(msg.id) },
                onSuggestionClick = onQuickPrompt,
                onRetry = { onRetryMessage(msg.id) },
                onGrantConsent = onGrantConsent
              )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
          }
        }
      }

      if (state.isProcessing) {
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("assistant_loading_indicator")
        )
        Text(
          text = if (state.streamingChars > 0) {
            stringResource(R.string.assistant_streaming_status, state.streamingChars)
          } else {
            stringResource(R.string.assistant_thinking_status)
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 4.dp)
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = state.inputText,
          onValueChange = onInputChanged,
          placeholder = {
            Text(
              stringResource(
                if (state.preferences.aiDataConsentGranted) {
                  R.string.assistant_input_hint
                } else {
                  R.string.assistant_input_hint_consent_needed
                }
              )
            )
          },
          enabled = !state.isProcessing,
          maxLines = 4,
          modifier = Modifier
            .weight(1f)
            .testTag("assistant_input")
        )
        FilledIconButton(
          onClick = onSend,
          enabled = !state.isProcessing && state.inputText.isNotBlank(),
          modifier = Modifier
            .size(48.dp)
            .testTag("assistant_send_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = stringResource(R.string.assistant_cd_send)
          )
        }
      }
    }
  }
}

@Composable
private fun AssistantStatusHeader(
  isOnline: Boolean,
  consentGranted: Boolean,
  hasMessages: Boolean,
  onRefreshConnectivity: () -> Unit,
  onClearConversation: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Surface(
      color = if (isOnline) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.errorContainer
      },
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.testTag("assistant_status_badge")
    ) {
      Text(
        text = stringResource(
          if (isOnline) R.string.assistant_status_online else R.string.assistant_status_offline
        ),
        style = MaterialTheme.typography.labelSmall,
        color = if (isOnline) {
          MaterialTheme.colorScheme.onPrimaryContainer
        } else {
          MaterialTheme.colorScheme.onErrorContainer
        },
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
      )
    }

    Surface(
      color = if (consentGranted) {
        MaterialTheme.colorScheme.secondaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
      },
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.testTag("assistant_consent_badge")
    ) {
      Text(
        text = stringResource(
          if (consentGranted) R.string.assistant_consent_active else R.string.assistant_consent_off
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    IconButton(
      onClick = onRefreshConnectivity,
      modifier = Modifier.testTag("assistant_refresh_status_button")
    ) {
      Icon(
        imageVector = Icons.Outlined.Refresh,
        contentDescription = stringResource(R.string.assistant_cd_refresh_status)
      )
    }

    if (hasMessages) {
      IconButton(
        onClick = onClearConversation,
        modifier = Modifier.testTag("assistant_clear_button")
      ) {
        Icon(
          imageVector = Icons.Outlined.DeleteSweep,
          contentDescription = stringResource(R.string.assistant_cd_clear_chat)
        )
      }
    }
  }
}

@Composable
private fun ConsentRequiredCard(
  onGrantConsent: () -> Unit,
  onOpenSettings: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("assistant_consent_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Outlined.Shield,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
        Text(
          text = stringResource(R.string.assistant_consent_title),
          style = MaterialTheme.typography.titleMedium
        )
      }
      Text(
        text = stringResource(R.string.assistant_consent_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = onGrantConsent,
          modifier = Modifier
            .weight(1f)
            .testTag("assistant_grant_consent_button")
        ) {
          Text(stringResource(R.string.assistant_consent_enable_action))
        }
        OutlinedButton(
          onClick = onOpenSettings,
          modifier = Modifier.testTag("assistant_consent_settings_button")
        ) {
          Text(stringResource(R.string.action_open_settings))
        }
      }
    }
  }
}

@Composable
private fun OfflineWarningBanner(onRetryConnection: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.errorContainer,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("assistant_offline_banner")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = Icons.Outlined.CloudOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onErrorContainer
      )
      Text(
        text = stringResource(R.string.assistant_offline_banner_text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.weight(1f)
      )
      TextButton(
        onClick = onRetryConnection,
        modifier = Modifier.testTag("assistant_retry_connection_button")
      ) {
        Text(stringResource(R.string.action_retry))
      }
    }
  }
}

@Composable
private fun AssistantEmptyState(
  consentGranted: Boolean,
  isOnline: Boolean,
  onQuickPrompt: (String) -> Unit
) {
  val sampleExam = stringResource(R.string.assistant_sample_exam)
  val sampleTasks = stringResource(R.string.assistant_sample_tasks)
  val sampleMemory = stringResource(R.string.assistant_sample_memory)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = Icons.Outlined.AutoAwesome,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp)
      )
      Text(
        text = stringResource(R.string.assistant_empty_title),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("assistant_empty_title")
      )
      Text(
        text = stringResource(R.string.assistant_empty_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      if (consentGranted && isOnline) {
        Spacer(modifier = Modifier.height(6.dp))
        SuggestionChip(
          onClick = { onQuickPrompt(sampleExam) },
          label = { Text(sampleExam) },
          modifier = Modifier.testTag("assistant_quick_prompt_exam")
        )
        SuggestionChip(
          onClick = { onQuickPrompt(sampleTasks) },
          label = { Text(sampleTasks) }
        )
        SuggestionChip(
          onClick = { onQuickPrompt(sampleMemory) },
          label = { Text(sampleMemory) }
        )
      }
    }
  }
}

@Composable
private fun AssistantMessageItem(
  message: AssistantChatMessage,
  preferences: UserPreferences,
  onConfirmProposal: () -> Unit,
  onDiscardProposal: () -> Unit,
  onSuggestionClick: (String) -> Unit,
  onRetry: () -> Unit,
  onGrantConsent: () -> Unit
) {
  if (message.isUser) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.widthIn(max = 320.dp)
      ) {
        Text(
          text = message.text,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
      }
    }
    return
  }

  if (message.unavailableReason != null) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("assistant_unavailable_card_${message.id}"),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
      )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer
          )
          Text(
            text = stringResource(
              when (message.unavailableReason) {
                AiUnavailableReason.CONSENT_REQUIRED -> R.string.assistant_error_consent_required
                AiUnavailableReason.OFFLINE -> R.string.assistant_error_offline
                AiUnavailableReason.TIMEOUT -> R.string.assistant_error_timeout
                AiUnavailableReason.SERVICE_ERROR -> R.string.assistant_error_service
              }
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onErrorContainer
          )
        }
        if (!message.errorDetail.isNullOrBlank()) {
          Text(
            text = message.errorDetail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
          )
        }
        if (message.unavailableReason == AiUnavailableReason.CONSENT_REQUIRED) {
          OutlinedButton(onClick = onGrantConsent) {
            Text(stringResource(R.string.assistant_consent_enable_action))
          }
        } else if (message.retryPrompt != null) {
          OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.testTag("assistant_retry_message_${message.id}")
          ) {
            Text(stringResource(R.string.action_retry))
          }
        }
      }
    }
    return
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = message.text,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface
        )

        if (message.referencedItems.isNotEmpty()) {
          Text(
            text = stringResource(
              R.string.assistant_referenced_items,
              message.referencedItems.joinToString(" · ")
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
          )
        }

        if (message.clarificationSuggestions.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            message.clarificationSuggestions.forEachIndexed { idx, suggestion ->
              SuggestionChip(
                onClick = { onSuggestionClick(suggestion) },
                label = { Text(suggestion) },
                modifier = Modifier.testTag("assistant_suggestion_${message.id}_$idx")
              )
            }
          }
        }
      }
    }

    message.proposedAction?.let { action ->
      AssistantProposalCard(
        messageId = message.id,
        action = action,
        status = message.proposalStatus,
        errorDetail = message.errorDetail,
        preferences = preferences,
        onConfirm = onConfirmProposal,
        onDiscard = onDiscardProposal
      )
    }
  }
}
