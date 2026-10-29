package org.samillimed.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.samillimed.app.ui.home.HomeScreen
import org.samillimed.app.ui.settings.AppearanceDialog
import org.samillimed.app.ui.settings.rememberThemeController
import org.samillimed.app.ui.splash.SamilliSplashHost
import org.samillimed.app.ui.theme.SamilliMedTheme

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
            // The saved choice: System default (follows the phone), Light or Dark.
            val theme = rememberThemeController()
            var showAppearance by rememberSaveable { mutableStateOf(false) }

            SamilliMedTheme(mode = theme.mode) {
                // Splash plays on a fresh launch only, then reveals the home screen underneath.
                SamilliSplashHost(playSplash = savedInstanceState == null) {
                    HomeScreen(onMenuClick = { showAppearance = true })
                    if (showAppearance) {
                        AppearanceDialog(
                            current = theme.mode,
                            onSelect = theme.setMode,
                            onDismiss = { showAppearance = false },
                        )
                    }
                }
            }
        }
    }
}
