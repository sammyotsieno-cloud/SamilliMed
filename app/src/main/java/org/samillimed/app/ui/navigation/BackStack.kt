package org.samillimed.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** Every screen in the app. Add new screens here. */
enum class Route(val title: String) {
    Home("Home"),
    Dispense("Dispense"),
    ReceiveStock("Receive Stock"),
    Register("Registers"),
    DrugIndex("Drug Index"),
    RegisteredProducts("Registered Products"),
    RegisteredServices("Services & Lab Tests"),
    Inventory("Inventory"),
    Sales("Sales"),
    Expenses("Expenses"),
    Reports("Reports"),
    Alerts("Alerts"),
    Till("Cash Count"),
    StockCheck("Stock Check"),
    StaffAccess("Staff Access"),
    Branding("Facility Branding"),
    Settings("Settings"),
}

/**
 * The history of screens the user has opened, newest last.
 * back() returns false when there is nothing to go back to (the user is on Home).
 * A screen opened from the side menu remembers that, so going back reopens the menu first.
 */
class BackStack(initial: List<Route> = listOf(Route.Home)) {
    private val stack = mutableStateListOf<Route>().apply {
        addAll(initial.ifEmpty { listOf(Route.Home) })
    }

    /** For each screen in the history: was it opened from the side menu? */
    private val menuFlags = mutableStateListOf<Boolean>().apply {
        repeat(stack.size) { add(false) }
    }

    /** Set by the main screen: reopens the side menu. */
    var onReturnToMenu: (() -> Unit)? = null

    val current: Route get() = stack.last()

    /** Opens a screen. If it is already in the history, returns to it instead of adding a duplicate. */
    fun navigate(route: Route, fromMenu: Boolean = false) {
        if (route == current) return
        val existing = stack.lastIndexOf(route)
        if (existing >= 0) {
            while (stack.lastIndex > existing) {
                stack.removeAt(stack.lastIndex)
                menuFlags.removeAt(menuFlags.lastIndex)
            }
        } else {
            stack.add(route)
            menuFlags.add(fromMenu)
        }
    }

    /** Goes to the previous screen. Returns false if already on the first screen. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        val wasFromMenu = menuFlags.removeAt(menuFlags.lastIndex)
        if (wasFromMenu) onReturnToMenu?.invoke()
        return true
    }

    companion object {
        /** Lets the history survive screen rotation and the app being recreated. */
        val Saver: Saver<BackStack, Any> = listSaver(
            save = { backStack -> backStack.stack.map { it.name } },
            restore = { names -> BackStack(names.map { Route.valueOf(it) }) },
        )
    }
}

@Composable
fun rememberBackStack(): BackStack = rememberSaveable(saver = BackStack.Saver) { BackStack() }
