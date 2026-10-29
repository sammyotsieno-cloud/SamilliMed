package org.samillimed.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.samillimed.app.ui.home.HomeScreen
import org.samillimed.app.ui.splash.SamilliSplashHost
import org.samillimed.app.ui.theme.SamilliMedTheme
import org.samillimed.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()   // system splash (plain green) hands over to the animation
        super.onCreate(savedInstanceState)
        // The header and bottom bar are dark green in both themes, so system icons stay light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            // ThemeMode.System follows the phone's light/dark setting.
            SamilliMedTheme(mode = ThemeMode.System) {
                // Splash plays on a fresh launch only, then reveals the home screen underneath.
                SamilliSplashHost(playSplash = savedInstanceState == null) {
                    HomeScreen()
                }
            }
        }
    }
}
