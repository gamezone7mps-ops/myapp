package com.example.features.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ai.AiProposedAction
import com.example.core.ai.AiTurnOutcome
import com.example.core.ai.AiUnavailableReason
import com.example.core.ai.AssistantGateway
import com.example.core.ai.AssistantMessageTurn
import com.example.core.ai.NetworkStatusProvider
import com.example.core.ai.ToolExecutionResult
import com.example.core.ai.WorkspaceToolExecutor
import com.example.core.datastore.PreferencesRepository
import com.example.domain.model.UserPreferences
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProposalStatus {
  PENDING,
  SAVING,
  CONFIRMED,
  DISCARDED
}

data class AssistantChatMessage(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val timestampUtcMillis: Long,
  val proposedAction: AiProposedAction? = null,
  val proposalStatus: ProposalStatus = ProposalStatus.PENDING,
  val clarificationSuggestions: List<String> = emptyList(),
  val referencedItems: List<String> = emptyList(),
  val unavailableReason: AiUnavailableReason? = null,
  val errorDetail: String? = null,
  val retryPrompt: String? = null
)

private data class AssistantControlState(
  val isOnline: Boolean = true,
  val messages: List<AssistantChatMessage> = emptyList(),
  val inputText: String = "",
  val isProcessing: Boolean = false,
  val streamingChars: Int = 0
)

sealed interface AssistantUiState {
  data object Loading : AssistantUiState
  data class Ready(
    val preferences: UserPreferences,
    val isOnline: Boolean,
    val messages: List<AssistantChatMessage>,
    val inputText: String,
    val isProcessing: Boolean,
    val streamingChars: Int
  ) : AssistantUiState

  data class FatalError(val message: String) : AssistantUiState
}

class AssistantViewModel(
  private val preferencesRepository: PreferencesRepository,
  private val networkStatusProvider: NetworkStatusProvider,
  private val workspaceToolExecutor: WorkspaceToolExecutor,
  private val assistantGateway: AssistantGateway
) : ViewModel() {

  private val controls = MutableStateFlow(
    AssistantControlState(isOnline = networkStatusProvider.isOnline())
  )

  val uiState: StateFlow<AssistantUiState> = combine(
    preferencesRepository.userPreferences,
    controls
  ) { prefs, ctrl ->
    AssistantUiState.Ready(
      preferences = prefs,
      isOnline = ctrl.isOnline,
      messages = ctrl.messages,
      inputText = ctrl.inputText,
      isProcessing = ctrl.isProcessing,
      streamingChars = ctrl.streamingChars
    )
  }
    .catch<AssistantUiState> {
      emit(AssistantUiState.FatalError(it.localizedMessage ?: "Assistant load failed"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = AssistantUiState.Loading
    )

  fun onInputChanged(value: String) {
    controls.update { it.copy(inputText = value) }
  }

  fun refreshConnectivity() {
    controls.update { it.copy(isOnline = networkStatusProvider.isOnline()) }
  }

  fun grantAiConsent() {
    viewModelScope.launch {
      preferencesRepository.setAiDataConsentGranted(true)
      refreshConnectivity()
    }
  }

  fun revokeAiConsent() {
    viewModelScope.launch {
      preferencesRepository.setAiDataConsentGranted(false)
    }
  }

  fun clearConversation() {
    controls.update {
      it.copy(
        messages = emptyList(),
        isProcessing = false,
        streamingChars = 0,
        isOnline = networkStatusProvider.isOnline()
      )
    }
  }

  fun sendMessage(promptOverride: String? = null) {
    val currentCtrl = controls.value
    if (currentCtrl.isProcessing) return

    val prompt = (promptOverride ?: currentCtrl.inputText).trim()
    if (prompt.isEmpty()) return

    val now = System.currentTimeMillis()
    val online = networkStatusProvider.isOnline()

    viewModelScope.launch {
      val prefs = preferencesRepository.userPreferences.first()
      if (!prefs.aiDataConsentGranted) {
        controls.update { state ->
          state.copy(
            isOnline = online,
            messages = state.messages + AssistantChatMessage(
              id = UUID.randomUUID().toString(),
              isUser = false,
              text = "",
              timestampUtcMillis = now,
              unavailableReason = AiUnavailableReason.CONSENT_REQUIRED
            )
          )
        }
        return@launch
      }

      val userMsg = AssistantChatMessage(
        id = UUID.randomUUID().toString(),
        isUser = true,
        text = prompt,
        timestampUtcMillis = now
      )

      if (!online) {
        controls.update { state ->
          state.copy(
            isOnline = false,
            inputText = if (promptOverride == null) "" else state.inputText,
            messages = state.messages + userMsg + AssistantChatMessage(
              id = UUID.randomUUID().toString(),
              isUser = false,
              text = "",
              timestampUtcMillis = now + 1L,
              unavailableReason = AiUnavailableReason.OFFLINE,
              retryPrompt = prompt
            )
          )
        }
        return@launch
      }

      val historyTurns = controls.value.messages
        .filter { it.unavailableReason == null && it.text.isNotBlank() }
        .takeLast(6)
        .map { AssistantMessageTurn(isUser = it.isUser, text = it.text) }

      controls.update { state ->
        state.copy(
          isOnline = true,
          inputText = if (promptOverride == null) "" else state.inputText,
          messages = state.messages + userMsg,
          isProcessing = true,
          streamingChars = 0
        )
      }

      try {
        val snapshot = workspaceToolExecutor.buildContextSnapshot(prompt)
        val outcome = assistantGateway.processTurn(
          userMessage = prompt,
          history = historyTurns,
          workspaceContext = snapshot,
          preferences = prefs,
          currentUtcMillis = System.currentTimeMillis(),
          onStreamingCharCount = { chars ->
            controls.update { it.copy(streamingChars = chars) }
          }
        )

        val responseMsg = when (outcome) {
          is AiTurnOutcome.Reply -> AssistantChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = outcome.replyText,
            timestampUtcMillis = System.currentTimeMillis(),
            referencedItems = outcome.referencedItems
          )

          is AiTurnOutcome.ClarificationNeeded -> AssistantChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = outcome.questionText,
            timestampUtcMillis = System.currentTimeMillis(),
            clarificationSuggestions = outcome.suggestions
          )

          is AiTurnOutcome.Proposal -> AssistantChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = outcome.explanationText,
            timestampUtcMillis = System.currentTimeMillis(),
            proposedAction = outcome.action,
            proposalStatus = ProposalStatus.PENDING,
            referencedItems = outcome.referencedItems
          )

          is AiTurnOutcome.Unavailable -> AssistantChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = "",
            timestampUtcMillis = System.currentTimeMillis(),
            unavailableReason = outcome.reason,
            errorDetail = outcome.detail,
            retryPrompt = prompt
          )
        }

        controls.update { state ->
          state.copy(
            isProcessing = false,
            streamingChars = 0,
            isOnline = networkStatusProvider.isOnline(),
            messages = state.messages + responseMsg
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        controls.update { state ->
          state.copy(
            isProcessing = false,
            streamingChars = 0,
            isOnline = networkStatusProvider.isOnline(),
            messages = state.messages + AssistantChatMessage(
              id = UUID.randomUUID().toString(),
              isUser = false,
              text = "",
              timestampUtcMillis = System.currentTimeMillis(),
              unavailableReason = AiUnavailableReason.SERVICE_ERROR,
              errorDetail = e.localizedMessage,
              retryPrompt = prompt
            )
          )
        }
      }
    }
  }

  fun retryFailedMessage(messageId: String) {
    val msg = controls.value.messages.firstOrNull { it.id == messageId } ?: return
    val retryText = msg.retryPrompt ?: return
    refreshConnectivity()
    sendMessage(promptOverride = retryText)
  }

  fun confirmProposal(messageId: String) {
    val target = controls.value.messages.firstOrNull { it.id == messageId } ?: return
    val action = target.proposedAction ?: return
    if (target.proposalStatus != ProposalStatus.PENDING) return

    updateMessage(messageId) { it.copy(proposalStatus = ProposalStatus.SAVING, errorDetail = null) }

    viewModelScope.launch {
      when (val result = workspaceToolExecutor.executeConfirmedProposal(action)) {
        is ToolExecutionResult.Success -> {
          updateMessage(messageId) {
            it.copy(proposalStatus = ProposalStatus.CONFIRMED, errorDetail = null)
          }
        }

        is ToolExecutionResult.Failure -> {
          updateMessage(messageId) {
            it.copy(proposalStatus = ProposalStatus.PENDING, errorDetail = result.reason)
          }
        }
      }
    }
  }

  fun discardProposal(messageId: String) {
    val target = controls.value.messages.firstOrNull { it.id == messageId } ?: return
    if (target.proposalStatus != ProposalStatus.PENDING) return
    updateMessage(messageId) { it.copy(proposalStatus = ProposalStatus.DISCARDED) }
  }

  private fun updateMessage(
    messageId: String,
    transform: (AssistantChatMessage) -> AssistantChatMessage
  ) {
    controls.update { state ->
      state.copy(
        messages = state.messages.map { msg ->
          if (msg.id == messageId) transform(msg) else msg
        }
      )
    }
  }

  class Factory(
    private val preferencesRepository: PreferencesRepository,
    private val networkStatusProvider: NetworkStatusProvider,
    private val workspaceToolExecutor: WorkspaceToolExecutor,
    private val assistantGateway: AssistantGateway
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return AssistantViewModel(
        preferencesRepository = preferencesRepository,
        networkStatusProvider = networkStatusProvider,
        workspaceToolExecutor = workspaceToolExecutor,
        assistantGateway = assistantGateway
      ) as T
    }
  }
}
