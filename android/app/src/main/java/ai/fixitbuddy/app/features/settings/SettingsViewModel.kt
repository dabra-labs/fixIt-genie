package ai.fixitbuddy.app.features.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.fixitbuddy.app.core.config.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val backendUrl = dataStore.data
        .map { prefs -> prefs[BACKEND_URL_KEY] ?: AppConfig.BACKEND_URL }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig.BACKEND_URL)

    fun saveBackendUrl(url: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[BACKEND_URL_KEY] = url }
        }
    }

    companion object {
        val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
    }
}
