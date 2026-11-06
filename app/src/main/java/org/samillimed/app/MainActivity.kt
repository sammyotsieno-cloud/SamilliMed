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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.launch
import org.samillimed.app.data.auth.rememberAuthController
import org.samillimed.app.data.catalog.rememberCatalog
import org.samillimed.app.ui.auth.LoginScreen
import org.samillimed.app.ui.catalog.ProductFormScreen
import org.samillimed.app.ui.catalog.RegisterChooserScreen
import org.samillimed.app.ui.catalog.RegisteredProductsScreen
import org.samillimed.app.ui.catalog.RegisteredServicesScreen
import org.samillimed.app.ui.catalog.ServiceFormScreen
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.home.HomeScreen
import org.samillimed.app.ui.inventory.DispenseScreen
import org.samillimed.app.ui.inventory.InventoryHost
import org.samillimed.app.ui.inventory.ReceiveStockScreen
import org.samillimed.app.ui.navigation.AppDrawer
import org.samillimed.app.ui.navigation.Route
import org.samillimed.app.ui.navigation.icon
import org.samillimed.app.ui.navigation.rememberBackStack
import org.samillimed.app.ui.placeholder.ComingSoonScreen
import org.samillimed.app.ui.reports.ReportsHost
import org.samillimed.app.ui.settings.SettingsScreen
import org.samillimed.app.ui.settings.rememberAppSettings
import org.samillimed.app.ui.splash.SamilliSplashHost
import org.samillimed.app.ui.theme.SamilliMedTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // The header and bottom bar are dark green in both themes,
        // so system icons stay light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            // Saved settings: theme, font type and font size.
            val settings = rememberAppSettings()

            // Who is signed in.
            val auth = rememberAuthController()

            // History of opened screens.
            val nav = rememberBackStack()

            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            var showExit by rememberSaveable {
                mutableStateOf(false)
            }

            var showSignOut by rememberSaveable {
                mutableStateOf(false)
            }

            // The splash only plays on a fresh launch.
            var splashDone by rememberSaveable {
                mutableStateOf(savedInstanceState != null)
            }

            // System back: ignore during splash, otherwise navigate back
            // or ask whether to exit.
            BackHandler {
                when {
                    !splashDone -> Unit
                    auth.isSignedIn && nav.back() -> Unit
                    else -> showExit = true
                }
            }

            // Back closes the drawer before navigating away.
            BackHandler(enabled = drawerState.isOpen) {
                scope.launch {
                    drawerState.close()
                }
            }

            SamilliMedTheme(
                mode = settings.mode,
                font = settings.font,
                textSize = settings.textSize,
            ) {
                SamilliSplashHost(
                    playSplash = savedInstanceState == null,
                    onSplashFinished = {
                        splashDone = true
                    },
                ) {
                    if (!auth.isSignedIn) {
                        LoginScreen(
                            onSignIn = { email, password, onResult ->
                                auth.signIn(email, password, onResult)
                            },
                        )
                    } else {
                        // Catalog is available only while signed in.
                        val catalog = rememberCatalog()

                        // Product form:
                        // null = closed
                        // "new" = registering
                        // otherwise = product ID being edited
                        var productForm by rememberSaveable {
                            mutableStateOf<String?>(null)
                        }

                        // Service form:
                        // null = closed
                        // "new" = registering
                        // otherwise = service ID being edited
                        var serviceForm by rememberSaveable {
                            mutableStateOf<String?>(null)
                        }

                        AppDrawer(
                            drawerState = drawerState,
                            current = nav.current,
                            onNavigate = { route ->
                                scope.launch {
                                    drawerState.close()
                                }

                                productForm = null
                                serviceForm = null

                                nav.navigate(route)
                            },
                            userEmail = auth.email,
                            onSignOut = {
                                scope.launch {
                                    drawerState.close()
                                }

                                showSignOut = true
                            },
                        ) {
                            val productTarget = productForm
                            val serviceTarget = serviceForm

                            when {

                                // -------------------------------------------------
                                // PRODUCT REGISTRATION / EDITING
                                // -------------------------------------------------

                                productTarget != null -> {
                                    val editing =
                                        if (productTarget == "new") {
                                            null
                                        } else {
                                            catalog.products.firstOrNull {
                                                it.id == productTarget
                                            }
                                        }

                                    when {
                                        productTarget != "new" &&
                                                !catalog.loaded -> {
                                            LoadingState()
                                        }

                                        productTarget != "new" &&
                                                editing == null -> {
                                            LaunchedEffect(productTarget) {
                                                productForm = null
                                            }
                                        }

                                        else -> {
                                            ProductFormScreen(
                                                catalog = catalog,
                                                product = editing,
                                                onClose = {
                                                    productForm = null
                                                },
                                            )
                                        }
                                    }
                                }

                                // -------------------------------------------------
                                // SERVICE REGISTRATION / EDITING
                                // -------------------------------------------------

                                serviceTarget != null -> {
                                    val editing =
                                        if (serviceTarget == "new") {
                                            null
                                        } else {
                                            catalog.services.firstOrNull {
                                                it.id == serviceTarget
                                            }
                                        }

                                    when {
                                        serviceTarget != "new" &&
                                                !catalog.loaded -> {
                                            LoadingState()
                                        }

                                        serviceTarget != "new" &&
                                                editing == null -> {
                                            LaunchedEffect(serviceTarget) {
                                                serviceForm = null
                                            }
                                        }

                                        else -> {
                                            ServiceFormScreen(
                                                catalog = catalog,
                                                service = editing,
                                                onClose = {
                                                    serviceForm = null
                                                },
                                            )
                                        }
                                    }
                                }

                                // -------------------------------------------------
                                // NORMAL NAVIGATION
                                // -------------------------------------------------

                                else -> {
                                    when (nav.current) {

                                        // HOME
                                        Route.Home -> {
                                            HomeScreen(
                                                onMenuClick = {
                                                    scope.launch {
                                                        drawerState.open()
                                                    }
                                                },
                                                onTabSelected = { index ->
                                                    when (index) {
                                                        1 -> nav.navigate(
                                                            Route.Dispense
                                                        )

                                                        2 -> nav.navigate(
                                                            Route.ReceiveStock
                                                        )

                                                        3 -> nav.navigate(
                                                            Route.Register
                                                        )

                                                        else -> Unit
                                                    }
                                                },
                                            )
                                        }

                                        // REGISTRATION CHOOSER
                                        Route.Register -> {
                                            RegisterChooserScreen(
                                                onBack = {
                                                    nav.back()
                                                },
                                                onProduct = {
                                                    serviceForm = null
                                                    productForm = "new"
                                                },
                                                onService = {
                                                    productForm = null
                                                    serviceForm = "new"
                                                },
                                            )
                                        }

                                        // REGISTERED PRODUCTS
                                        Route.RegisteredProducts -> {
                                            RegisteredProductsScreen(
                                                catalog = catalog,
                                                onBack = {
                                                    nav.back()
                                                },
                                                onAdd = {
                                                    serviceForm = null
                                                    productForm = "new"
                                                },
                                                onEdit = {
                                                    serviceForm = null
                                                    productForm = it.id
                                                },
                                            )
                                        }

                                        // REGISTERED SERVICES
                                        Route.RegisteredServices -> {
                                            RegisteredServicesScreen(
                                                catalog = catalog,
                                                onBack = {
                                                    nav.back()
                                                },
                                                onAdd = {
                                                    productForm = null
                                                    serviceForm = "new"
                                                },
                                                onEdit = {
                                                    productForm = null
                                                    serviceForm = it.id
                                                },
                                            )
                                        }

                                        // DISPENSE
                                        Route.Dispense -> {
                                            DispenseScreen(
                                                catalog = catalog,
                                                onBack = {
                                                    nav.back()
                                                },
                                            )
                                        }

                                        // INVENTORY
                                        Route.Inventory -> {
                                            InventoryHost(
                                                catalog = catalog,
                                                onBack = { nav.back() },
                                            )
                                        }

                                        // REPORTS
                                        Route.Reports -> {
                                            ReportsHost(
                                                catalog = catalog,
                                                onBack = { nav.back() },
                                            )
                                        }

                                        // RECEIVE STOCK
                                        Route.ReceiveStock -> {
                                            if (!catalog.loaded) {
                                                LoadingState()
                                            } else {
                                                ReceiveStockScreen(
                                                    catalog = catalog,
                                                    onBack = {
                                                        nav.back()
                                                    },
                                                )
                                            }
                                        }

                                        // SETTINGS
                                        Route.Settings -> {
                                            SettingsScreen(
                                                settings = settings,
                                                onBack = {
                                                    nav.back()
                                                },
                                            )
                                        }

                                        // OTHER SCREENS
                                        else -> {
                                            ComingSoonScreen(
                                                title = nav.current.title,
                                                icon = nav.current.icon(),
                                                onBack = {
                                                    nav.back()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // SIGN-OUT CONFIRMATION
                    // ---------------------------------------------------------

                    if (showSignOut) {
                        AlertDialog(
                            onDismissRequest = {
                                showSignOut = false
                            },
                            title = {
                                Text(
                                    "Sign out?",
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            text = {
                                Text(
                                    "You will need to sign in again to use SamilliMed."
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showSignOut = false
                                        auth.signOut()
                                        nav.navigate(Route.Home)
                                    },
                                ) {
                                    Text("Yes")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showSignOut = false
                                    },
                                ) {
                                    Text("No")
                                }
                            },
                        )
                    }

                    // ---------------------------------------------------------
                    // EXIT CONFIRMATION
                    // ---------------------------------------------------------

                    if (showExit) {
                        AlertDialog(
                            onDismissRequest = {
                                showExit = false
                            },
                            title = {
                                Text(
                                    "Exit SamilliMed?",
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            text = {
                                Text(
                                    "Are you sure you want to exit the app?"
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        finish()
                                    },
                                ) {
                                    Text("Yes")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showExit = false
                                    },
                                ) {
                                    Text("No")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
