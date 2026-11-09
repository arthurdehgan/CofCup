package app.cupnlog

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
        setContent {
            CupnlogTheme {
                CuppingApp()
            }
        }
    }
}
