package app.cupnlog

import app.cupnlog.data.CupColors
import app.cupnlog.data.CuppingDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression guard: a 16-cup plan must have 16 coffee entries and colours. */
class CupColorsTest {

    private val maxCups = CuppingDraft.MAX_CUPS

    @Test
    fun palette_has_16_entries_matching_MAX_CUPS() {
        assertEquals(16, maxCups)
        assertEquals(maxCups, CupColors.palette.size)
        assertTrue("Palette colours must be distinct", CupColors.palette.toSet().size == CupColors.palette.size)
    }

    @Test
    fun sixteen_cup_plan_has_16_coffees() {
        val plan = CuppingDraft(cupCount = maxCups).toPlan()
        assertEquals(maxCups, plan.coffees.size)
        assertEquals(
            CupColors.palette.take(maxCups).toSet(),
            plan.coffees.map { it.colorArgb }.toSet(),
        )
    }

    @Test
    fun default_coffees_default_to_full_palette() {
        assertEquals(maxCups, CupColors.defaultCoffees().size)
        assertEquals(maxCups, CuppingDraft().coffees.size)
    }
}
