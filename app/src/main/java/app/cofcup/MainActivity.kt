package app.cofcup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.cofcup.ui.CuppingApp
import app.cofcup.ui.theme.CofcupTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CofcupTheme {
                CuppingApp()
            }
        }
    }
}
