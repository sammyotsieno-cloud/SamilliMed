
package org.samillimed.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.launch
import org.samillimed.app.data.auth.rememberAuthController
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.facility.FacilityConnection
import org.samillimed.app.data.facility.FacilityScope
import org.samillimed.app.data.facility.ProfileState
import org.samillimed.app.data.facility.rememberProfileState
import org.samillimed.app.ui.facility.AccountGate
import org.samillimed.app.ui.facility.FacilitySetupLink
import org.samillimed.app.ui.facility.rememberAccessProbe
import org.samillimed.app.data.catalog.rememberCatalog
import org.samillimed.app.data.reports.ReportsReader
import org.samillimed.app.ui.alerts.AlertsScreen
import org.samillimed.app.ui.auth.LoginScreen
import org.samillimed.app.ui.catalog.ProductFormScreen
import org.samillimed.app.ui.catalog.RegisterChooserScreen
import org.samillimed.app.ui.catalog.RegisteredProductsScreen
import org.samillimed.app.ui.catalog.RegisteredServicesScreen
import org.samillimed.app.ui.catalog.ServiceFormScreen
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.home.HomeScreen
import org.samillimed.app.ui.home.rememberHomeData
import org.samillimed.app.ui.inventory.DispenseScreen
import org.samillimed.app.ui.inventory.InventoryHost
import org.samillimed.app.ui.inventory.ReceiveStockScreen
import org.samillimed.app.ui.navigation.AppDrawer
import org.samillimed.app.ui.navigation.Route
import org.samillimed.app.ui.navigation.icon
import org.samillimed.app.ui.navigation.rememberBackStack
import org.samillimed.app.ui.placeholder.ComingSoonScreen
import org.samillimed.app.ui.reports.BillsScreen
import org.samillimed.app.ui.reports.ReportsHost
import org.samillimed.app.ui.settings.SettingsScreen
import org.samillimed.app.ui.settings.rememberAppSettings
import org.samillimed.app.ui.splash.SamilliSplashHost
import org.samillimed.app.ui.theme.SamilliMedTheme
import java.time.LocalDate
import org.samillimed.app.data.catalog.ServiceKind
import org.samillimed.app.ui.catalog.RegisterChoiceDialog
import org.samillimed.app.ui.finance.ExpensesScreen
import org.samillimed.app.data.facility.Permission
import org.samillimed.app.data.facility.Permissions
import org.samillimed.app.data.facility.rememberPermissionsLoaded
import org.samillimed.app.ui.access.StaffAccessScreen
import org.samillimed.app.ui.till.CashCountScreen
import org.samillimed.app.data.branding.Brand
import org.samillimed.app.data.branding.rememberBrandLoaded
import org.samillimed.app.ui.branding.BrandingScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        FacilityConnection.init(applicationContext)
        Brand.loadCached(applicationContext)

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

            // The signed-in account: its facility, role, and whether it is active.
            val profileState = rememberProfileState(auth.isSignedIn)
            val accessError = rememberAccessProbe(profileState)

            // History of opened screens.
            val nav = rememberBackStack()

            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            nav.onReturnToMenu = { scope.launch { drawerState.open() } }

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
                        Box(Modifier.fillMaxSize()) {
                            LoginScreen(
                                onSignIn = { email, password, onResult ->
                                    auth.signIn(email, password, onResult)
                                },
                            )
                            FacilitySetupLink(Modifier.align(Alignment.BottomCenter))
                        }
                    } else if (profileState !is ProfileState.Ready) {
                        // Signed in, but the account is loading, not linked to a facility, or disabled.
                        AccountGate(
                            state = profileState,
                            onSignOut = { auth.signOut() },
                        )
                    } else if (accessError != null) {
                        // The account is fine, but the database refused or could not answer.
                        AccountGate(
                            state = ProfileState.Failed(accessError),
                            onSignOut = { auth.signOut() },
                        )
                    } else {
                        // Every screen below reads and writes only this facility's data.
                        FacilityScope.facilityId = profileState.profile.facilityId
                        CurrentUser.update(profileState.profile)
                        rememberPermissionsLoaded()
                        rememberBrandLoaded()

                        // Catalog is available only while signed in.
                        val catalog = rememberCatalog()

                        // Live numbers for Home and Alerts. Kept for the whole signed-in
                        // session, so coming back to Home does not read everything again.
                        val homeData = rememberHomeData(catalog)

                        // Product form:
                        // null = closed
                        // "new" = registering
                        // otherwise = product ID being edited
                        var productForm by rememberSaveable {
                            mutableStateOf<String?>(null)
                        }

                        // The Register pop-up, and whether a new item is a service or a lab test.
                        var showRegister by rememberSaveable { mutableStateOf(false) }
                        var newKind by rememberSaveable { mutableStateOf(ServiceKind.SERVICE) }
                        var servicesKind by rememberSaveable { mutableStateOf("ALL") }

                        // Service form:
                        // null = closed
                        // "new" = registering
                        // otherwise = service ID being edited
                        var serviceForm by rememberSaveable {
                            mutableStateOf<String?>(null)
                        }

                        if (showRegister) {
                            RegisterChoiceDialog(
                                onProduct = {
                                    showRegister = false
                                    nav.navigate(Route.RegisteredProducts)
                                },
                                onService = {
                                    showRegister = false
                                    servicesKind = ServiceKind.SERVICE
                                    nav.navigate(Route.RegisteredServices)
                                },
                                onLabTest = {
                                    showRegister = false
                                    servicesKind = ServiceKind.LAB
                                    nav.navigate(Route.RegisteredServices)
                                },
                                onDismiss = { showRegister = false },
                            )
                        }

                        AppDrawer(
                            drawerState = drawerState,
                            current = nav.current,
                            onNavigate = { route ->
                                val viaDrawer = drawerState.isOpen
                                if (route == Route.RegisteredServices) servicesKind = "ALL"
                                scope.launch {
                                    drawerState.close()
                                }

                                productForm = null
                                serviceForm = null

                                if (route == Route.Register) {
                                    showRegister = true
                                } else {
                                    nav.navigate(route, fromMenu = viaDrawer)
                                }
                                Unit
                            },
                            userEmail = "${auth.email} (${CurrentUser.roleLabel()})",
                            isAdmin = CurrentUser.isAdmin,
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
                                                kind = newKind,
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
                                                data = homeData,
                                                onMenuClick = {
                                                    scope.launch {
                                                        drawerState.open()
                                                    }
                                                },
                                                isAdmin = Permissions.can(Permission.VIEW_COSTS),
                                                onTabSelected = { index ->
                                                    val admin = Permissions.can(Permission.RECEIVE_STOCK) && Permissions.can(Permission.REGISTER_ITEMS)
                                                    when (index) {
                                                        1 -> nav.navigate(Route.Dispense)
                                                        2 -> nav.navigate(if (admin) Route.ReceiveStock else Route.Inventory)
                                                        3 -> {
                                                            if (admin) {
                                                                showRegister = true
                                                            } else {
                                                                nav.navigate(Route.Sales)
                                                            }
                                                            Unit
                                                        }
                                                        else -> Unit
                                                    }
                                                },
                                                onOpenAlerts = {
                                                    nav.navigate(Route.Alerts)
                                                },
                                                onOpenSales = {
                                                    nav.navigate(Route.Sales)
                                                },
                                                onOpenReports = {
                                                    if (Permissions.can(Permission.VIEW_REPORTS)) nav.navigate(Route.Reports)
                                                },
                                                onOpenInventory = {
                                                    nav.navigate(Route.Inventory)
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
                                                initialKind = servicesKind,
                                                onAddKind = { kind ->
                                                    productForm = null
                                                    newKind = kind
                                                    serviceForm = "new"
                                                },
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

                                        // SALES: today's bills, with day navigation and bill details
                                        Route.Sales -> {
                                            val reportsReader = remember { ReportsReader() }
                                            BillsScreen(
                                                catalog = catalog,
                                                reader = reportsReader,
                                                initialDateKey = LocalDate.now().toString(),
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

                                        // ALERTS
                                        Route.Alerts -> {
                                            AlertsScreen(
                                                alerts = homeData.alerts,
                                                onBack = { nav.back() },
                                                onOpenInventory = {
                                                    nav.navigate(Route.Inventory)
                                                },
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

                                        // EXPENSES
                                        Route.Expenses -> {
                                            ExpensesScreen(onBack = { nav.back() })
                                        }

                                        // CASH COUNT: check the cash in the till
                                        Route.Till -> {
                                            CashCountScreen(onBack = { nav.back() })
                                        }

                                        // BRANDING: the facility's name, logo and colours
                                        Route.Branding -> {
                                            BrandingScreen(onBack = { nav.back() })
                                        }

                                        // STAFF ACCESS: the administrator decides what staff can see and do
                                        Route.StaffAccess -> {
                                            StaffAccessScreen(onBack = { nav.back() })
                                        }

                                        // NOT BUILT YET: Drug Index
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
