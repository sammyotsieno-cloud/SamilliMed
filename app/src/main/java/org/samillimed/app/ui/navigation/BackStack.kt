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
    Register("Register"),
    DrugIndex("Drug Index"),
    RegisteredProducts("Registered Products"),
    RegisteredServices("Registered Services"),
    Inventory("Inventory"),
    Sales("Sales"),
    Expenses("Expenses"),
    Reports("Reports"),
    Alerts("Alerts"),
    Settings("Settings"),
}

/**
 * The history of screens the user has opened, newest last.
 * back() returns false when there is nothing to go back to (the user is on Home).
 */
class BackStack(initial: List<Route> = listOf(Route.Home)) {
    private val stack = mutableStateListOf<Route>().apply {
        addAll(initial.ifEmpty { listOf(Route.Home) })
    }

    val current: Route get() = stack.last()

    /** Opens a screen. If it is already in the history, returns to it instead of adding a duplicate. */
    fun navigate(route: Route) {
        if (route == current) return
        val existing = stack.lastIndexOf(route)
        if (existing >= 0) {
            while (stack.lastIndex > existing) stack.removeAt(stack.lastIndex)
        } else {
            stack.add(route)
        }
    }

    /** Goes to the previous screen. Returns false if already on the first screen. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
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
