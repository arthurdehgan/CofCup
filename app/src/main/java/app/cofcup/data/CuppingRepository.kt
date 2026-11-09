package app.cofcup.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.cofcupDataStore by preferencesDataStore(name = "cofcup_store")

/**
 * Persists the cupping session history as JSON in a DataStore preference.
 * Sessions survive app restarts without any database dependency.
 */
class CuppingRepository(context: Context) {

    private val store = context.applicationContext.cofcupDataStore
    private val historyKey = stringPreferencesKey("history_v1")
    private val soundKey = booleanPreferencesKey("sound_on_next_step")
    private val vibrateKey = booleanPreferencesKey("vibrate_on_next_step")

    /** All saved sessions, newest first. */
    val sessions: Flow<List<SavedSession>> = store.data.map { prefs ->
        decodeHistory(prefs[historyKey])
    }

    /** Whether a chime is played when the timer advances a step (on by default). */
    val soundOn: Flow<Boolean> = store.data.map { prefs -> prefs[soundKey] ?: true }

    /** Whether the phone vibrates when the timer advances a step (off by default). */
    val vibrateOn: Flow<Boolean> = store.data.map { prefs -> prefs[vibrateKey] ?: false }

    suspend fun setSoundOn(enabled: Boolean) {
        store.edit { prefs -> prefs[soundKey] = enabled }
    }

    suspend fun setVibrateOn(enabled: Boolean) {
        store.edit { prefs -> prefs[vibrateKey] = enabled }
    }

    suspend fun save(session: SavedSession) {
        store.edit { prefs ->
            val current = decodeHistory(prefs[historyKey])
            val updated = (listOf(session) + current.filterNot { it.id == session.id })
                .sortedByDescending { it.createdAtMs }
            prefs[historyKey] = encodeHistory(updated)
        }
    }

    suspend fun delete(sessionId: String) {
        store.edit { prefs ->
            val current = decodeHistory(prefs[historyKey]).filterNot { it.id == sessionId }
            prefs[historyKey] = encodeHistory(current)
        }
    }

    private fun decodeHistory(json: String?): List<SavedSession> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                runCatching {
                    val obj = array.getJSONObject(i)
                    val coffeesArray = obj.getJSONArray("coffees")
                    val coffees = (0 until coffeesArray.length()).map { j ->
                        val coffee = coffeesArray.getJSONObject(j)
                        CoffeeEntry(
                            name = coffee.optString("name", ""),
                            number = coffee.optInt("number", 0),
                            colorArgb = coffee.getLong("color"),
                        )
                    }
                    SavedSession(
                        id = obj.getString("id"),
                        createdAtMs = obj.getLong("createdAt"),
                        plan = CuppingPlan(
                            cupSizeMl = obj.getInt("cupSizeMl"),
                            cupCount = obj.getInt("cupCount"),
                            coffees = coffees,
                        ),
                    )
                }.getOrNull()
            }.sortedByDescending { it.createdAtMs }
        }.getOrDefault(emptyList())
    }

    private fun encodeHistory(sessions: List<SavedSession>): String {
        val array = JSONArray()
        // Keep the stored history bounded so the preference stays small.
        sessions.take(MAX_STORED_SESSIONS).forEach { session ->
            val coffees = JSONArray()
            session.plan.coffees.forEach { coffee ->
                coffees.put(
                    JSONObject().apply {
                        put("name", coffee.name)
                        put("number", coffee.number)
                        put("color", coffee.colorArgb)
                    }
                )
            }
            array.put(
                JSONObject().apply {
                    put("id", session.id)
                    put("createdAt", session.createdAtMs)
                    put("cupSizeMl", session.plan.cupSizeMl)
                    put("cupCount", session.plan.cupCount)
                    put("coffees", coffees)
                }
            )
        }
        return array.toString()
    }

    private companion object {
        const val MAX_STORED_SESSIONS = 200
    }
}
