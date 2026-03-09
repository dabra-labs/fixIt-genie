package ai.fixitbuddy.app.features.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.fixitbuddy.app.core.config.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

enum class ConnectionTestState {
    Idle, Testing, Success, Failed
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    val backendUrl = dataStore.data
        .map { prefs -> prefs[BACKEND_URL_KEY] ?: AppConfig.BACKEND_URL }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig.BACKEND_URL)

    private val _connectionTestState = MutableStateFlow(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState

    private val _connectionTestMessage = MutableStateFlow("")
    val connectionTestMessage: StateFlow<String> = _connectionTestMessage

    fun saveBackendUrl(url: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[BACKEND_URL_KEY] = url }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            val url = backendUrl.value
            val result = withContext(Dispatchers.IO) {
                try {
                    // ADK web server list-apps endpoint proves the backend is alive
                    val request = Request.Builder()
                        .url("$url/list-apps")
                        .get()
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            "Connected — agent is online"
                        } else {
                            "Server responded with ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    "Failed: ${e.message?.take(60) ?: "unknown error"}"
                }
            }
            if (result.startsWith("Connected")) {
                _connectionTestState.value = ConnectionTestState.Success
            } else {
                _connectionTestState.value = ConnectionTestState.Failed
            }
            _connectionTestMessage.value = result
        }
    }

    companion object {
        val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
    }
}
