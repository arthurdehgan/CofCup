package app.cupnlog

import app.cupnlog.data.CoffeeEntry
import app.cupnlog.data.CupColors
import app.cupnlog.data.CuppingPlan
import app.cupnlog.data.HistoryCodec
import app.cupnlog.data.SavedSession
import app.cupnlog.data.SessionOrigin
import app.cupnlog.data.SessionStatus
import app.cupnlog.data.reveal
import app.cupnlog.data.toCompleted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1: planned→done conversion must be a same-row overwrite (decision 3). */
class PlanConversionTest {

    private fun plannedSession() = SavedSession(
        id = "planned-1",
        createdAtMs = 1_700_000_000_000L,
        plan = CuppingPlan(cupSizeMl = 150, cupCount = 4, coffees = CupColors.defaultCoffees(4)),
        status = SessionStatus.PLANNED,
    )

    @Test
    fun conversion_keeps_same_row() {
        val planned = plannedSession()
        val done = planned.toCompleted(plan = planned.plan)
        assertEquals(planned.id, done.id)
        assertEquals(planned.createdAtMs, done.createdAtMs)
        assertEquals(SessionStatus.DONE, done.status)
    }

    @Test
    fun conversion_carries_the_plan_the_run_used() {
        val planned = plannedSession()
        val runPlan = CuppingPlan(cupSizeMl = 200, cupCount = 3, coffees = listOf(CoffeeEntry(name = "Ethiopia")))
        val done = planned.toCompleted(plan = runPlan)
        assertEquals(runPlan, done.plan)
    }

    @Test
    fun conversion_defaults_to_no_ratings() {
        val done = plannedSession().toCompleted(plan = plannedSession().plan)
        assertEquals(0, done.ratings.size)
    }

    @Test
    fun conversion_clears_statuses_but_keeps_other_metadata() {
        val planned = SavedSession(
            id = "joined-1",
            createdAtMs = 123L,
            plan = CuppingPlan(150, 2, CupColors.defaultCoffees(2)),
            status = SessionStatus.PLANNED,
            revealedAtMs = null,
            origin = SessionOrigin.JOINED,
            hostName = "Sam",
            sessionRef = "host-9",
        )
        val done = planned.toCompleted(planned.plan)
        assertEquals("joined-1", done.id)
        assertEquals(123L, done.createdAtMs)
        assertEquals(SessionOrigin.JOINED, done.origin)
        assertEquals("host-9", done.sessionRef)
        assertNull(done.revealedAtMs)
    }

    @Test
    fun encode_decode_roundtrip_of_planned_then_done_row() {
        val planned = plannedSession()
        val decoded = HistoryCodec.decode(HistoryCodec.encode(listOf(planned))).single()
        assertEquals(SessionStatus.PLANNED, decoded.status)
        val finished = decoded.toCompleted(decoded.plan)
        val redecoded = HistoryCodec.decode(HistoryCodec.encode(listOf(finished))).single()
        assertEquals(SessionStatus.DONE, redecoded.status)
        assertEquals(planned.id, redecoded.id)
        assertEquals(planned.createdAtMs, redecoded.createdAtMs)
    }

    // ------------------------------------------------------------------
    // Phase 2: blind flag + explicit reveal
    // ------------------------------------------------------------------

    /** The run's plan is authoritative for blind — in both directions. */
    @Test
    fun conversion_stamps_blind_from_the_run_plan() {
        val planned = plannedSession()
        val blindRun = planned.toCompleted(plan = planned.plan.copy(blind = true))
        assertTrue(blindRun.blind)

        val blindPlanned = plannedSession().copy(blind = true)
        val visibleRun = blindPlanned.toCompleted(plan = blindPlanned.plan.copy(blind = false))
        assertFalse(visibleRun.blind)
    }

    @Test
    fun reveal_sets_revealedAt_exactly_once() {
        val blind = plannedSession().copy(blind = true)
        val revealed = blind.reveal(nowMs = 123L)
        assertEquals(123L, revealed.revealedAtMs)
        assertEquals(blind.id, revealed.id)
        // Not reversible and idempotent: a second call changes nothing.
        assertEquals(revealed, revealed.reveal(nowMs = 456L))
    }

    @Test
    fun reveal_is_a_noop_on_non_blind_or_already_revealed_sessions() {
        val visible = plannedSession()
        assertEquals(visible, visible.reveal(nowMs = 123L))

        val alreadyRevealed = plannedSession().copy(blind = true, revealedAtMs = 5L)
        assertEquals(alreadyRevealed, alreadyRevealed.reveal(nowMs = 7L))
    }

    @Test
    fun blind_flag_round_trips_through_the_history_json() {
        val blind = plannedSession().copy(blind = true)
        val decoded = HistoryCodec.decode(HistoryCodec.encode(listOf(blind))).single()
        assertTrue(decoded.blind)
        assertNull(decoded.revealedAtMs)
    }
}
