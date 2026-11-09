package app.cofcup

import app.cofcup.data.CoffeeEntry
import app.cofcup.data.CupRating
import app.cofcup.data.HistoryCodec
import app.cofcup.data.SavedSession
import app.cofcup.data.SessionOrigin
import app.cofcup.data.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the persisted-history codec: legacy tolerance, tolerant decode
 * granularity, and the phase-0 optional-field schema round-trip.
 */
class HistoryCodecTest {

    // Legacy row shape: only the original fields, no status/blind/ratings keys.
    private val legacyJson =
        """[{"id":"legacy-1","createdAt":1700000000000,"cupSizeMl":150,"cupCount":4,
           "coffees":[{"name":"Ethiopia","number":1,"color":-1614531}]}]"""

    @Test
    fun legacy_rows_decode_as_done_own_visible() {
        val sessions = HistoryCodec.decode(legacyJson)
        assertEquals(1, sessions.size)
        val session = sessions.first()
        assertEquals(SessionStatus.DONE, session.status)
        assertEquals(SessionOrigin.OWN, session.origin)
        assertEquals(false, session.blind)
        assertNull(session.revealedAtMs)
        assertEquals("", session.sessionRef)
        assertEquals("", session.hostName)
        assertTrue(session.ratings.isEmpty())
        assertEquals(150, session.plan.cupSizeMl)
        assertEquals(4, session.plan.cupCount)
    }

    @Test
    fun legacy_rows_survive_a_round_trip_unchanged() {
        val decoded = HistoryCodec.decode(legacyJson)
        val encoded = HistoryCodec.encode(decoded)
        // Defaults are omitted, so re-encoded legacy rows keep the legacy shape.
        assertTrue(encoded.contains("id\":\"legacy-1"))
        assertTrue(!encoded.contains("status"))
        val redecoded = HistoryCodec.decode(encoded)
        assertEquals(decoded, redecoded)
    }

    @Test
    fun optional_fields_round_trip() {
        val session = SavedSession(
            id = "abc",
            createdAtMs = 1_700_000_000_000,
            plan = app.cofcup.data.CuppingPlan(
                cupSizeMl = 200,
                cupCount = 2,
                coffees = listOf(
                    CoffeeEntry(name = "Kenya", number = 3, colorArgb = 0xFF123456L),
                    CoffeeEntry(),
                ),
            ),
            status = SessionStatus.PLANNED,
            blind = true,
            revealedAtMs = 1_700_000_001_000,
            origin = SessionOrigin.JOINED,
            hostName = "Host",
            sessionRef = "host-session-id",
            ratings = listOf(
                CupRating(
                    cup = 0,
                    raterId = "r1",
                    rater = "Arthur",
                    aroma = 8,
                    flavor = 7,
                    acidity = 6,
                    sweetness = 5,
                    mouthfeel = 4,
                    aftertaste = 3,
                    overall = 9,
                    notes = "florals, tea-like",
                ),
                CupRating(cup = 1, raterId = "r2"),
            ),
        )
        val redecoded = HistoryCodec.decode(HistoryCodec.encode(listOf(session))).single()
        assertEquals(session, redecoded)
    }

    @Test
    fun defaults_are_not_written_into_the_json() {
        val plain = SavedSession(
            id = "x",
            createdAtMs = 1L,
            plan = app.cofcup.data.CuppingPlan(cupSizeMl = 150, cupCount = 1, coffees = emptyList()),
        )
        val encoded = HistoryCodec.encode(listOf(plain))
        // Planned/blind/joined/ratings keys only appear when they carry a value.
        assertTrue(!encoded.contains("status"))
        assertTrue(!encoded.contains("blind"))
        assertTrue(!encoded.contains("origin"))
        assertTrue(!encoded.contains("ratings"))
    }

    @Test
    fun malformed_rating_skips_the_rating_not_the_session() {
        val json =
            """[{"id":"a","createdAt":1,"cupSizeMl":150,"cupCount":2,"coffees":[],
               "ratings":[
                 {"cup":0,"raterId":"r1","aroma":8},
                 {"cup":0,"aroma":"not-a-rater-id"},
                 {"not":"even a raterId"}
               ]}]"""
        val sessions = HistoryCodec.decode(json)
        assertEquals(1, sessions.size)
        assertEquals(1, sessions.first().ratings.size)
        assertEquals("r1", sessions.first().ratings.first().raterId)
    }

    @Test
    fun malformed_coffee_entry_skips_the_entry_not_the_session() {
        // The malformed entry is valid JSON but not an object, so only the array
        // level stays well-formed and the other entries decode.
        val json =
            """[{"id":"a","createdAt":1,"cupSizeMl":150,"cupCount":2,
               "coffees":[{"name":"Ok","number":1,"color":1},"oops"]}]"""
        val sessions = HistoryCodec.decode(json)
        assertEquals(1, sessions.size)
        assertEquals(1, sessions.first().plan.coffees.size)
        assertEquals("Ok", sessions.first().plan.coffees.first().name)
    }

    @Test
    fun malformed_records_are_dropped_individually() {
        // A non-object element is skipped as well as one missing required fields.
        val json =
            """[{"id":"good","createdAt":1,"cupSizeMl":150,"cupCount":1,"coffees":[]},
               {"id":"no-size"},
               "just a string"]"""
        val sessions = HistoryCodec.decode(json)
        assertEquals(1, sessions.size)
        assertEquals("good", sessions.first().id)
    }

    @Test
    fun records_without_required_fields_are_dropped() {
        val noId = """[{"createdAt":1,"cupSizeMl":150,"cupCount":1,"coffees":[]}]"""
        assertTrue(HistoryCodec.decode(noId).isEmpty())
        val noSize = """[{"id":"x","createdAt":1,"coffees":[]}]"""
        assertTrue(HistoryCodec.decode(noSize).isEmpty())
    }

    @Test
    fun absent_zero_revealedAt_decodes_as_not_revealed() {
        val json =
            """[{"id":"a","createdAt":1,"cupSizeMl":150,"cupCount":1,"coffees":[]},
               {"id":"b","createdAt":2,"cupSizeMl":150,"cupCount":1,"coffees":[],"blind":true,"revealedAt":0},
               {"id":"c","createdAt":3,"cupSizeMl":150,"cupCount":1,"coffees":[],"blind":true,"revealedAt":5}]"""
        val sessions = HistoryCodec.decode(json)
        assertNull(sessions.first { it.id == "a" }.revealedAtMs)
        assertNull(sessions.first { it.id == "b" }.revealedAtMs)
        assertEquals(5L, sessions.first { it.id == "c" }.revealedAtMs)
    }

    @Test
    fun sessions_are_returned_newest_first() {
        val json =
            """[{"id":"old","createdAt":1,"cupSizeMl":150,"cupCount":1,"coffees":[]},
               {"id":"new","createdAt":2,"cupSizeMl":150,"cupCount":1,"coffees":[]}]"""
        assertEquals("new", HistoryCodec.decode(json).first().id)
    }

    @Test
    fun degenerate_inputs_decode_to_empty_history() {
        assertTrue(HistoryCodec.decode(null).isEmpty())
        assertTrue(HistoryCodec.decode("").isEmpty())
        assertTrue(HistoryCodec.decode("   ").isEmpty())
        assertTrue(HistoryCodec.decode("not json at all").isEmpty())
    }
}
