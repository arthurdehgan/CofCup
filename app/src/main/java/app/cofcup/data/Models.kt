package app.cofcup.data

/** One cupping cup: what it is called, the number written on it, and its colour. */
data class CoffeeEntry(
    val name: String = "",
    /** 0 means "no number set". */
    val number: Int = 0,
    /** ARGB colour as a Long, e.g. 0xFFE53935L. */
    val colorArgb: Long = FALLBACK_COLOR.toLong(),
) {
    val displayName: String
        get() = if (name.isBlank()) FALLBACK_NAME else name

    val displayNumber: String
        get() = if (number > 0) number.toString() else ""

    companion object {
        const val FALLBACK_NAME = "Unnamed coffee"
        const val FALLBACK_COLOR = 0xFF90A4AEL
    }
}

/** Twelve distinct colours, one per possible coffee, used to mark the cups. */
object CupColors {
    val palette: List<Long> = listOf(
        0xFFFF5252L, // red
        0xFFFF9800L, // orange
        0xFFFFD740L, // yellow
        0xFF43A047L, // green
        0xFF26A69AL, // teal
        0xFF42A5F5L, // blue
        0xFF5C6BC0L, // indigo
        0xFFAB47BCL, // purple
        0xFFF06292L, // pink
        0xFF795548L, // brown
        0xFF90A4AEL, // grey
        0xFF26C6DAL, // cyan
    )

    fun defaultCoffees(count: Int = palette.size): List<CoffeeEntry> =
        palette.take(count).mapIndexed { index, color ->
            CoffeeEntry(name = "", number = index + 1, colorArgb = color)
        }
}

/** Settings picked before a session starts. Editable at any time before cupping. */
data class CuppingDraft(
    val cupSizeMl: Int = DEFAULT_CUP_SIZE,
    val cupCount: Int = DEFAULT_CUP_COUNT,
    val coffees: List<CoffeeEntry> = CupColors.defaultCoffees(),
) {
    /** Immutable snapshot used while a session is running. */
    fun toPlan(): CuppingPlan = CuppingPlan(
        cupSizeMl = cupSizeMl,
        cupCount = cupCount,
        coffees = coffees.take(cupCount),
    )

    companion object {
        const val DEFAULT_CUP_SIZE = 150
        const val DEFAULT_CUP_COUNT = 3
        const val MIN_CUP_SIZE = 50
        const val MAX_CUP_SIZE = 500
        const val MIN_CUPS = 1
        const val MAX_CUPS = 12
    }
}

/** Immutable snapshot of the plan for a session actually started. */
data class CuppingPlan(
    val cupSizeMl: Int,
    val cupCount: Int,
    val coffees: List<CoffeeEntry>,
)

/** A finished session stored in the history. */
data class SavedSession(
    val id: String,
    val createdAtMs: Long,
    val plan: CuppingPlan,
)
