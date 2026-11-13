package app.cupnlog

import app.cupnlog.data.CupRating
import app.cupnlog.data.RatingField
import app.cupnlog.data.isRated
import app.cupnlog.data.scoreFor
import app.cupnlog.data.withScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2: the "rated" definition (any score — attribute, overall, or notes —
 * exists) and the per-field accessors the rating screens are built on.
 */
class CupRatingTest {

    private fun unrated() = CupRating(cup = 0, raterId = "r1")

    @Test
    fun all_empty_is_not_rated() {
        assertFalse(unrated().isRated)
    }

    @Test
    fun any_single_attribute_makes_it_rated() {
        RatingField.entries.forEach { field ->
            assertTrue("$field", unrated().withScore(field, 5).isRated)
        }
    }

    @Test
    fun notes_alone_count_as_rated() {
        assertTrue(unrated().copy(notes = "florals").isRated)
    }

    @Test
    fun clearing_every_field_and_note_is_not_rated_again() {
        val rated = unrated()
            .withScore(RatingField.OVERALL, 8)
            .withScore(RatingField.AROMA, 7)
            .copy(notes = "x")
        assertTrue(rated.isRated)
        assertFalse(
            rated
                .withScore(RatingField.OVERALL, null)
                .withScore(RatingField.AROMA, null)
                .copy(notes = "")
                .isRated,
        )
    }

    @Test
    fun score_accessor_round_trips_each_field() {
        var rating = unrated()
        RatingField.entries.forEach { field ->
            rating = rating.withScore(field, 10)
            assertEquals(10, rating.scoreFor(field))
        }
        // Overall is stored, never derived from the attributes.
        assertEquals(10, rating.overall)
    }

    @Test
    fun unset_score_reads_as_null() {
        assertNull(unrated().scoreFor(RatingField.OVERALL))
    }
}
