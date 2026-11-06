package app.cofcup.ui

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
    val screen by vm.screen.collectAsState()

    when (screen) {
        Screen.Setup -> SetupScreen(vm)
        Screen.LabelCoffees -> LabelScreen(vm)
        Screen.StartCheck -> StartCheckScreen(vm)
        Screen.LiveSession -> SessionScreen(vm)
        Screen.History -> HistoryScreen(vm)
        is Screen.SessionDetail -> SessionDetailScreen(vm, screen.sessionId)
    }

    BackHandler {
        if (!vm.handleBack()) {
            (LocalContext.current as? Activity)?.finish()
        }
    }
}
