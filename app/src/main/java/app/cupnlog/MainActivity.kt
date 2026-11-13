package app.cupnlog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.cupnlog.ui.CuppingApp
import app.cupnlog.ui.theme.CupnlogTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        forwardIncomingLink(intent)
        setContent {
            CupnlogTheme {
                CuppingApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        forwardIncomingLink(intent)
    }

    /**
     * Hands the intent's data string to the application holder — no parsing
     * here. The view model collects it and routes through the share codec
     * (queueing during a live session, confirm preview otherwise).
     */
    private fun forwardIncomingLink(intent: Intent?) {
        val data = intent?.dataString ?: return
        (application as CupnlogApplication).postIncomingLink(data)
    }
}
