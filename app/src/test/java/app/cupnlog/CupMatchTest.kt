package app.cupnlog

import app.cupnlog.data.CoffeeEntry
import app.cupnlog.data.CupColors
import app.cupnlog.data.CuppingPlan
import app.cupnlog.data.SavedSession
import app.cupnlog.data.SessionStatus
import app.cupnlog.data.invalidLabelIndices
import app.cupnlog.data.matchAndReveal
import app.cupnlog.data.reorderedByMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blind identification: label validity and the position permutation that
 * attaches the cupped ratings to the right coffees at reveal time.
 */
class CupMatchTest {

    private fun coffee(index: Int, label: String = (index + 1).toString()) =
        CoffeeEntry(name = "Coffee ${index + 1}", label = label, colorArgb = CupColors.palette[index])

    private fun doneBlindSession(labels: List<String>) = SavedSession(
        id = "blind-1",
        createdAtMs = 1_700_000_000_000L,
        plan = CuppingPlan(
            cupSizeMl = 150,
            cupCount = labels.size,
            coffees = labels.mapIndexed { i, label -> coffee(i, label) },
            blind = true,
        ),
        status = SessionStatus.DONE,
        blind = true,
    )

    // ------------------------------------------------------------------
    // Label validity
    // ------------------------------------------------------------------

    @Test
    fun blank_and_duplicate_labels_are_invalid() {
        val coffees = listOf(
            coffee(0, "1"),
            coffee(1, ""),
            coffee(2, "1"),
        )
        // Every blank entry, and every side of a duplicate clash, is flagged.
        assertEquals(setOf(0, 1, 2), coffees.invalidLabelIndices())
    }

    @Test
    fun duplicate_labels_are_matched_case_insensitively_and_with_trim() {
        val coffees = listOf(coffee(0, "esP"), coffee(1, "Esp "))
        assertEquals(setOf(0, 1), coffees.invalidLabelIndices())
    }

    @Test
    fun distinct_labels_are_all_valid() {
        assertTrue(CupColors.defaultCoffees(16).invalidLabelIndices().isEmpty())
    }

    // ------------------------------------------------------------------
    // The permutation
    // ------------------------------------------------------------------

    @Test
    fun match_reorders_coffees_into_cup_positions() {
        val coffees = listOf(coffee(0), coffee(1), coffee(2), coffee(3))
        // Table order: cup 1 held coffee 2, cup 2 coffee 1, cup 3 coffee 4, cup 4 coffee 3.
        val reordered = coffees.reorderedByMatch(listOf(1, 0, 3, 2))
        assertEquals(listOf(coffees[1], coffees[0], coffees[3], coffees[2]), reordered)
    }

    @Test
    fun match_must_cover_every_position() {
        val coffees = listOf(coffee(0), coffee(1), coffee(2))
        assertThrows(IllegalArgumentException::class.java) {
            coffees.reorderedByMatch(listOf(0, 1))
        }
    }

    @Test
    fun match_must_use_each_coffee_once() {
        val coffees = listOf(coffee(0), coffee(1), coffee(2))
        assertThrows(IllegalArgumentException::class.java) {
            coffees.reorderedByMatch(listOf(0, 0, 1))
        }
    }

    // ------------------------------------------------------------------
    // matchAndReveal
    // ------------------------------------------------------------------

    @Test
    fun confirming_the_match_reorders_and_reveals() {
        val session = doneBlindSession(listOf("1", "2", "3", "4"))
        val matched = session.matchAndReveal(listOf(2, 0, 1, 3), nowMs = 123L)
        assertEquals(session.plan.coffees[2], matched.plan.coffees[0])
        assertEquals(session.plan.coffees[0], matched.plan.coffees[1])
        assertEquals(session.plan.coffees[1], matched.plan.coffees[2])
        assertEquals(session.plan.coffees[3], matched.plan.coffees[3])
        assertEquals(123L, matched.revealedAtMs)
    }

    @Test
    fun re_matching_re_permutes_but_never_moves_the_reveal_stamp() {
        val session = doneBlindSession(listOf("1", "2", "3"))
        val first = session.matchAndReveal(listOf(1, 2, 0), nowMs = 100L)
        val second = first.matchAndReveal(listOf(2, 0, 1), nowMs = 999L)
        assertEquals(100L, second.revealedAtMs)
        assertEquals(first.plan.coffees[2], second.plan.coffees[0])
        assertEquals(first.plan.coffees[0], second.plan.coffees[1])
        assertEquals(first.plan.coffees[1], second.plan.coffees[2])
    }

    @Test
    fun matching_is_a_noop_where_it_never_applies() {
        // Non-blind: identities were never hidden from the cupping.
        val visible = SavedSession(
            id = "v",
            createdAtMs = 1L,
            plan = CuppingPlan(150, 2, listOf(coffee(0), coffee(1))),
        )
        assertEquals(visible, visible.matchAndReveal(listOf(1, 0), nowMs = 1L))

        // Planned rows have no cupped ratings to align; they reveal directly.
        val planned = doneBlindSession(listOf("1", "2")).copy(status = SessionStatus.PLANNED)
        assertEquals(planned, planned.matchAndReveal(listOf(1, 0), nowMs = 1L))
    }
}
