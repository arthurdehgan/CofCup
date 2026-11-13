package app.cupnlog

import android.app.Application
import app.cupnlog.data.CuppingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An incoming deep link with a sequence number, so every consumer can dedup. */
data class IncomingLink(val seq: Long, val text: String)

class CupnlogApplication : Application() {

    val repository: CuppingRepository by lazy { CuppingRepository(this) }

    /**
     * Incoming deep-link text, posted by MainActivity (onCreate / onNewIntent).
     * A StateFlow holds the latest link so one posted before a view model
     * exists (cold start) is still delivered. The value is never cleared:
     * entries carry a monotonically increasing seq, and each view model
     * handles every entry with a seq above what it has already handled. With
     * several stacked activity instances each view model sees the same value —
     * clearing here would race and starve the one actually on screen.
     */
    private val _pendingIncomingLink = MutableStateFlow<IncomingLink?>(null)

    /** The link awaiting handling, or null. */
    val pendingIncomingLink: StateFlow<IncomingLink?> = _pendingIncomingLink.asStateFlow()

    /** Routes an incoming VIEW intent's data string for handling. */
    fun postIncomingLink(text: String?) {
        if (text.isNullOrBlank()) return
        _pendingIncomingLink.value = IncomingLink(seq = seq + 1, text = text)
        seq += 1
    }

    private var seq = 0L
}
