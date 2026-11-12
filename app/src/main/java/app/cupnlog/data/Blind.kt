package app.cupnlog.data

/**
 * Pure helpers for rendering blind sessions.
 *
 * Visible identity in a blind session is the cup's left-to-right position only:
 * "Cup 1…N". Names, colours and custom numbers are private metadata that appear
 * only after an explicit reveal, and must stay out of labels, content
 * descriptions and instruction copy. These helpers are the only route through
 * which blind surfaces render cup identity, so the leak audit can stay focused.
 */

/** A blind cup as shown to the user: a position, nothing else. */
data class BlindCupDisplay(
    /** 1-based left-to-right position in the plan. */
    val position: Int,
) {
    /** The only identity text a blind surface may show. */
    val label: String get() = "Cup $position"
}

/**
 * Renders every coffee in a plan as a position-only display object, dropping
 * names, colours and custom numbers entirely.
 */
fun List<CoffeeEntry>.blindedByPosition(): List<BlindCupDisplay> =
    indices.map { BlindCupDisplay(position = it + 1) }

/** Neutral instruction text for the Done step in a blind session. */
fun blindDoneInstruction(cupCount: Int): String =
    if (cupCount == 1) {
        "Identify cup 1 afterwards. Coffee names stay hidden until you reveal them."
    } else {
        "Identify each cup as 1–$cupCount by its position. Coffee names stay hidden " +
            "until you reveal them."
    }
