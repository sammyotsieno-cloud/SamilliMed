package org.samillimed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.samillimed.app.ui.splash.SamilliSplashHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()   // system splash (plain green) hands over to the animation
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()      // splash must cover the status bar and camera cut-out
        setContent {
            MaterialTheme {
                // Plays only on a fresh launch (savedInstanceState is null), not after rotation
                SamilliSplashHost(playSplash = savedInstanceState == null) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "SamilliMed")
                        }
                    }
                }
            }
        }
    }
}
