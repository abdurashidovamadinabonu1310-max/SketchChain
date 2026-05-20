package uz.ictschool.sketchchain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind status bar AND navigation bar so our background fills the whole screen
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
