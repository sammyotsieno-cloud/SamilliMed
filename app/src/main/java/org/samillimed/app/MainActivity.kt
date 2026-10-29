package org.samillimed.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.samillimed.app.ui.home.HomeScreen
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.navigation.Route
import org.samillimed.app.ui.navigation.rememberBackStack
import org.samillimed.app.ui.placeholder.ComingSoonScreen
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
            // History of opened screens, used by the back buttons.
            val nav = rememberBackStack()
            var showAppearance by rememberSaveable { mutableStateOf(false) }
            var showExit by rememberSaveable { mutableStateOf(false) }
            // The splash only plays on a fresh launch; after rotation it counts as done.
            var splashDone by rememberSaveable { mutableStateOf(savedInstanceState != null) }

            // System back button: ignore during the splash, otherwise go to the previous
            // screen, and on Home ask whether to exit.
            BackHandler {
                when {
                    !splashDone -> Unit
                    nav.back() -> Unit
                    else -> showExit = true
                }
            }

            SamilliMedTheme(mode = theme.mode) {
                SamilliSplashHost(
                    playSplash = savedInstanceState == null,
                    onSplashFinished = { splashDone = true },
                ) {
                    when (nav.current) {
                        Route.Home -> HomeScreen(
                            onMenuClick = { showAppearance = true },
                            onTabSelected = { index ->
                                when (index) {
                                    1 -> nav.navigate(Route.Dispense)
                                    2 -> nav.navigate(Route.ReceiveStock)
                                    3 -> nav.navigate(Route.Register)
                                    else -> Unit
                                }
                            },
                        )
                        Route.Dispense -> ComingSoonScreen(
                            title = Route.Dispense.title,
                            icon = SamilliIcons.Pill,
                            onBack = { nav.back() },
                        )
                        Route.ReceiveStock -> ComingSoonScreen(
                            title = Route.ReceiveStock.title,
                            icon = SamilliIcons.BoxPlus,
                            onBack = { nav.back() },
                        )
                        Route.Register -> ComingSoonScreen(
                            title = Route.Register.title,
                            icon = SamilliIcons.ClipboardPlus,
                            onBack = { nav.back() },
                        )
                    }

                    if (showAppearance) {
                        AppearanceDialog(
                            current = theme.mode,
                            onSelect = theme.setMode,
                            onDismiss = { showAppearance = false },
                        )
                    }

                    if (showExit) {
                        AlertDialog(
                            onDismissRequest = { showExit = false },
                            title = { Text("Exit SamilliMed?", fontWeight = FontWeight.Bold) },
                            text = { Text("Are you sure you want to exit the app?") },
                            confirmButton = { TextButton(onClick = { finish() }) { Text("Yes") } },
                            dismissButton = { TextButton(onClick = { showExit = false }) { Text("No") } },
                        )
                    }
                }
            }
        }
    }
}
