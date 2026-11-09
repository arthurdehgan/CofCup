package app.cupnlog.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

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
        Screen.Settings -> SettingsScreen(vm)
        Screen.History -> HistoryScreen(vm)
        // `screen` is a delegated property, so copy it into a local for a stable smart cast.
        is Screen.SessionDetail -> SessionDetailScreen(vm, s.sessionId)
    }

    // Session shell: owns keep-screen-on, chime playback and the Done save for
    // the whole live session, across every screen it may surface through.
    SessionShell(vm)

    BackHandler {
        if (!vm.handleBack()) {
            (context as? Activity)?.finish()
        }
    }
}
