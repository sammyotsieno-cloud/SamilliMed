package org.samillimed.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.launch
import org.samillimed.app.ui.home.HomeScreen
import org.samillimed.app.ui.navigation.AppDrawer
import org.samillimed.app.ui.navigation.Route
import org.samillimed.app.ui.navigation.icon
import org.samillimed.app.ui.navigation.rememberBackStack
import org.samillimed.app.ui.placeholder.ComingSoonScreen
import org.samillimed.app.ui.settings.SettingsScreen
import org.samillimed.app.ui.settings.rememberAppSettings
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
            // Saved settings: theme (System default / Light / Dark), font type, font size.
            val settings = rememberAppSettings()
            // History of opened screens, used by the back buttons.
            val nav = rememberBackStack()
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            var showExit by rememberSaveable { mutableStateOf(false) }
            // The splash only plays on a fresh launch; after rotation it counts as done.
            var splashDone by rememberSaveable { mutableStateOf(savedInstanceState != null) }

            // System back: ignore during the splash, otherwise go to the previous screen,
            // and on Home ask whether to exit.
            BackHandler {
                when {
                    !splashDone -> Unit
                    nav.back() -> Unit
                    else -> showExit = true
                }
            }
            // Registered later, so it wins while the side menu is open: back closes the menu first.
            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }

            SamilliMedTheme(
                mode = settings.mode,
                font = settings.font,
                textSize = settings.textSize,
            ) {
                SamilliSplashHost(
                    playSplash = savedInstanceState == null,
                    onSplashFinished = { splashDone = true },
                ) {
                    AppDrawer(
                        drawerState = drawerState,
                        current = nav.current,
                        onNavigate = { route ->
                            scope.launch { drawerState.close() }
                            nav.navigate(route)
                        },
                    ) {
                        when (nav.current) {
                            Route.Home -> HomeScreen(
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onTabSelected = { index ->
                                    when (index) {
                                        1 -> nav.navigate(Route.Dispense)
                                        2 -> nav.navigate(Route.ReceiveStock)
                                        3 -> nav.navigate(Route.Register)
                                        else -> Unit
                                    }
                                },
                            )
                            Route.Settings -> SettingsScreen(
                                settings = settings,
                                onBack = { nav.back() },
                            )
                            else -> ComingSoonScreen(
                                title = nav.current.title,
                                icon = nav.current.icon(),
                                onBack = { nav.back() },
                            )
                        }
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
