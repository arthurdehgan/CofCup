package app.cofcup.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Cupping brew maths, based on the SCA cupping protocol:
 * 8.25 g of coffee per 150 ml of water, i.e. 0.055 g per ml (55 g per litre).
 */
object CuppingMath {

    /** Grams of coffee per ml of water, from the SCA ratio 8.25 g / 150 ml. */
    const val GRAMS_PER_ML: Double = 8.25 / 150.0 // = 0.055

    /** Total water needed: number of cups x cup size. */
    fun waterTotalMl(cupSizeMl: Int, cupCount: Int): Int = cupSizeMl * cupCount

    /** Coffee dose per cup, rounded to the first decimal. */
    fun coffeePerCupG(cupSizeMl: Int): Double = round1(cupSizeMl * GRAMS_PER_ML)

    /** Total coffee to grind for the whole session, rounded to the first decimal. */
    fun coffeeTotalG(cupSizeMl: Int, cupCount: Int): Double =
        round1(cupSizeMl * cupCount * GRAMS_PER_ML)

    /** Round to one decimal, half up — e.g. 8.25 g -> 8.3 g, 13.75 g -> 13.8 g. */
    fun round1(value: Double): Double =
        BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()

    fun formatGrams(grams: Double): String = String.format(Locale.US, "%.1f g", grams)

    fun formatWaterMl(ml: Int): String =
        if (ml >= 1000) String.format(Locale.US, "%.2f L (%d ml)", ml / 1000.0, ml) else "$ml ml"

    /** 93 seconds -> "1:33". Negative values are clamped to zero. */
    fun formatClock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }
}
