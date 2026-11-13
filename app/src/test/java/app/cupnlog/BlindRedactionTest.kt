package app.cupnlog

import app.cupnlog.data.CoffeeEntry
import app.cupnlog.data.blindDoneInstruction
import app.cupnlog.data.blindedByPosition
import org.junit.Assert.assertEquals
import org.junit.Test

/** Blind redaction helpers: visible identity is position only, never any metadata. */
class BlindRedactionTest {

    @Test
    fun blind_display_shows_positions_only() {
        val coffees = listOf(
            CoffeeEntry(name = "Ethiopia", label = "7", colorArgb = 0xFFFF5252L),
            CoffeeEntry(name = "", label = "2", colorArgb = 0xFF43A047L),
            CoffeeEntry(name = "Kenya", label = "", colorArgb = 0xFF42A5F5L),
        )
        val blind = coffees.blindedByPosition()
        assertEquals(listOf("Cup 1", "Cup 2", "Cup 3"), blind.map { it.label })
    }

    @Test
    fun blind_display_is_empty_for_empty_plans() {
        assertEquals(emptyList<String>(), emptyList<CoffeeEntry>().blindedByPosition().map { it.label })
    }

    @Test
    fun blind_done_instruction_never_mentions_colours_numbers_or_names() {
        for (count in listOf(1, 4, 16)) {
            val text = blindDoneInstruction(count)
            assertEquals(false, text.contains("colour"))
            assertEquals(false, text.contains("color"))
            assertEquals(false, text.contains("number"))
            assertEquals(false, text.contains("Unnamed"))
        }
        assertEquals(false, blindDoneInstruction(1).contains("cups"))
        assertEquals(true, blindDoneInstruction(4).contains("1–4"))
    }
}

