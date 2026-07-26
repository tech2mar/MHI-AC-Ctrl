package de.tech2mar.openwebui.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class ConnectionSettings(
    val serverUrl: String = "",
    val apiKey: String = "",
    val selectedModel: String = ""
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && apiKey.isNotBlank()
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val API_KEY = stringPreferencesKey("api_key")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
    }

    val settings: Flow<ConnectionSettings> = context.dataStore.data.map { prefs ->
        ConnectionSettings(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            apiKey = prefs[Keys.API_KEY] ?: "",
            selectedModel = prefs[Keys.SELECTED_MODEL] ?: ""
        )
    }

    suspend fun saveConnection(serverUrl: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = serverUrl.trimEnd('/')
            prefs[Keys.API_KEY] = apiKey
        }
    }

    suspend fun saveSelectedModel(modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_MODEL] = modelId
        }
    }
}
