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

/** Sixteen distinct colours, one per possible coffee, used to mark the cups. */
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
        0xFFFF7043L, // deep orange
        0xFFAEEA00L, // lime
        0xFF7E57C2L, // deep purple
        0xFF455A64L, // dark slate
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
        const val DEFAULT_CUP_COUNT = 4
        const val MIN_CUP_SIZE = 100
        const val MAX_CUP_SIZE = 400
        const val MIN_CUPS = 1
        const val MAX_CUPS = 16
    }
}

/** Immutable snapshot of the plan for a session actually started. */
data class CuppingPlan(
    val cupSizeMl: Int,
    val cupCount: Int,
    val coffees: List<CoffeeEntry>,
)

/** Lifecycle state of a stored session. Absent in JSON means done (legacy rows). */
enum class SessionStatus { PLANNED, DONE }

/** Whether the session is the user's own, or a joined copy of someone else's. */
enum class SessionOrigin { OWN, JOINED }

/**
 * One rater's scores for one cup. Attribute ratings (aroma..aftertaste) are
 * informational only; [overall] is strictly manual and never derived from them.
 */
data class CupRating(
    /** Zero-based index of the cup, matching the plan's coffees position. */
    val cup: Int,
    /** Stable participant id; merges and re-submissions are idempotent per rater. */
    val raterId: String,
    /** Display name at save time; follows `my_name` when known. */
    val rater: String = "",
    val aroma: Int? = null,
    val flavor: Int? = null,
    val acidity: Int? = null,
    val sweetness: Int? = null,
    val mouthfeel: Int? = null,
    val aftertaste: Int? = null,
    val overall: Int? = null,
    val notes: String = "",
)

/** A session stored in the history: planned set-ups, finished runs, joined copies. */
data class SavedSession(
    val id: String,
    val createdAtMs: Long,
    val plan: CuppingPlan,
    /** Absent in old rows; legacy JSON decodes as DONE. */
    val status: SessionStatus = SessionStatus.DONE,
    /** Blind tasting: identities are hidden until an explicit reveal. */
    val blind: Boolean = false,
    /** Epoch ms of a blind identity reveal; null = not revealed. */
    val revealedAtMs: Long? = null,
    val origin: SessionOrigin = SessionOrigin.OWN,
    /** Host's display name; only set when origin is joined. */
    val hostName: String = "",
    /** Host's session id; empty for the user's own sessions. */
    val sessionRef: String = "",
    /** All participants' ratings keyed by raterId (see [CupRating.raterId]). */
    val ratings: List<CupRating> = emptyList(),
)
