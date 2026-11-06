package app.cofcup.data

/**
 * The cupping timeline. 0:00 is the moment the first cup receives water.
 *
 * Windows follow the SCA cupping protocol and common home-cupping guides:
 * the crust stays unbroken for 4 minutes, the crust break happens at the
 * 4-minute mark, the surface is skimmed shortly after, first tasting starts
 * around 8 minutes after the pour (~70 °C), and the cups are re-tasted as they
 * cool, with a final (optional) pass near room temperature around 22-30 min.
 */
data class CuppingStep(
    val title: String,
    val instruction: String,
    /** When this step starts, in seconds from the first pour. */
    val startSec: Long,
    /** When this step ends, in seconds; null for the final "done" state. */
    val endSec: Long?,
) {
    val isLast: Boolean get() = endSec == null
}

object CuppingTimeline {

    const val STEEP_SECONDS = 4L * 60        // crust stays unbroken for 4 min
    const val BREAK_END_SECONDS = 5L * 60    // break crust + smell for ~1 min
    const val SETTLE_END_SECONDS = 6L * 60   // grounds sink back for ~1 min
    const val CLEAN_END_SECONDS = 8L * 60    // skim, then cool towards ~70 °C
    const val ROUND1_END_SECONDS = 15L * 60  // first round: flavour/acidity/body
    const val ROUND2_END_SECONDS = 22L * 60  // second round: as cups cool further
    const val ROUND3_END_SECONDS = 30L * 60  // optional third round, room temp

    val steps: List<CuppingStep> = listOf(
        CuppingStep(
            title = "Fill the cups and let steep",
            instruction = "Pour water just off the boil (~93 °C) straight onto the grounds, " +
                "filling every cup to the brim. Start the timer with your very first pour, " +
                "and fill the cups in the same order you will taste them. " +
                "Then leave everything undisturbed while the crust forms.",
            startSec = 0L,
            endSec = STEEP_SECONDS,
        ),
        CuppingStep(
            title = "Break the crust (smell aromas)",
            instruction = "Put your nose close to the surface and push the crust back " +
                "three times with the back of a spoon. The released burst of aroma is the " +
                "strongest the coffee will give — note what you smell. " +
                "Rinse the spoon in hot water between cups.",
            startSec = STEEP_SECONDS,
            endSec = BREAK_END_SECONDS,
        ),
        CuppingStep(
            title = "Let grounds settle",
            instruction = "Give the cups a moment. The grounds that were pushed around " +
                "during the crust break should be allowed to sink back to the bottom " +
                "before you skim.",
            startSec = BREAK_END_SECONDS,
            endSec = SETTLE_END_SECONDS,
        ),
        CuppingStep(
            title = "Clean the top of the cup",
            instruction = "Skim off the foam and any floating grounds with one or two " +
                "spoons until the surface is clean, rinsing the spoons between cups. " +
                "Then let the coffee cool towards ~70 °C.",
            startSec = SETTLE_END_SECONDS,
            endSec = CLEAN_END_SECONDS,
        ),
        CuppingStep(
            title = "First cupping round",
            instruction = "Take half a spoonful and slurp it hard, so the coffee sprays " +
                "across your whole palate. At around 70 °C focus on aroma, flavour, " +
                "acidity, body and aftertaste. Go cup by cup, rinsing the spoon between " +
                "samples, and spit if you are tasting many coffees.",
            startSec = CLEAN_END_SECONDS,
            endSec = ROUND1_END_SECONDS,
        ),
        CuppingStep(
            title = "Second cupping round",
            instruction = "Taste down the row again while the cups cool further " +
                "(roughly 55 °C and falling). Sweetness becomes clearer, acidity opens " +
                "up, and defects start to show. Note what changes compared to the first round.",
            startSec = ROUND1_END_SECONDS,
            endSec = ROUND2_END_SECONDS,
        ),
        CuppingStep(
            title = "Optional third cupping round",
            instruction = "One more pass near room temperature, if you have time: this is " +
                "where faint flaws and the final aftertaste show up most clearly. " +
                "Skip it if the cups are too bland — the session moves to \"done\" either way.",
            startSec = ROUND2_END_SECONDS,
            endSec = ROUND3_END_SECONDS,
        ),
        CuppingStep(
            title = "Done",
            instruction = "Cupping complete. Check the coffee list below to identify " +
                "each cup by its colour and number. The session has been saved to your history.",
            startSec = ROUND3_END_SECONDS,
            endSec = null,
        ),
    )

    /** The step that is running at the given elapsed time (seconds since 0:00). */
    fun stepAt(elapsedSec: Long): CuppingStep =
        steps.last { elapsedSec >= it.startSec }

    /** The step after [step], or null when [step] is the final one. */
    fun nextStep(step: CuppingStep): CuppingStep? {
        val index = steps.indexOf(step)
        if (index < 0 || index == steps.size - 1) return null
        return steps[index + 1]
    }

    /** Seconds still to wait until [next] starts. */
    fun secondsUntil(elapsedSec: Long, next: CuppingStep): Long =
        (next.startSec - elapsedSec).coerceAtLeast(0)

    /** 0..1 progress of the countdown to the next step, for a progress ring. */
    fun countdownFraction(elapsedSec: Long, current: CuppingStep, next: CuppingStep): Float {
        val total = (next.startSec - current.startSec).coerceAtLeast(1)
        val remaining = secondsUntil(elapsedSec, next)
        return ((total - remaining).toFloat() / total).coerceIn(0f, 1f)
    }
}
