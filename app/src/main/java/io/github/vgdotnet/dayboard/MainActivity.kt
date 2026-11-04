package io.github.vgdotnet.dayboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.vgdotnet.dayboard.ui.DayBoardApp
import io.github.vgdotnet.dayboard.ui.theme.DayBoardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DayBoardTheme {
                DayBoardApp()
            }
        }
    }
}
