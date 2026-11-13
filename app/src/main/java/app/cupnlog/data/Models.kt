package app.cupnlog.data

/** One cupping cup: what it is called, the label written under it, and its colour. */
data class CoffeeEntry(
    val name: String = "",
    /** Short label under the cup (a number or short text, e.g. "3" or "ESP1"); blank = none. */
    val label: String = "",
    /** ARGB colour as a Long, e.g. 0xFFE53935L. */
    val colorArgb: Long = FALLBACK_COLOR.toLong(),
) {
    val displayName: String
        get() = if (name.isBlank()) FALLBACK_NAME else name

    /** The label as shown inside the swatch; blank when none was set. */
    val displayLabel: String
        get() = label.trim()

    companion object {
        const val FALLBACK_NAME = "Unnamed coffee"
        const val FALLBACK_COLOR = 0xFF90A4AEL
        /** Labels must stay short enough to fit inside the swatch circle. */
        const val MAX_LABEL_LENGTH = 6
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
            CoffeeEntry(name = "", label = (index + 1).toString(), colorArgb = color)
        }
}

/** Settings picked before a session starts. Editable at any time before cupping. */
data class CuppingDraft(
    val cupSizeMl: Int = DEFAULT_CUP_SIZE,
    val cupCount: Int = DEFAULT_CUP_COUNT,
    val coffees: List<CoffeeEntry> = CupColors.defaultCoffees(),
    /** Optional display name of the cupping session; used for saved setups. */
    val sessionName: String = "",
    /** Blind tasting: identities are hidden until the cups are identified and revealed. */
    val blind: Boolean = false,
) {
    /** Immutable snapshot used while a session is running. */
    fun toPlan(): CuppingPlan = CuppingPlan(
        cupSizeMl = cupSizeMl,
        cupCount = cupCount,
        coffees = coffees.take(cupCount),
        sessionName = sessionName,
        blind = blind,
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
    /** Optional display name of the session (see [CuppingDraft.sessionName]). */
    val sessionName: String = "",
    /** Blind tasting: live screens render positions only (see [Blind.kt]). */
    val blind: Boolean = false,
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

/** The six informational attributes plus the strictly manual Overall (1–10). */
enum class RatingField(val label: String) {
    AROMA("Aroma"),
    FLAVOR("Flavor"),
    ACIDITY("Acidity"),
    SWEETNESS("Sweetness"),
    MOUTHFEEL("Mouthfeel"),
    AFTERTASTE("Aftertaste"),
    OVERALL("Overall"),
}

/** Sets one score field; a null value means "not rated". */
fun CupRating.withScore(field: RatingField, value: Int?): CupRating = when (field) {
    RatingField.AROMA -> copy(aroma = value)
    RatingField.FLAVOR -> copy(flavor = value)
    RatingField.ACIDITY -> copy(acidity = value)
    RatingField.SWEETNESS -> copy(sweetness = value)
    RatingField.MOUTHFEEL -> copy(mouthfeel = value)
    RatingField.AFTERTASTE -> copy(aftertaste = value)
    RatingField.OVERALL -> copy(overall = value)
}

/** Reads one score field, so screens need no per-field branching. */
fun CupRating.scoreFor(field: RatingField): Int? = when (field) {
    RatingField.AROMA -> aroma
    RatingField.FLAVOR -> flavor
    RatingField.ACIDITY -> acidity
    RatingField.SWEETNESS -> sweetness
    RatingField.MOUTHFEEL -> mouthfeel
    RatingField.AFTERTASTE -> aftertaste
    RatingField.OVERALL -> overall
}

/**
 * The "rated" definition: a cup counts as rated if any score (attribute,
 * overall, or notes) exists. Nothing gates the Done step on ratings.
 */
val CupRating.isRated: Boolean
    get() = aroma != null || flavor != null || acidity != null || sweetness != null ||
        mouthfeel != null || aftertaste != null || overall != null || notes.isNotBlank()

/** A session stored in the history: planned set-ups, finished runs, joined copies. */
data class SavedSession(
    val id: String,
    val createdAtMs: Long,
    val plan: CuppingPlan,
    /** Absent in old rows; legacy JSON decodes as DONE. */
    val status: SessionStatus = SessionStatus.DONE,
    /** Blind tasting: identities are hidden until the cups are identified and revealed. */
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

/**
 * Planned→done conversion (user decision 3): when a run started from a planned
 * session reaches Done, the result overwrites the SAME row — same id and
 * createdAt, status flips to DONE — carrying the plan the run actually used and
 * the ratings captured during it. No second artifact is ever created.
 *
 * The blind flag follows the plan the run used: editing a planned row and
 * toggling blind off converts the row into a non-blind session.
 */
fun SavedSession.toCompleted(
    plan: CuppingPlan,
    ratings: List<CupRating> = emptyList(),
): SavedSession = copy(plan = plan, status = SessionStatus.DONE, blind = plan.blind, ratings = ratings)

/**
 * Explicit blind reveal: identities become visible only by a deliberate action,
 * never automatically, and the reveal is not reversible. A second call (or a
 * call on a non-blind session) is a no-op, so revealedAtMs is set exactly once.
 */
fun SavedSession.reveal(nowMs: Long): SavedSession =
    if (blind && revealedAtMs == null) copy(revealedAtMs = nowMs) else this
