package org.samillimed.app.ui.navigation

import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.facility.Permission
import org.samillimed.app.data.facility.Permissions

/** Whether the signed-in person may open this screen. Administrators and owners always may. */
fun Route.allowed(): Boolean = when (this) {
    Route.Register, Route.RegisteredProducts, Route.RegisteredServices -> Permissions.can(Permission.REGISTER_ITEMS)
    Route.ReceiveStock -> Permissions.can(Permission.RECEIVE_STOCK)
    Route.Expenses -> Permissions.can(Permission.EXPENSES)
    Route.Reports -> Permissions.can(Permission.VIEW_REPORTS)
    Route.Inventory, Route.Alerts -> Permissions.can(Permission.VIEW_INVENTORY)
    Route.Sales -> Permissions.can(Permission.VIEW_SALES)
    Route.Dispense -> Permissions.can(Permission.DISPENSE)
    Route.StaffAccess -> CurrentUser.isAdmin
    else -> true
}
