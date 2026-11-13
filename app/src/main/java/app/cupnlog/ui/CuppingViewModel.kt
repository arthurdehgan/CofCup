package app.cupnlog.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cupnlog.CupnlogApplication
import app.cupnlog.data.CoffeeEntry
import app.cupnlog.data.CupRating
import app.cupnlog.data.CuppingDraft
import app.cupnlog.data.CuppingPlan
import app.cupnlog.data.CuppingTimeline
import app.cupnlog.data.RatingField
import app.cupnlog.data.SavedSession
import app.cupnlog.data.SessionOrigin
import app.cupnlog.data.SessionStatus
import app.cupnlog.data.ShareCodec
import app.cupnlog.data.reveal
import app.cupnlog.data.matchAndReveal
import app.cupnlog.data.toCompleted
import app.cupnlog.data.withScore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The app's destinations. Kept simple: one sealed interface plus a screen switch. */
sealed interface Screen {
    data object Home : Screen
    data object Setup : Screen
    data object LabelCoffees : Screen

    /** `returnTo` keeps `handleBack()` sensible when StartCheck is opened
     *  directly (e.g. running a saved setup) instead of through the setup flow. */
    data class StartCheck(val returnTo: Screen? = null) : Screen

    data object LiveSession : Screen
    data object Ratings : Screen
    data object Settings : Screen
    data object History : Screen
    data class SessionDetail(val sessionId: String) : Screen

    /**
     * Blind identification for a saved row: assign each cup position the label
     * that was under it. `returnTo` keeps `handleBack()` sensible — the flow is
     * opened from the Finished live screen or from the session detail.
     */
    data class IdentifyCups(val sessionId: String, val returnTo: Screen) : Screen

    /**
     * Phase 3 share: renders the saved setup's QR (https App Link), with
     * share-as-text / copy-link / bare-code fallbacks. `returnTo` keeps
     * `handleBack()` sensible — the screen is opened from Home's saved-setup
     * chips (and later from Setup/StartCheck).
     */
    data class ShareSetup(val sessionId: String, val returnTo: Screen) : Screen

    /**
     * Phase 3 join: in-app QR scanner with the paste-code fallback below it.
     * Decoded payloads go through [CuppingViewModel.handleIncoming] and the
     * confirm preview — the scanner itself never saves anything.
     */
    data object Scanner : Screen

    /**
     * Phase 3 confirm preview: shows exactly what an incoming link / scan /
     * paste carries and asks before saving anything. The decoded payload (or
     * decode error) lives in the view model's `incoming` state, not here, so
     * deep-link errors reuse the same screen. `returnTo` (null → Home) keeps
     * `handleBack()` sensible.
     */
    data class ImportPreview(val returnTo: Screen?) : Screen
}

/**
 * The rating screen's entry modes. The choice is session-scoped view-model
 * state: leaving the Ratings screen and coming back keeps it, while every
 * fresh or reset session defaults back to BY_CUP. Both modes write the same
 * per-cup ratings — the mode is only the order of entry, never persisted.
 */
enum class RatingMode(val label: String) {
    BY_CUP("By cup"),
    BY_CATEGORY("By category"),
}

/** A session that has been set up but may not have started timing yet. */
data class LiveSession(
    val plan: CuppingPlan,
    /** Epoch (elapsedRealtime) when the timer was started; null until the first pour. */
    val startedAtMs: Long? = null,
    /** Offset in ms added by skipped steps. */
    val extraOffsetMs: Long = 0,
    /** True once the "Done" step was reached and the session saved. */
    val finished: Boolean = false,
    /**
     * Id of the planned session row this run came from; on Done, that row is
     * converted in place (same id/createdAt, status → done). Null for runs
     * started without a saved setup behind them.
     */
    val plannedSessionId: String? = null,
    /**
     * Id of the history row the Done save wrote (the converted planned row or
     * the fresh one). Set only once the save has landed; lets the Finished
     * screen and the Ratings-exit persist point at the exact saved row.
     */
    val savedSessionId: String? = null,
    /**
     * True for the quick-started empty session ("Start Cupping" from Home).
     * The pre-start screen shows its saved-setup picker only then: a run
     * started through the setup flow already IS a setup, and a planned run
     * already has its row. Sticky across setup picks so the picker stays
     * reachable to swap or cancel a pick.
     */
    val fromQuickStart: Boolean = false,
)

private fun Application.appRepository() =
    (this as? CupnlogApplication)?.repository
        ?: error("CuppingViewModel requires CupnlogApplication as the Application class")

class CuppingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.appRepository()

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _draft = MutableStateFlow(CuppingDraft())
    val draft: StateFlow<CuppingDraft> = _draft.asStateFlow()

    private val _live = MutableStateFlow<LiveSession?>(null)
    val live: StateFlow<LiveSession?> = _live.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _showLeaveDialog = MutableStateFlow(false)
    val showLeaveDialog: StateFlow<Boolean> = _showLeaveDialog.asStateFlow()

    /**
     * Ratings captured during the live run, keyed by cup position. They live
     * in the VM only (no keystroke writes): Done persists them, and leaving
     * the Ratings screen after Done re-persists them into the saved row.
     */
    private val _myRatings = MutableStateFlow<Map<Int, CupRating>>(emptyMap())
    val myRatings: StateFlow<Map<Int, CupRating>> = _myRatings.asStateFlow()

    /**
     * Which entry mode the Ratings screen was last left in ("Rate by cup"
     * vs "Rate by category"). Lives in the VM so the choice survives leaving
     * and re-entering the screen during a session; [resetLiveSession],
     * [startLiveSession] and [quickStartCupping] put it back to BY_CUP for a
     * fresh run.
     */
    private val _ratingMode = MutableStateFlow(RatingMode.BY_CUP)
    val ratingMode: StateFlow<RatingMode> = _ratingMode.asStateFlow()

    /**
     * In-progress blind identification for the saved row behind the current
     * IdentifyCups screen: cup position (0-based) → index into that row's
     * coffees. Lives in the VM only; [confirmMatch] persists it, while leaving
     * the screen or resetting the session discards it.
     */
    private val _pendingMatch = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val pendingMatch: StateFlow<Map<Int, Int>> = _pendingMatch.asStateFlow()

    /**
     * Id of the planned row the current StartCheck screen is about to run (set
     * when a saved-setup chip or a "Run this setup" button was tapped). While
     * this is non-null, StartCheck hides its "Save setup" action — the row
     * already exists and the run will convert it on Done.
     */
    private val _plannedRunSource = MutableStateFlow<String?>(null)
    val plannedRunSource: StateFlow<String?> = _plannedRunSource.asStateFlow()

    /**
     * Id of the planned row being edited: a Home-chip edit loads its draft and
     * remembers this, so a later "Save setup" updates the SAME row instead of
     * creating a duplicate. Cleared when a fresh setup is opened from Home.
     */
    private var editingPlannedSessionId: String? = null

    /**
     * Snapshot of the live session + draft before the first saved-setup pick
     * on the pre-start "Cupping" screen; restored by [clearSelectedSetupForLive]
     * when the pick is cancelled by tapping the selected chip again. Null until
     * the first pick; cleared again on cancel and when the session is reset.
     */
    private var liveBeforeSelection: Pair<LiveSession, CuppingDraft>? = null

    private val _stepChimes = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits every time the timer itself advances into a new step; skipped steps stay silent. */
    val stepChimes: SharedFlow<Unit> = _stepChimes.asSharedFlow()

    /** Start second of the last step whose entry was accounted for (chimed or skipped). */
    private var announcedStepStartSec = 0L

    val history: StateFlow<List<SavedSession>> =
        repository.sessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Timer-advance chime setting (stored, on by default). */
    val soundOn: StateFlow<Boolean> = repository.soundOn.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    /** Timer-advance vibration setting (stored, off by default). */
    val vibrateOn: StateFlow<Boolean> = repository.vibrateOn.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    /** This device's stable participant id (empty until first generated). */
    val myId: StateFlow<String> = repository.myId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "",
    )

    /** This device's participant display name (stamped into shared payloads). */
    val myName: StateFlow<String> = repository.myName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "",
    )

    fun setSoundOn(enabled: Boolean) {
        viewModelScope.launch { repository.setSoundOn(enabled) }
    }

    fun setVibrateOn(enabled: Boolean) {
        viewModelScope.launch { repository.setVibrateOn(enabled) }
    }

    /** This device's participant display name (stamped into shared payloads). */
    fun setMyName(name: String) {
        viewModelScope.launch { repository.setMyName(name) }
    }

    init {
        viewModelScope.launch {
            while (isActive) {
                refreshElapsed()
                delay(200)
            }
        }
        // Deep links land here from MainActivity (onCreate / onNewIntent), via
        // the application's pending-link holder. Everything routes through
        // [handleIncoming] — queueing during a live session, confirm preview
        // otherwise. Dedup by seq: every entry is handled at most once per
        // view model, even though all stacked instances share the same holder.
        // Plain Main (not the implicit Main.immediate): the link must be
        // handled AFTER the constructor finishes — an immediate dispatch would
        // run this collector inline while the later-declared state still
        // doesn't exist.
        viewModelScope.launch(Dispatchers.Main) {
            val app = getApplication<CupnlogApplication>()
            app.pendingIncomingLink.collect { link ->
                if (link != null && link.seq > handledLinkSeq) {
                    handledLinkSeq = link.seq
                    handleIncoming(link.text)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Draft editing (setup + labels)
    // ------------------------------------------------------------------

    fun setCupSize(sizeMl: Int) {
        _draft.update {
            it.copy(cupSizeMl = sizeMl.coerceIn(CuppingDraft.MIN_CUP_SIZE, CuppingDraft.MAX_CUP_SIZE))
        }
    }

    fun setCups(count: Int) {
        _draft.update { it.copy(cupCount = count.coerceIn(CuppingDraft.MIN_CUPS, CuppingDraft.MAX_CUPS)) }
    }

    fun setCoffeeName(index: Int, name: String) {
        _draft.update { draft ->
            draft.coffees.getOrNull(index) ?: return@update draft
            draft.copy(
                coffees = draft.coffees.mapIndexed { i, entry ->
                    if (i == index) entry.copy(name = name.take(40)) else entry
                }
            )
        }
    }

    fun setCoffeeLabel(index: Int, label: String) {
        _draft.update { draft ->
            draft.coffees.getOrNull(index) ?: return@update draft
            draft.copy(
                coffees = draft.coffees.mapIndexed { i, entry ->
                    if (i == index) entry.copy(label = label.take(CoffeeEntry.MAX_LABEL_LENGTH)) else entry
                }
            )
        }
    }

    fun setCoffeeColor(index: Int, colorArgb: Long) {
        _draft.update { draft ->
            draft.coffees.getOrNull(index) ?: return@update draft
            draft.copy(
                coffees = draft.coffees.mapIndexed { i, entry ->
                    if (i == index) entry.copy(colorArgb = colorArgb) else entry
                }
            )
        }
    }

    /** Optional display name of the cupping session (shown for saved setups). */
    fun setSessionName(name: String) {
        _draft.update { it.copy(sessionName = name.take(40)) }
    }

    /** Blind tasting toggle on the setup screen; the flag rides the draft into
     *  the plan (and from there into the saved session at save/conversion). */
    fun setBlind(enabled: Boolean) {
        _draft.update { it.copy(blind = enabled) }
    }

    // ------------------------------------------------------------------
    // In-session ratings (phase 2)
    // ------------------------------------------------------------------

    /** Number of cups in the live plan, or 0 when no session is live. */
    private fun liveCupCount(): Int = _live.value?.plan?.cupCount ?: 0

    /** The in-progress rating for a cup, or an unrated placeholder. */
    private fun ratingFor(cup: Int): CupRating =
        _myRatings.value[cup] ?: CupRating(cup = cup, raterId = "")

    /** Sets one score field (1–10) for one cup; edits live in the VM only. */
    fun setCupRatingScore(cup: Int, field: RatingField, value: Int) {
        if (cup !in 0 until liveCupCount()) return
        _myRatings.update {
            it + (cup to ratingFor(cup).withScore(field, value.coerceIn(1, 10)))
        }
    }

    /** Remembers the Ratings screen's entry mode for this session. */
    fun setRatingMode(mode: RatingMode) {
        _ratingMode.value = mode
    }

    /** Sets a cup's free-text notes, capped at 500 characters. */
    fun setCupRatingNotes(cup: Int, notes: String) {
        if (cup !in 0 until liveCupCount()) return
        _myRatings.update { it + (cup to ratingFor(cup).copy(notes = notes.take(500))) }
    }

    /** Drops a cup's rating entirely (back to "not rated"). */
    fun clearCupRating(cup: Int) {
        _myRatings.update { it - cup }
    }

    // ------------------------------------------------------------------
    // Blind identification (match cups to labels)
    // ------------------------------------------------------------------

    /**
     * Opens the identification screen for a saved row. A row that is already
     * revealed (re-matching) starts from its current assignment, so fixing one
     * mistaken cup is a single change; a fresh match starts empty — the cups
     * were mixed, so no position is known yet.
     */
    fun openIdentifyCups(sessionId: String, returnTo: Screen) {
        _screen.value = Screen.IdentifyCups(sessionId = sessionId, returnTo = returnTo)
        viewModelScope.launch {
            val session = repository.sessions.first().firstOrNull { it.id == sessionId }
                ?: return@launch
            // Joined copies get their identification from the host, never here.
            if (session.origin == app.cupnlog.data.SessionOrigin.JOINED) return@launch
            if (session.revealedAtMs != null) {
                _pendingMatch.value = session.plan.coffees.indices.associateWith { it }
            }
        }
    }

    /** Assigns a coffee (by index) to a cup position, moving it off any other position. */
    fun assignCupToCoffee(cup: Int, coffeeIndex: Int) {
        if (cup < 0 || coffeeIndex < 0) return
        _pendingMatch.update { current ->
            current.filterValues { it != coffeeIndex } + (cup to coffeeIndex)
        }
    }

    /** Clears one cup position's assignment. */
    fun clearCupAssignment(cup: Int) {
        _pendingMatch.update { it - cup }
    }

    /**
     * Confirms the identification: applies the permutation and stamps the
     * reveal (first match), or re-permutes an already-revealed row (re-match,
     * `revealedAtMs` untouched). No-op unless every cup position carries a
     * distinct in-range coffee — [matchAndReveal] validates the bijection too.
     */
    fun confirmMatch(sessionId: String) {
        val match = _pendingMatch.value
        viewModelScope.launch {
            val session = repository.sessions.first().firstOrNull { it.id == sessionId }
                ?: return@launch
            val n = session.plan.cupCount
            if ((0 until n).any { match[it] == null } ||
                match.values.any { it !in session.plan.coffees.indices } ||
                match.values.distinct().size != n
            ) {
                return@launch
            }
            val cupToCoffee = (0 until n).map { match.getValue(it) }
            repository.save(session.matchAndReveal(cupToCoffee, System.currentTimeMillis()))
            _pendingMatch.value = emptyMap()
            // Back to wherever the identification was opened from (the Finished
            // live screen or the session detail); a stale screen falls back to
            // History.
            _screen.value =
                (screen.value as? Screen.IdentifyCups)?.takeIf { it.sessionId == sessionId }?.returnTo
                    ?: Screen.History
        }
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    fun openLabelCoffees() {
        _screen.value = Screen.LabelCoffees
    }

    /** "Set up cupping" on the home screen — a fresh setup, no planned row behind it.
     *  The draft is reset to the defaults: a stale draft from a saved-setup
     *  edit/run must not leak its coffees into the new setup. */
    fun openSetup() {
        editingPlannedSessionId = null
        _plannedRunSource.value = null
        _draft.value = CuppingDraft()
        _screen.value = Screen.Setup
    }

    fun openStartCheck() {
        // The guided flow reaches StartCheck through LabelCoffees; a planned-run
        // StartCheck is opened directly by [startPlannedRun] and never gets here.
        _plannedRunSource.value = null
        _screen.value = Screen.StartCheck(returnTo = Screen.LabelCoffees)
    }

    fun openHistory() {
        _screen.value = Screen.History
    }

    fun openSessionDetail(sessionId: String) {
        _screen.value = Screen.SessionDetail(sessionId)
    }

    /** Gear icon on the live-session screens. */
    fun openSettings() {
        _screen.value = Screen.Settings
    }

    /** "Rate" chip on the live-session screens. */
    fun openRatings() {
        _screen.value = Screen.Ratings
    }

    /** "Share" on a saved-setup chip: renders the setup's QR (phase 3). */
    fun openShareSetup(sessionId: String) {
        _screen.value = Screen.ShareSetup(sessionId = sessionId, returnTo = Screen.Home)
    }

    /** "Join a cupping" on Home: in-app scanner, paste-code fallback below it. */
    fun openScanner() {
        _screen.value = Screen.Scanner
    }

    /**
     * System/hardware back. Returns true when the press was handled, false when
     * the caller should let the activity finish.
     */
    fun handleBack(): Boolean {
        return when (val current = _screen.value) {
            Screen.Home -> false
            Screen.Setup -> {
                _screen.value = Screen.Home
                true
            }
            Screen.LabelCoffees -> {
                _screen.value = Screen.Setup
                true
            }
            is Screen.StartCheck -> {
                val fromPlannedRun = _plannedRunSource.value != null
                if (fromPlannedRun) {
                    // "Back" on a planned-run check is not the discard contract
                    // that ends a session — it simply leaves the run, and the
                    // planned row stays intact for later.
                    _plannedRunSource.value = null
                }
                // Leaving StartCheck backwards abandons any unstarted run
                // still in memory (e.g. after returning here from the
                // pre-start screen); a fresh start recreates it from the draft.
                _live.value?.let { if (it.startedAtMs == null && !it.finished) resetLiveSession() }
                _screen.value = current.returnTo ?: Screen.LabelCoffees
                true
            }
            Screen.LiveSession -> {
                val session = _live.value
                val startedAndNotFinished =
                    session != null && session.startedAtMs != null && !session.finished
                val freshUnstartedSetup =
                    session != null && session.startedAtMs == null && !session.finished &&
                        session.plan.cupCount > 0 && session.plannedSessionId == null
                when {
                    startedAndNotFinished -> _showLeaveDialog.value = true
                    // Back from an unstarted run that carries a fresh setup
                    // (the guided flow's just-created one) returns to the
                    // ready-to-cup screen, where the setup can still be saved
                    // — nothing is discarded yet.
                    freshUnstartedSetup ->
                        _screen.value = Screen.StartCheck(returnTo = Screen.LabelCoffees)
                    else -> discardLiveSession()
                }
                true
            }
            Screen.Settings -> {
                _screen.value = Screen.LiveSession
                true
            }
            Screen.Ratings -> {
                // Leaving Ratings after Done re-persists the edits (the Done
                // save has already landed); otherwise nothing is written here.
                persistMyRatingsIfFinished()
                _screen.value = Screen.LiveSession
                true
            }
            Screen.History -> {
                _screen.value = Screen.Home
                true
            }
            is Screen.SessionDetail -> {
                _screen.value = Screen.History
                true
            }
            is Screen.IdentifyCups -> {
                // Backing out discards the pending identification; the row
                // stays exactly as it was (blind until a confirmed match).
                _pendingMatch.value = emptyMap()
                _screen.value = current.returnTo
                true
            }
            is Screen.ShareSetup -> {
                _screen.value = current.returnTo
                true
            }
            Screen.Scanner -> {
                _screen.value = Screen.Home
                true
            }
            is Screen.ImportPreview -> {
                // Back / cancel: nothing was saved.
                _incoming.value = null
                _screen.value = current.returnTo ?: Screen.Home
                true
            }
        }
    }

    /** Back from an unstarted session, or confirmed "End & discard" — land on Home. */
    fun discardLiveSession() {
        resetLiveSession()
        _screen.value = Screen.Home
        popQueuedIncoming()
    }

    fun dismissLeaveDialog() {
        _showLeaveDialog.value = false
    }

    // ------------------------------------------------------------------
    // Live session
    // ------------------------------------------------------------------

    /** Called from the "Start cupping" button on the pre-start screen. */
    fun startLiveSession() {
        val current = _live.value
        // (Re)create for every (re)start of an unstarted run: returning from
        // the pre-start screen to StartCheck and starting again must build
        // the session from the draft as it is now. A running or finished
        // session is never touched here.
        if (current == null || current.startedAtMs == null) {
            _live.value = LiveSession(
                plan = _draft.value.toPlan(),
                // Run started from a planned row: Done converts that row in place.
                plannedSessionId = _plannedRunSource.value ?: editingPlannedSessionId,
            )
            _elapsedMs.value = 0
            announcedStepStartSec = 0
            _myRatings.value = emptyMap()
            _ratingMode.value = RatingMode.BY_CUP
        }
        _screen.value = Screen.LiveSession
    }

    /** Called from the big "Start" button: 0:00 = first pour. */
    fun beginTimer() {
        _live.update { session ->
            if (session == null || session.startedAtMs != null) session
            else session.copy(startedAtMs = SystemClock.elapsedRealtime())
        }
    }

    /** "Start Cupping" on the home screen: straight to the timer, with no cups set up.
     *  A session completed this way is stored in history as an "Empty Cupping Session". */
    fun quickStartCupping() {
        val current = _live.value
        // Quick-start has no saved setup behind it, ever; a stale unstarted
        // run left over from earlier navigation is replaced, not resurrected.
        if (current == null || current.startedAtMs == null) {
            _plannedRunSource.value = null
            _live.value = LiveSession(
                plan = CuppingPlan(
                    cupSizeMl = _draft.value.cupSizeMl,
                    cupCount = 0,
                    coffees = emptyList(),
                ),
                fromQuickStart = true,
            )
            _elapsedMs.value = 0
            announcedStepStartSec = 0
            _myRatings.value = emptyMap()
            _ratingMode.value = RatingMode.BY_CUP
        }
        _screen.value = Screen.LiveSession
    }

    /** Advances the timeline to the start of the next step. */
    fun skipStep() {
        val elapsedMs = _elapsedMs.value
        val current = CuppingTimeline.stepAt(elapsedMs / 1000)
        val next = CuppingTimeline.nextStep(current) ?: return
        val targetMs = next.startSec * 1000
        if (targetMs <= elapsedMs) return
        _live.update { it?.copy(extraOffsetMs = it.extraOffsetMs + (targetMs - elapsedMs)) }
        _elapsedMs.value = targetMs
        // The chime is reserved for timer-driven advances; account for the skipped
        // step here so the ticker stays silent about it.
        announcedStepStartSec = next.startSec
    }

    /** Saves the session exactly once when the "Done" step becomes current.
     *  Called from the ViewModel's ticker (via [refreshElapsed]) so the save
     *  happens no matter which session screen is on top. */
    fun onCuppingComplete() {
        val session = _live.value ?: return
        if (session.finished) return
        _live.update { it?.copy(finished = true) }
        val plannedId = session.plannedSessionId
        viewModelScope.launch {
            val myId = repository.getOrCreateMyId()
            val myName = repository.myName.first()
            val ratings = sessionRatings(myId, myName)
            // Done with a saved setup behind the run: convert that planned row
            // in place (same id, same createdAt, status → done). If the row was
            // deleted meanwhile, fall back to a fresh history row.
            val planned = plannedId?.let { id ->
                repository.sessions.first()
                    .firstOrNull { it.id == id && it.status == SessionStatus.PLANNED }
            }
            val saved = planned?.toCompleted(session.plan, ratings)
                ?: SavedSession(
                    id = UUID.randomUUID().toString(),
                    createdAtMs = System.currentTimeMillis(),
                    plan = session.plan,
                    blind = session.plan.blind,
                    ratings = ratings,
                )
            repository.save(saved)
            // Remember the saved row so the Finished screen and the Ratings-exit
            // persist can target it.
            _live.update { it?.copy(savedSessionId = saved.id) }
        }
    }

    /** Maps the VM-held ratings into history entries for this device's rater,
     *  ordered by cup position and bounded to the live plan's cup count. */
    private fun sessionRatings(raterId: String, rater: String): List<CupRating> =
        _myRatings.value
            .filterKeys { it in 0 until liveCupCount() }
            .entries.sortedBy { it.key }
            .map { (cup, rating) -> rating.copy(cup = cup, raterId = raterId, rater = rater) }

    /**
     * Ratings-screen exit after Done already saved: writes the VM-held edits
     * into the saved row, replacing this device's previous entries per
     * raterId (idempotent — the same replace-per-rater rule the QR merge uses).
     * No-op while the session is still running: there, Done does the writing.
     */
    private fun persistMyRatingsIfFinished() {
        val session = _live.value ?: return
        if (!session.finished) return
        val savedId = session.savedSessionId ?: return
        viewModelScope.launch {
            val myId = repository.getOrCreateMyId()
            val myName = repository.myName.first()
            val mine = sessionRatings(myId, myName)
            val saved = repository.sessions.first().firstOrNull { it.id == savedId }
                ?: return@launch
            val merged = saved.ratings
                .filterNot { it.raterId == myId }
                .plus(mine)
                .sortedBy { it.cup }
            repository.save(saved.copy(ratings = merged))
        }
    }

    /**
     * Explicit blind reveal for a saved session (never automatic; not
     * reversible — see [reveal]). Only meaningful for own sessions: joined
     * copies get their reveal from the host's broadcast (phase 4).
     */
    fun revealBlind(sessionId: String) {
        viewModelScope.launch {
            val session = repository.sessions.first()
                .firstOrNull { it.id == sessionId }
                ?: return@launch
            if (session.origin == app.cupnlog.data.SessionOrigin.JOINED) return@launch
            repository.save(session.reveal(System.currentTimeMillis()))
        }
    }
    fun goHomeAfterSession() {
        resetLiveSession()
        _screen.value = Screen.Home
        popQueuedIncoming()
    }

    fun goHistoryAfterSession() {
        resetLiveSession()
        _screen.value = Screen.History
        popQueuedIncoming()
    }

    // ------------------------------------------------------------------
    // Sharing & joining via QR (phase 3)
    // ------------------------------------------------------------------

    /** What an incoming link / scan / paste carried, decoded. */
    sealed interface IncomingShare {
        data class Setup(val setup: ShareCodec.SharedSetup) : IncomingShare
        data class Error(val error: ShareCodec.ShareError) : IncomingShare
    }

    /** The payload currently behind the ImportPreview screen, if any. */
    private val _incoming = MutableStateFlow<IncomingShare?>(null)
    val incoming: StateFlow<IncomingShare?> = _incoming.asStateFlow()

    /**
     * An import recognized while a live session was on screen. Never replaces
     * the live session: a dialog offers to open it now or after the session
     * ([popQueuedIncoming] surfaces it when the session surface is left).
     * The prompt has its own visibility flag — "not now" closes the dialog
     * while the payload stays queued for the session's end.
     */
    private val _queuedIncoming = MutableStateFlow<IncomingShare?>(null)
    val queuedIncoming: StateFlow<IncomingShare?> = _queuedIncoming.asStateFlow()

    private val _showQueuedDialog = MutableStateFlow(false)
    val showQueuedDialog: StateFlow<Boolean> = _showQueuedDialog.asStateFlow()

    /** A last scanned code that wasn't a CofCup share (plain hint on the scanner). */
    private val _scanHint = MutableStateFlow<String?>(null)
    val scanHint: StateFlow<String?> = _scanHint.asStateFlow()

    /** Seq of the last incoming link this view model handled (see the init collect). */
    private var handledLinkSeq = 0L

    /**
     * The one entry point for every incoming form (deep link, scanner, paste).
     * Recognized payloads route to the confirm preview — or queue with a
     * prompt when a live session is on screen (never replacing the timer).
     * Non-CofCup codes are ignored (they only ever reach here from the
     * scanner/paste, where the plain hint is shown in place).
     *
     * @return true when the text was recognized as a CofCup payload (valid
     *   or carrying a CofCup-specific error), false to keep scanning.
     */
    fun handleIncoming(text: String): Boolean {
        val share: IncomingShare = when (val result = ShareCodec.decode(text)) {
            is ShareCodec.DecodeResult.Success -> IncomingShare.Setup(result.setup)
            is ShareCodec.DecodeResult.Failure -> {
                if (result.error == ShareCodec.ShareError.NOT_A_COFCUP_CODE) {
                    _scanHint.value = result.error.message
                    return false
                }
                IncomingShare.Error(result.error)
            }
        }
        _scanHint.value = null
        if (_live.value != null) {
            _queuedIncoming.value = share
            _showQueuedDialog.value = true
        } else {
            showIncoming(share, returnTo = _screen.value.takeIf { it !is Screen.ImportPreview })
        }
        return true
    }

    /** Opens the confirm preview for an import (never auto-saves anything). */
    private fun showIncoming(share: IncomingShare, returnTo: Screen?) {
        _incoming.value = share
        _screen.value = Screen.ImportPreview(returnTo = returnTo)
    }

    /** Dialog action "Open now" on a queued import. */
    fun openQueuedIncoming() {
        val queued = _queuedIncoming.value ?: return
        _queuedIncoming.value = null
        _showQueuedDialog.value = false
        showIncoming(queued, returnTo = Screen.LiveSession)
    }

    /** Dialog action "After this session" — the import stays queued. */
    fun dismissQueuedIncoming() {
        _showQueuedDialog.value = false
    }

    /**
     * Called when the live-session surface is left (discard, or Home/History
     * after a finished session): a queued import now shows its confirm
     * preview, exactly as the prompt promised.
     */
    private fun popQueuedIncoming() {
        val queued = _queuedIncoming.value ?: return
        _queuedIncoming.value = null
        showIncoming(queued, returnTo = Screen.Home)
    }

    /**
     * Confirm on the import preview: saves the setup as a `planned` / `joined`
     * row of its own (own UUID in `id`, the host's id in `sessionRef`).
     * Blind payloads carry no identities, so the joined copy holds blank
     * coffees rendered as Cup 1…N until the host's reveal lands (phase 4).
     * Re-joining the same host session replaces the previous joined PLANNED
     * copy (idempotent, keyed on `sessionRef` + `status=planned`).
     */
    fun confirmImport() {
        val setup = (_incoming.value as? IncomingShare.Setup)?.setup ?: return
        viewModelScope.launch {
            val plan = CuppingPlan(
                cupSizeMl = setup.cupSizeMl,
                cupCount = setup.cupCount,
                coffees = setup.coffees.take(setup.cupCount),
                sessionName = setup.sessionName,
                blind = setup.blind,
            )
            val existing = repository.sessions.first().firstOrNull {
                it.origin == SessionOrigin.JOINED &&
                    it.sessionRef == setup.hostSessionId &&
                    it.status == SessionStatus.PLANNED
            }
            val row = (existing ?: SavedSession(
                id = UUID.randomUUID().toString(),
                createdAtMs = System.currentTimeMillis(),
                plan = plan,
                status = SessionStatus.PLANNED,
                blind = setup.blind,
                origin = SessionOrigin.JOINED,
                hostName = setup.hostName,
                sessionRef = setup.hostSessionId,
            )).copy(plan = plan, blind = setup.blind)
            repository.save(row)
            _incoming.value = null
            _screen.value = (_screen.value as? Screen.ImportPreview)?.returnTo ?: Screen.Home
        }
    }

    /** Cancel / back on the import preview: nothing was saved. */
    fun dismissImport() {
        _incoming.value = null
        _screen.value = (_screen.value as? Screen.ImportPreview)?.returnTo ?: Screen.Home
    }

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    fun deleteSession(sessionId: String) {
        viewModelScope.launch { repository.delete(sessionId) }
    }

    // ------------------------------------------------------------------
    // Planned sessions (saved setups)
    // ------------------------------------------------------------------

    /** Serializes the current draft into a `planned` row. When the draft was
     *  loaded from a planned chip edit, the SAME row is updated instead.
     *  Returns the saved row's id (used by the save-then-share flow). */
    private suspend fun persistDraftAsPlanned(): String {
        val plan = _draft.value.toPlan()
        val editingId = editingPlannedSessionId
        val original = if (editingId != null) {
            repository.sessions.first().firstOrNull { it.id == editingId }
        } else {
            null
        }
        val session = original
            ?: SavedSession(
                id = UUID.randomUUID().toString(),
                createdAtMs = System.currentTimeMillis(),
                plan = plan,
                status = SessionStatus.PLANNED,
            )
        // The plan the draft holds is authoritative for blind too: editing a
        // planned row and toggling blind off updates the SAME row's flag.
        val row = session.copy(plan = plan, status = SessionStatus.PLANNED, blind = plan.blind)
        repository.save(row)
        return row.id
    }

    fun saveSetup() {
        viewModelScope.launch {
            persistDraftAsPlanned()
            editingPlannedSessionId = null
            // Saving leaves for Home: any unstarted run still holding this
            // draft in memory is void now — quick-starting later must not
            // resurrect it.
            _live.value?.let { if (it.startedAtMs == null && !it.finished) resetLiveSession() }
            _screen.value = Screen.Home
        }
    }

    /**
     * "Share setup" on Ready to cup. Running an existing saved setup: its row
     * exists — share that id directly (no duplicate row). Otherwise save the
     * draft first (same-row overwrite on an edit, a new planned row otherwise)
     * and share the saved row. Back/Done on the share screen returns to the
     * Ready-to-cup screen this was opened from.
     */
    fun shareSetupFromStartCheck() {
        val current = _screen.value
        val source = _plannedRunSource.value
        if (source != null) {
            _screen.value = Screen.ShareSetup(sessionId = source, returnTo = current)
            return
        }
        viewModelScope.launch {
            val id = persistDraftAsPlanned()
            editingPlannedSessionId = null
            // The draft is persisted: void any unstarted run still holding it
            // in memory (same contract as [saveSetup]).
            _live.value?.let { if (it.startedAtMs == null && !it.finished) resetLiveSession() }
            _screen.value = Screen.ShareSetup(sessionId = id, returnTo = current)
        }
    }

    /** Tap on a Home saved-setup chip (or "Run this setup" on the detail):
     *  loads the planned plan into the draft and jumps straight to StartCheck,
     *  with its existing "one big Start button" contract. */
    fun startPlannedRun(sessionId: String, backTo: Screen = Screen.Home) {
        if (_live.value != null) return
        viewModelScope.launch {
            val session = repository.sessions.first()
                .firstOrNull { it.id == sessionId && it.status == SessionStatus.PLANNED }
                ?: return@launch
            _draft.value = CuppingDraft(
                cupSizeMl = session.plan.cupSizeMl,
                cupCount = session.plan.cupCount,
                coffees = session.plan.coffees,
                sessionName = session.plan.sessionName,
                blind = session.blind,
            )
            editingPlannedSessionId = null
            _plannedRunSource.value = sessionId
            _screen.value = Screen.StartCheck(returnTo = backTo)
        }
    }

    /**
     * On the pre-start "Cupping" screen (e.g. right after a quick start, which
     * has no cups set up), the user can still pick a saved setup: its plan
     * replaces the live one, and Done later converts that planned row in place
     * — the same contract as running it from a Home chip. Only possible before
     * the timer starts; ignored once the session is running or finished.
     * Picking another chip swaps the plan and re-points the Done conversion at
     * it (a previously picked row simply stays planned); tapping the already
     * selected chip cancels the pick (see [clearSelectedSetupForLive]).
     */
    fun selectPlannedSetupForLive(sessionId: String) {
        val session = _live.value ?: return
        if (session.startedAtMs != null || session.finished) return
        // Remember the session as it was before the first pick, so a later
        // tap on the selected chip can restore it exactly.
        if (liveBeforeSelection == null) liveBeforeSelection = session to _draft.value
        viewModelScope.launch {
            val planned = repository.sessions.first()
                .firstOrNull { it.id == sessionId && it.status == SessionStatus.PLANNED }
                ?: return@launch
            // Normalize through the draft/toPlan contract (coffees = take(cupCount)).
            val plan = CuppingDraft(
                cupSizeMl = planned.plan.cupSizeMl,
                cupCount = planned.plan.cupCount,
                coffees = planned.plan.coffees,
                sessionName = planned.plan.sessionName,
                blind = planned.blind,
            ).toPlan()
            _draft.value = CuppingDraft(
                cupSizeMl = plan.cupSizeMl,
                cupCount = plan.cupCount,
                coffees = plan.coffees,
                sessionName = plan.sessionName,
                blind = plan.blind,
            )
            // A swapped plan invalidates any ratings keyed by cup position.
            _myRatings.value = emptyMap()
            _live.update { live ->
                if (live == null || live.startedAtMs != null || live.finished) live
                else live.copy(plan = plan, plannedSessionId = sessionId)
            }
        }
    }

    /**
     * Tapping the already-selected setup again on the pre-start "Cupping"
     * screen cancels the pick: the live session (and draft) are restored to
     * exactly the state before the first pick — the empty plan for a
     * quick-started session, so Done again saves an "Empty Cupping Session".
     * Ignored when nothing was picked from that screen (e.g. the setup came
     * from a Home chip) or once the timer has started.
     */
    fun clearSelectedSetupForLive() {
        val (restoredSession, restoredDraft) = liveBeforeSelection ?: return
        val current = _live.value ?: return
        if (current.startedAtMs != null || current.finished) return
        _live.value = restoredSession
        _draft.value = restoredDraft
        liveBeforeSelection = null
        // The pick may have swapped the plan; position-keyed ratings are void.
        _myRatings.value = emptyMap()
    }

    /** Chip overflow "Edit": loads the planned plan into the draft and opens
     *  Setup, remembering the row so [saveSetup] overwrites it. */
    fun editPlannedSetup(sessionId: String) {
        viewModelScope.launch {
            val session = repository.sessions.first()
                .firstOrNull { it.id == sessionId && it.status == SessionStatus.PLANNED }
                ?: return@launch
            _draft.value = CuppingDraft(
                cupSizeMl = session.plan.cupSizeMl,
                cupCount = session.plan.cupCount,
                coffees = session.plan.coffees,
                sessionName = session.plan.sessionName,
                blind = session.blind,
            )
            editingPlannedSessionId = sessionId
            _plannedRunSource.value = null
            _screen.value = Screen.Setup
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun resetLiveSession() {
        _live.value = null
        _elapsedMs.value = 0
        _showLeaveDialog.value = false
        _plannedRunSource.value = null
        liveBeforeSelection = null
        announcedStepStartSec = 0
        _myRatings.value = emptyMap()
        _ratingMode.value = RatingMode.BY_CUP
        _pendingMatch.value = emptyMap()
    }

    private fun refreshElapsed() {
        val session = _live.value ?: return
        val startedAt = session.startedAtMs ?: run {
            if (_elapsedMs.value != 0L) _elapsedMs.value = 0
            return
        }
        // The clock stops once the final "Done" step is reached: the session
        // ran for the timeline's length, and the screen may stay open longer.
        val doneStartMs = CuppingTimeline.steps.last().startSec * 1000
        val elapsed = (SystemClock.elapsedRealtime() - startedAt + session.extraOffsetMs)
            .coerceAtLeast(0)
            .coerceAtMost(doneStartMs)
        if (elapsed != _elapsedMs.value) _elapsedMs.value = elapsed
        announceChimeFor(elapsed)
        // Detect Done here in the ticker (an idempotent save-once): the session
        // is saved even when another session screen (Ratings, Settings, …) is
        // on top, since there is no screen watching for this moment anymore.
        if (CuppingTimeline.stepAt(elapsed / 1000).isLast) {
            onCuppingComplete()
        }
    }

    /** Chimes when the timer's own passage enters a new step (never for skips). */
    private fun announceChimeFor(elapsedMs: Long) {
        val stepStartSec = CuppingTimeline.stepAt(elapsedMs / 1000).startSec
        if (stepStartSec == announcedStepStartSec) return
        announcedStepStartSec = stepStartSec
        // 0:00 is the timer start itself, not a step advance — stay silent there.
        if (stepStartSec > 0) _stepChimes.tryEmit(Unit)
    }
}
