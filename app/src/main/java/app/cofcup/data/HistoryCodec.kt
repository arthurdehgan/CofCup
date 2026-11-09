package app.cofcup.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for the persisted session history (the `history_v1` preference).
 *
 * Decode is tolerant in granularity: a malformed record drops only that record,
 * but within a record a malformed rating drops only that rating and a malformed
 * coffee entry only that coffee — never the whole session. New optional fields
 * are read with `opt*` calls so legacy rows decode untouched.
 */
internal object HistoryCodec {

    fun decode(json: String?): List<SavedSession> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                decodeSession(array.optJSONObject(i) ?: return@mapNotNull null)
            }.sortedByDescending { it.createdAtMs }
        }.getOrDefault(emptyList())
    }

    fun encode(sessions: List<SavedSession>): String {
        val array = JSONArray()
        // Keep the stored history bounded so the preference stays small.
        sessions.take(MAX_STORED_SESSIONS).forEach { session ->
            array.put(encodeSession(session))
        }
        return array.toString()
    }

    // ------------------------------------------------------------------
    // Decode
    // ------------------------------------------------------------------

    private fun decodeSession(obj: JSONObject): SavedSession? = runCatching {
        val id = obj.optString("id")
        if (id.isBlank()) return null
        val cupSizeMl = obj.optInt("cupSizeMl", -1)
        val cupCount = obj.optInt("cupCount", -1)
        if (cupSizeMl <= 0 || cupCount < 0) return null
        SavedSession(
            id = id,
            createdAtMs = obj.optLong("createdAt", 0L),
            plan = CuppingPlan(cupSizeMl = cupSizeMl, cupCount = cupCount, coffees = decodeCoffees(obj)),
            status = when (obj.optString("status")) {
                "planned" -> SessionStatus.PLANNED
                else -> SessionStatus.DONE
            },
            blind = obj.optBoolean("blind", false),
            revealedAtMs = obj.optLong("revealedAt", 0L).takeIf { it > 0 },
            origin = when (obj.optString("origin")) {
                "joined" -> SessionOrigin.JOINED
                else -> SessionOrigin.OWN
            },
            hostName = obj.optString("hostName", ""),
            sessionRef = obj.optString("sessionRef", ""),
            ratings = decodeRatings(obj),
        )
    }.getOrNull()

    /** A malformed coffee entry drops only that entry, not the session. */
    private fun decodeCoffees(obj: JSONObject): List<CoffeeEntry> {
        val coffeesArray = obj.optJSONArray("coffees") ?: return emptyList()
        return buildList {
            for (j in 0 until coffeesArray.length()) {
                val coffee = coffeesArray.optJSONObject(j) ?: continue
                runCatching {
                    add(
                        CoffeeEntry(
                            name = coffee.optString("name", ""),
                            number = coffee.optInt("number", 0),
                            colorArgb = coffee.optLong("color", CoffeeEntry.FALLBACK_COLOR),
                        )
                    )
                }
            }
        }
    }

    /** A malformed rating drops only that rating, not the session. */
    private fun decodeRatings(obj: JSONObject): List<CupRating> {
        val ratingsArray = obj.optJSONArray("ratings") ?: return emptyList()
        return buildList {
            for (j in 0 until ratingsArray.length()) {
                val rating = ratingsArray.optJSONObject(j) ?: continue
                runCatching { decodeRating(rating)?.let(::add) }
            }
        }
    }

    private fun decodeRating(rating: JSONObject): CupRating? {
        val raterId = rating.optString("raterId")
        if (raterId.isBlank()) return null
        return CupRating(
            cup = rating.optInt("cup", 0),
            raterId = raterId,
            rater = rating.optString("rater", ""),
            aroma = rating.optScore("aroma"),
            flavor = rating.optScore("flavor"),
            acidity = rating.optScore("acidity"),
            sweetness = rating.optScore("sweetness"),
            mouthfeel = rating.optScore("mouthfeel"),
            aftertaste = rating.optScore("aftertaste"),
            overall = rating.optScore("overall"),
            notes = rating.optString("notes", ""),
        )
    }

    /** In the history store explicit scores are optional; absent stays absent. */
    private fun JSONObject.optScore(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getInt(key) }.getOrNull()
    }

    // ------------------------------------------------------------------
    // Encode
    // ------------------------------------------------------------------

    private fun encodeSession(session: SavedSession): JSONObject {
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
        return JSONObject().apply {
            put("id", session.id)
            put("createdAt", session.createdAtMs)
            put("cupSizeMl", session.plan.cupSizeMl)
            put("cupCount", session.plan.cupCount)
            put("coffees", coffees)
            if (session.status == SessionStatus.PLANNED) put("status", "planned")
            if (session.blind) put("blind", true)
            if (session.revealedAtMs != null) put("revealedAt", session.revealedAtMs)
            if (session.origin == SessionOrigin.JOINED) put("origin", "joined")
            if (session.hostName.isNotBlank()) put("hostName", session.hostName)
            if (session.sessionRef.isNotBlank()) put("sessionRef", session.sessionRef)
            if (session.ratings.isNotEmpty()) put("ratings", encodeRatings(session.ratings))
        }
    }

    private fun encodeRatings(ratings: List<CupRating>): JSONArray {
        val array = JSONArray()
        ratings.forEach { rating ->
            array.put(
                JSONObject().apply {
                    put("cup", rating.cup)
                    put("raterId", rating.raterId)
                    if (rating.rater.isNotBlank()) put("rater", rating.rater)
                    rating.aroma?.let { put("aroma", it) }
                    rating.flavor?.let { put("flavor", it) }
                    rating.acidity?.let { put("acidity", it) }
                    rating.sweetness?.let { put("sweetness", it) }
                    rating.mouthfeel?.let { put("mouthfeel", it) }
                    rating.aftertaste?.let { put("aftertaste", it) }
                    rating.overall?.let { put("overall", it) }
                    if (rating.notes.isNotBlank()) put("notes", rating.notes)
                }
            )
        }
        return array
    }

    private const val MAX_STORED_SESSIONS = 200
}
