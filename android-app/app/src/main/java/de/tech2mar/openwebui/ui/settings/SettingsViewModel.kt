package de.tech2mar.openwebui.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.tech2mar.openwebui.data.OpenWebUiApi
import de.tech2mar.openwebui.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TestResult {
    data object Idle : TestResult
    data class Success(val modelCount: Int) : TestResult
    data class Failure(val message: String) : TestResult
}

data class SettingsUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isTesting: Boolean = false,
    val testResult: TestResult = TestResult.Idle
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val api: OpenWebUiApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val current = repository.settings.first()
            _uiState.update { it.copy(serverUrl = current.serverUrl, apiKey = current.apiKey) }
        }
    }

    fun onServerUrlChange(value: String) {
        _uiState.update { it.copy(serverUrl = value, testResult = TestResult.Idle) }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value, testResult = TestResult.Idle) }
    }

    fun testAndSave(onSaved: () -> Unit) {
        val state = _uiState.value
        val normalizedUrl = normalizeUrl(state.serverUrl)

        if (normalizedUrl.isBlank() || state.apiKey.isBlank()) {
            _uiState.update { it.copy(testResult = TestResult.Failure("Bitte Server-URL und API-Key eingeben.")) }
            return
        }

        _uiState.update { it.copy(isTesting = true, testResult = TestResult.Idle, serverUrl = normalizedUrl) }
        viewModelScope.launch {
            api.fetchModels(normalizedUrl, state.apiKey)
                .onSuccess { models ->
                    repository.saveConnection(normalizedUrl, state.apiKey)
                    _uiState.update { it.copy(isTesting = false, testResult = TestResult.Success(models.size)) }
                    onSaved()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isTesting = false, testResult = TestResult.Failure(error.message ?: "Verbindung fehlgeschlagen."))
                    }
                }
        }
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
    }

    class Factory(
        private val repository: SettingsRepository,
        private val api: OpenWebUiApi
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository, api) as T
        }
    }
}
