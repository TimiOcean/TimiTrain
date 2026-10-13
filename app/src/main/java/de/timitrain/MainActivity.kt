package de.timitrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.timitrain.ui.TimiTrainNav
import de.timitrain.ui.theme.TimiTrainTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimiTrainTheme {
                TimiTrainNav()
            }
        }
    }
}
