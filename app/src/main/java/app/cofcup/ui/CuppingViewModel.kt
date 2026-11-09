package app.cofcup.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cofcup.CofcupApplication
import app.cofcup.data.CuppingDraft
import app.cofcup.data.CuppingPlan
import app.cofcup.data.CuppingTimeline
import app.cofcup.data.SavedSession
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The app's destinations. Kept simple: one sealed interface plus a screen switch. */
sealed interface Screen {
    data object Setup : Screen
    data object LabelCoffees : Screen
    data object StartCheck : Screen
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
)

private fun Application.appRepository() =
    (this as? CofcupApplication)?.repository
        ?: error("CuppingViewModel requires CofcupApplication as the Application class")

class CuppingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.appRepository()

    private val _screen = MutableStateFlow<Screen>(Screen.Setup)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _draft = MutableStateFlow(CuppingDraft())
    val draft: StateFlow<CuppingDraft> = _draft.asStateFlow()

    private val _live = MutableStateFlow<LiveSession?>(null)
    val live: StateFlow<LiveSession?> = _live.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _showLeaveDialog = MutableStateFlow(false)
    val showLeaveDialog: StateFlow<Boolean> = _showLeaveDialog.asStateFlow()

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

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    fun openLabelCoffees() {
        _screen.value = Screen.LabelCoffees
    }

    fun openStartCheck() {
        _screen.value = Screen.StartCheck
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
            Screen.Setup -> false
            Screen.LabelCoffees -> {
                _screen.value = Screen.Setup
                true
            }
            Screen.StartCheck -> {
                _screen.value = Screen.LabelCoffees
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
                _screen.value = Screen.Setup
                true
            }
            is Screen.SessionDetail -> {
                _screen.value = Screen.History
                true
            }
        }
    }

    /** Confirmed from the leave-session dialog. */
    fun discardLiveSession() {
        resetLiveSession()
        _screen.value = Screen.Setup
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
            _live.value = LiveSession(plan = _draft.value.toPlan())
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

    /** Called when the "Done" step becomes current; saves the session once. */
    fun onCuppingComplete() {
        val session = _live.value ?: return
        if (session.finished) return
        _live.update { it?.copy(finished = true) }
        val saved = SavedSession(
            id = UUID.randomUUID().toString(),
            createdAtMs = System.currentTimeMillis(),
            plan = session.plan,
        )
        viewModelScope.launch { repository.save(saved) }
    }

    fun goHomeAfterSession() {
        resetLiveSession()
        _screen.value = Screen.Setup
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
    // Internals
    // ------------------------------------------------------------------

    private fun resetLiveSession() {
        _live.value = null
        _elapsedMs.value = 0
        _showLeaveDialog.value = false
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
