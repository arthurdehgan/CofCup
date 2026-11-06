package app.cofcup

import app.cofcup.data.CuppingMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CuppingMathTest {

    @Test
    fun water_total_is_cups_times_cup_size() {
        assertEquals(450, CuppingMath.waterTotalMl(150, 3))
        assertEquals(1, CuppingMath.waterTotalMl(1, 1))
    }

    @Test
    fun coffee_per_cup_follows_sca_ratio() {
        // SCA: 8.25 g per 150 ml -> rounded to one decimal.
        assertEquals(8.3, CuppingMath.coffeePerCupG(150), 0.0001)
        // 55 g per litre: 100 ml -> 5.5 g, 200 ml -> 11.0 g, 250 ml -> 13.8 g
        assertEquals(5.5, CuppingMath.coffeePerCupG(100), 0.0001)
        assertEquals(11.0, CuppingMath.coffeePerCupG(200), 0.0001)
        assertEquals(13.8, CuppingMath.coffeePerCupG(250), 0.0001)
    }

    @Test
    fun coffee_total_sums_per_cup_doses() {
        assertEquals(24.8, CuppingMath.coffeeTotalG(150, 3), 0.0001)
    }

    @Test
    fun round1_keeps_one_decimal() {
        assertEquals(8.3, CuppingMath.round1(8.25), 0.0001)
        assertEquals(13.8, CuppingMath.round1(13.75), 0.0001)
        assertEquals(5.5, CuppingMath.round1(5.5), 0.0001)
    }

    @Test
    fun formatting_helpers() {
        assertTrue(CuppingMath.formatGrams(8.25).contains("8.3"))
        assertTrue(CuppingMath.formatWaterMl(450).contains("450 ml"))
        assertTrue(CuppingMath.formatWaterMl(1800).contains("L"))
        assertEquals("4:00", CuppingMath.formatClock(240))
        assertEquals("0:09", CuppingMath.formatClock(9))
    }
}
