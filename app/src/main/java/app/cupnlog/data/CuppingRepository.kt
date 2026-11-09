package app.cupnlog.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.cupnlogDataStore by preferencesDataStore(name = "cupnlog_store")

/**
 * Persists the cupping session history as JSON in a DataStore preference.
 * Sessions survive app restarts without any database dependency.
 * Profile preferences (`my_id`, `my_name`) live in the same store.
 */
class CuppingRepository(context: Context) {

    private val store = context.applicationContext.cupnlogDataStore
    private val historyKey = stringPreferencesKey("history_v1")
    private val soundKey = booleanPreferencesKey("sound_on_next_step")
    private val vibrateKey = booleanPreferencesKey("vibrate_on_next_step")
    private val myIdKey = stringPreferencesKey("my_id")
    private val myNameKey = stringPreferencesKey("my_name")

    /** All saved sessions, newest first. */
    val sessions: Flow<List<SavedSession>> = store.data.map { prefs ->
        HistoryCodec.decode(prefs[historyKey])
    }

    /** Whether a chime is played when the timer advances a step (on by default). */
    val soundOn: Flow<Boolean> = store.data.map { prefs -> prefs[soundKey] ?: true }

    /** Whether the phone vibrates when the timer advances a step (off by default). */
    val vibrateOn: Flow<Boolean> = store.data.map { prefs -> prefs[vibrateKey] ?: false }

    /** This device's stable participant id, empty until first generated. */
    val myId: Flow<String> = store.data.map { prefs -> prefs[myIdKey].orEmpty() }

    /** This device's participant display name (decided lazily, editable in Settings). */
    val myName: Flow<String> = store.data.map { prefs -> prefs[myNameKey].orEmpty() }

    suspend fun setSoundOn(enabled: Boolean) {
        store.edit { prefs -> prefs[soundKey] = enabled }
    }

    suspend fun setVibrateOn(enabled: Boolean) {
        store.edit { prefs -> prefs[vibrateKey] = enabled }
    }

    /** Stable participant id, generated (random UUID) on first use. */
    suspend fun getOrCreateMyId(): String {
        var result = ""
        store.edit { prefs ->
            val existing = prefs[myIdKey]
            if (existing.isNullOrBlank()) {
                result = UUID.randomUUID().toString()
                prefs[myIdKey] = result
            } else {
                result = existing
            }
        }
        return result
    }

    suspend fun setMyName(name: String) {
        store.edit { prefs -> prefs[myNameKey] = name.trim().take(MY_NAME_MAX_LENGTH) }
    }

    suspend fun save(session: SavedSession) {
        store.edit { prefs ->
            val current = HistoryCodec.decode(prefs[historyKey])
            val updated = (listOf(session) + current.filterNot { it.id == session.id })
                .sortedByDescending { it.createdAtMs }
            prefs[historyKey] = HistoryCodec.encode(updated)
        }
    }

    suspend fun delete(sessionId: String) {
        store.edit { prefs ->
            val current = HistoryCodec.decode(prefs[historyKey]).filterNot { it.id == sessionId }
            prefs[historyKey] = HistoryCodec.encode(current)
        }
    }

    private companion object {
        const val MY_NAME_MAX_LENGTH = 40
    }
}
