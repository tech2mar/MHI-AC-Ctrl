package de.tech2mar.openwebui.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.tech2mar.openwebui.data.OpenWebUiApi
import de.tech2mar.openwebui.data.SettingsRepository
import de.tech2mar.openwebui.data.StreamEvent
import de.tech2mar.openwebui.data.model.ChatMessage
import de.tech2mar.openwebui.data.model.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.sse.EventSource

data class ChatMessageUi(
    val role: String,
    val content: String,
    val isStreaming: Boolean = false
)

data class ChatUiState(
    val models: List<ModelInfo> = emptyList(),
    val selectedModel: String = "",
    val isLoadingModels: Boolean = false,
    val messages: List<ChatMessageUi> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val repository: SettingsRepository,
    private val api: OpenWebUiApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var serverUrl: String = ""
    private var apiKey: String = ""
    private var activeEventSource: EventSource? = null

    init {
        viewModelScope.launch {
            val settings = repository.settings.first()
            serverUrl = settings.serverUrl
            apiKey = settings.apiKey
            loadModels(preferredModel = settings.selectedModel)
        }
    }

    private fun loadModels(preferredModel: String) {
        _uiState.update { it.copy(isLoadingModels = true, error = null) }
        viewModelScope.launch {
            api.fetchModels(serverUrl, apiKey)
                .onSuccess { models ->
                    val selected = when {
                        models.any { it.id == preferredModel } -> preferredModel
                        models.isNotEmpty() -> models.first().id
                        else -> ""
                    }
                    _uiState.update { it.copy(models = models, selectedModel = selected, isLoadingModels = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoadingModels = false, error = "Modelle konnten nicht geladen werden: ${error.message}")
                    }
                }
        }
    }

    fun onModelSelected(modelId: String) {
        _uiState.update { it.copy(selectedModel = modelId) }
        viewModelScope.launch { repository.saveSelectedModel(modelId) }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.input.trim()
        if (text.isEmpty() || state.isSending || state.selectedModel.isBlank()) return

        val userMessage = ChatMessageUi(role = "user", content = text)
        val requestHistory = (state.messages + userMessage).map { ChatMessage(it.role, it.content) }
        val assistantPlaceholder = ChatMessageUi(role = "assistant", content = "", isStreaming = true)

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + assistantPlaceholder,
                input = "",
                isSending = true,
                error = null
            )
        }

        activeEventSource?.cancel()
        activeEventSource = api.streamChatCompletion(
            serverUrl = serverUrl,
            apiKey = apiKey,
            model = state.selectedModel,
            messages = requestHistory,
            onEvent = ::handleStreamEvent
        )
    }

    private fun handleStreamEvent(event: StreamEvent) {
        when (event) {
            is StreamEvent.Delta -> _uiState.update { state ->
                val messages = state.messages.toMutableList()
                val lastIndex = messages.lastIndex
                if (lastIndex >= 0 && messages[lastIndex].isStreaming) {
                    messages[lastIndex] = messages[lastIndex].copy(content = messages[lastIndex].content + event.text)
                }
                state.copy(messages = messages)
            }

            StreamEvent.Done -> {
                _uiState.update { state ->
                    val messages = state.messages.toMutableList()
                    val lastIndex = messages.lastIndex
                    if (lastIndex >= 0 && messages[lastIndex].isStreaming) {
                        messages[lastIndex] = messages[lastIndex].copy(isStreaming = false)
                    }
                    state.copy(messages = messages, isSending = false)
                }
                activeEventSource = null
            }

            is StreamEvent.Error -> {
                _uiState.update { state ->
                    val messages = state.messages.toMutableList()
                    val lastIndex = messages.lastIndex
                    if (lastIndex >= 0 && messages[lastIndex].isStreaming) {
                        messages.removeAt(lastIndex)
                    }
                    state.copy(
                        messages = messages,
                        isSending = false,
                        error = event.throwable.message ?: "Streaming-Fehler."
                    )
                }
                activeEventSource = null
            }
        }
    }

    override fun onCleared() {
        activeEventSource?.cancel()
        super.onCleared()
    }

    class Factory(
        private val repository: SettingsRepository,
        private val api: OpenWebUiApi
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repository, api) as T
        }
    }
}
