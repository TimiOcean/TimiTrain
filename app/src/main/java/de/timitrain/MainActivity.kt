package de.timitrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.timitrain.ui.TimiTrainNav
import de.timitrain.ui.theme.TimiTrainTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TimiTrainApp
        setContent {
            val settings by app.repository.observeSettings().collectAsState(initial = null)
            TimiTrainTheme(darkTheme = settings?.darkMode == true) {
                TimiTrainNav()
            }
        }
    }
}
