package app.cupnlog

import android.app.Application
import app.cupnlog.data.CuppingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CupnlogApplication : Application() {

    val repository: CuppingRepository by lazy { CuppingRepository(this) }

    /**
     * Incoming deep-link text, posted by MainActivity (onCreate / onNewIntent)
     * and consumed once by the view model. A StateFlow (not a SharedFlow) so a
     * cold-start link posted before the view model exists is still delivered.
     */
    private val _pendingIncomingLink = MutableStateFlow<String?>(null)

    /** The link awaiting handling, or null. */
    val pendingIncomingLink: StateFlow<String?> = _pendingIncomingLink.asStateFlow()

    /** Routes an incoming VIEW intent's data to the view model. */
    fun postIncomingLink(text: String?) {
        _pendingIncomingLink.value = text
    }

    /** Called by the view model once it took the link. */
    fun clearPendingIncomingLink() {
        _pendingIncomingLink.value = null
    }
}
