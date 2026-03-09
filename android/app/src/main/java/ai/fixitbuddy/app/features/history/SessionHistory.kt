package ai.fixitbuddy.app.features.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SessionRecord(
    val timestampMs: Long,
    val durationSec: Int,
    val transcriptSnippet: String,
    val toolCallCount: Int
)

/**
 * Persists the last 10 session summaries in DataStore as JSON.
 */
@Singleton
class SessionHistoryStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val json = Json { ignoreUnknownKeys = true }

    val sessions: Flow<List<SessionRecord>> = dataStore.data.map { prefs ->
        val raw = prefs[HISTORY_KEY] ?: "[]"
        try {
            json.decodeFromString<List<SessionRecord>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addSession(record: SessionRecord) {
        dataStore.edit { prefs ->
            val current = try {
                json.decodeFromString<List<SessionRecord>>(prefs[HISTORY_KEY] ?: "[]")
            } catch (_: Exception) {
                emptyList()
            }
            // Keep last 10 sessions, most recent first
            val updated = (listOf(record) + current).take(MAX_SESSIONS)
            prefs[HISTORY_KEY] = json.encodeToString(updated)
        }
    }

    companion object {
        private val HISTORY_KEY = stringPreferencesKey("session_history")
        private const val MAX_SESSIONS = 10
    }
}
