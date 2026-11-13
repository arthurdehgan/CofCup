package app.cupnlog.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cupnlog.ui.components.ConfirmDialog

/** Root composable: switches between the app's screens based on the view model. */
@Composable
fun CuppingApp() {
    val vm: CuppingViewModel = viewModel()
    val context = LocalContext.current
    val screen by vm.screen.collectAsState()

    when (val s = screen) {
        Screen.Home -> HomeScreen(vm)
        Screen.Setup -> SetupScreen(vm)
        Screen.LabelCoffees -> LabelScreen(vm)
        is Screen.StartCheck -> StartCheckScreen(vm)
        Screen.LiveSession -> SessionScreen(vm)
        Screen.Ratings -> RatingsScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        Screen.History -> HistoryScreen(vm)
        // `screen` is a delegated property, so copy it into a local for a stable smart cast.
        is Screen.SessionDetail -> SessionDetailScreen(vm, s.sessionId)
        is Screen.IdentifyCups -> IdentifyCupsScreen(vm, s.sessionId)
        is Screen.ShareSetup -> ShareSetupScreen(vm, s.sessionId)
        Screen.Scanner -> ScannerScreen(vm)
        is Screen.ImportPreview -> ImportPreviewScreen(vm)
    }
    // Session shell: owns keep-screen-on, chime playback and the Done save for
    // the whole live session, across every screen it may surface through.
    SessionShell(vm)

    // A deep link recognized while a live session is on screen is queued, not
    // shown — the timer is never replaced. This prompt offers to open it now
    // or let it surface when the session is left (see popQueuedIncoming).
    val queuedIncoming by vm.queuedIncoming.collectAsState()
    if (queuedIncoming != null) {
        ConfirmDialog(
            title = "Open a CofCup link?",
            text = "A shared cupping setup was received. " +
                "You can look at it now or after this session.",
            confirmLabel = "Open now",
            onConfirm = { vm.openQueuedIncoming() },
            onDismiss = { vm.dismissQueuedIncoming() },
        )
    }

    BackHandler {
        if (!vm.handleBack()) {
            (context as? Activity)?.finish()
        }
    }
}
