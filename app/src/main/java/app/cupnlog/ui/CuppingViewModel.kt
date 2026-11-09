package app.cupnlog.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cupnlog.CupnlogApplication
import app.cupnlog.data.CuppingDraft
import app.cupnlog.data.CuppingPlan
import app.cupnlog.data.CuppingTimeline
import app.cupnlog.data.SavedSession
import app.cupnlog.data.SessionStatus
import app.cupnlog.data.toCompleted
import java.util.UUID
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
    data object Settings : Screen
    data object History : Screen
    data class SessionDetail(val sessionId: String) : Screen
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

    fun setSoundOn(enabled: Boolean) {
        viewModelScope.launch { repository.setSoundOn(enabled) }
    }

    fun setVibrateOn(enabled: Boolean) {
        viewModelScope.launch { repository.setVibrateOn(enabled) }
    }

    init {
        viewModelScope.launch {
            while (isActive) {
                refreshElapsed()
                delay(200)
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

    fun setCoffeeNumber(index: Int, number: Int) {
        _draft.update { draft ->
            draft.coffees.getOrNull(index) ?: return@update draft
            draft.copy(
                coffees = draft.coffees.mapIndexed { i, entry ->
                    if (i == index) entry.copy(number = number.coerceIn(0, 99)) else entry
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
                _screen.value = current.returnTo ?: Screen.LabelCoffees
                true
            }
            Screen.LiveSession -> {
                val session = _live.value
                val startedAndNotFinished =
                    session != null && session.startedAtMs != null && !session.finished
                if (startedAndNotFinished) {
                    _showLeaveDialog.value = true
                } else {
                    discardLiveSession()
                }
                true
            }
            Screen.Settings -> {
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
        }
    }

    /** Back from an unstarted session, or confirmed "End & discard" — land on Home. */
    fun discardLiveSession() {
        resetLiveSession()
        _screen.value = Screen.Home
    }

    fun dismissLeaveDialog() {
        _showLeaveDialog.value = false
    }

    // ------------------------------------------------------------------
    // Live session
    // ------------------------------------------------------------------

    /** Called from the "Start cupping" button on the pre-start screen. */
    fun startLiveSession() {
        if (_live.value == null) {
            _live.value = LiveSession(
                plan = _draft.value.toPlan(),
                // Run started from a planned row: Done converts that row in place.
                plannedSessionId = _plannedRunSource.value ?: editingPlannedSessionId,
            )
            _elapsedMs.value = 0
            announcedStepStartSec = 0
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
        if (_live.value == null) {
            // Quick-start has no saved setup behind it, ever.
            _plannedRunSource.value = null
            _live.value = LiveSession(
                plan = CuppingPlan(
                    cupSizeMl = _draft.value.cupSizeMl,
                    cupCount = 0,
                    coffees = emptyList(),
                ),
            )
            _elapsedMs.value = 0
            announcedStepStartSec = 0
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
            // Done with a saved setup behind the run: convert that planned row
            // in place (same id, same createdAt, status → done). If the row was
            // deleted meanwhile, fall back to a fresh history row.
            val planned = plannedId?.let { id ->
                repository.sessions.first()
                    .firstOrNull { it.id == id && it.status == SessionStatus.PLANNED }
            }
            val saved = planned?.toCompleted(session.plan)
                ?: SavedSession(
                    id = UUID.randomUUID().toString(),
                    createdAtMs = System.currentTimeMillis(),
                    plan = session.plan,
                )
            repository.save(saved)
        }
    }
    fun goHomeAfterSession() {
        resetLiveSession()
        _screen.value = Screen.Home
    }

    fun goHistoryAfterSession() {
        resetLiveSession()
        _screen.value = Screen.History
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
     *  loaded from a planned chip edit, the SAME row is updated instead. */
    fun saveSetup() {
        val plan = _draft.value.toPlan()
        val editingId = editingPlannedSessionId
        viewModelScope.launch {
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
            repository.save(session.copy(plan = plan, status = SessionStatus.PLANNED))
            editingPlannedSessionId = null
            _screen.value = Screen.Home
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
            ).toPlan()
            _draft.value = CuppingDraft(
                cupSizeMl = plan.cupSizeMl,
                cupCount = plan.cupCount,
                coffees = plan.coffees,
                sessionName = plan.sessionName,
            )
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
    }

    private fun refreshElapsed() {
        val session = _live.value ?: return
        val startedAt = session.startedAtMs ?: run {
            if (_elapsedMs.value != 0L) _elapsedMs.value = 0
            return
        }
        val elapsed = (SystemClock.elapsedRealtime() - startedAt + session.extraOffsetMs)
            .coerceAtLeast(0)
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
