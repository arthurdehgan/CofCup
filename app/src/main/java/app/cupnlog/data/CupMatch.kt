package app.cupnlog.data

/**
 * Pure helpers for blind identification: after cupping, the user lifts each
 * cup, reads the label beneath it, and assigns that label to the position the
 * cup was cupped at. Ratings stay keyed to the cup position; confirming a
 * complete match reorders the coffees so index `i` is the coffee that sat at
 * position `i` — position and identity then agree, and names can be revealed.
 */

/** Indices of coffees whose label is blank or duplicates another (trim + case-insensitive). */
fun List<CoffeeEntry>.invalidLabelIndices(): Set<Int> {
    val seen = mutableMapOf<String, Int>()
    val invalid = mutableSetOf<Int>()
    forEachIndexed { index, coffee ->
        val key = coffee.label.trim().lowercase()
        if (key.isEmpty()) {
            invalid.add(index)
        } else {
            val first = seen.put(key, index)
            if (first != null) {
                invalid.add(first)
                invalid.add(index)
            }
        }
    }
    return invalid
}

/**
 * Applies the identification permutation: `cupToCoffee[position]` is the index
 * (into this list) of the coffee that was cupped at that position. The list
 * must be a bijection — every cup position covered, every coffee used once.
 */
fun List<CoffeeEntry>.reorderedByMatch(cupToCoffee: List<Int>): List<CoffeeEntry> {
    require(cupToCoffee.size == size) { "every cup position must be identified" }
    require(cupToCoffee.sorted() == indices.toList()) { "each coffee may be assigned only once" }
    return cupToCoffee.map { this[it] }
}

/**
 * Confirms the identification on a saved blind session: coffees are reordered
 * into cup positions and identities are revealed (`revealedAtMs` is stamped
 * exactly once, so a later re-match only re-permutes the coffees). No-op on
 * rows where matching never applies: non-blind sessions (identities were never
 * hidden) and planned rows (no cupped ratings to align — they reveal directly).
 */
fun SavedSession.matchAndReveal(cupToCoffee: List<Int>, nowMs: Long): SavedSession {
    if (!blind || status != SessionStatus.DONE || plan.cupCount == 0 ||
        plan.coffees.size != plan.cupCount
    ) {
        return this
    }
    return copy(
        plan = plan.copy(coffees = plan.coffees.reorderedByMatch(cupToCoffee)),
        revealedAtMs = revealedAtMs ?: nowMs,
    )
}
