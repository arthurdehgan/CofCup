package app.cupnlog.data

/**
 * Pure helpers for rendering blind sessions.
 *
 * Visible identity in a blind session is the cup's left-to-right position only:
 * "Cup 1…N". Names, colours and labels are private metadata that appear only
 * once the cups have been identified and revealed, and must stay out of labels,
 * content descriptions and instruction copy. These helpers are the only route
 * through which blind surfaces render cup identity, so the leak audit can stay
 * focused.
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
 * names, colours and labels entirely.
 */
fun List<CoffeeEntry>.blindedByPosition(): List<BlindCupDisplay> =
    indices.map { BlindCupDisplay(position = it + 1) }

/** Neutral instruction text for the Done step in a blind session. */
fun blindDoneInstruction(cupCount: Int): String =
    if (cupCount == 1) {
        "Lift the cup and read the label beneath it; you'll identify it in the app " +
            "after cupping. Coffee names stay hidden until then."
    } else {
        "Lift each cup and read the label beneath it; you'll identify cups 1–$cupCount " +
            "in the app after cupping. Coffee names stay hidden until then."
    }
