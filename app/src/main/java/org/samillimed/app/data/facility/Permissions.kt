package org.samillimed.app.data.facility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Things an administrator can allow or refuse for staff. key is the name saved in the database
 * (the database rules use the same names for the ones marked serverEnforced).
 */
enum class Permission(
    val key: String,
    val title: String,
    val description: String,
    val staffDefault: Boolean,
    /** True for things staff DO (checked by the database on every write); false for things staff SEE. */
    val action: Boolean,
) {
    VIEW_SALES("viewSales", "See sales", "The list of bills, bill details and receipts", true, false),
    VIEW_INVENTORY("viewInventory", "See inventory and alerts", "Stock levels, expiry dates, the stock card and alerts", true, false),
    VIEW_COSTS("viewCosts", "See costs and profit", "Purchase costs, profit and the value of stock", false, false),
    VIEW_REPORTS("viewReports", "See reports", "Sales reports, top sellers and payment summaries", false, false),
    EXPORT_DATA("exportData", "Export sheets", "Save sales, stock and product sheets to the phone", false, false),
    DISPENSE("dispense", "Sell and dispense", "Create bills for medicines, services and lab tests", true, true),
    REGISTER_ITEMS("registerItems", "Register and edit items", "Products, services and lab tests", false, true),
    RECEIVE_STOCK("receiveStock", "Receive stock", "Supplier deliveries and opening stock", false, true),
    ADJUST_STOCK("adjustStock", "Write off or adjust stock", "Expired, damaged, lost and stock-count corrections", false, true),
    RETURNS("returns", "Record returns and voids", "Refund a bill, fully or partly", false, true),
    EXPENSES("expenses", "Record and see expenses", "Rent, salaries, transport and other costs", false, true),
    TILL_COUNT("tillCount", "Count the till", "Count the cash at the end of a shift and compare it with what the bills say", true, true),
}

/** What the signed-in person may do. Administrators and owners may do everything. */
object Permissions {
    private val staffFlags = mutableStateMapOf<String, Boolean>()

    var loaded by mutableStateOf(false)
        private set

    /** The setting for staff accounts (the administrator's choice, or the default). */
    fun staffAllowed(p: Permission): Boolean = staffFlags[p.key] ?: p.staffDefault

    fun can(p: Permission): Boolean = CurrentUser.isAdmin || staffAllowed(p)

    fun update(flags: Map<String, Boolean>) {
        staffFlags.clear()
        staffFlags.putAll(flags)
        loaded = true
    }
}

class PermissionRepository {

    private val db get() = FacilityScope.firestore()

    private fun settingsPath() = FacilityScope.path("settings")

    /** Live: a change made by the administrator reaches staff phones within seconds. One read. */
    fun flow(): Flow<Map<String, Boolean>> = callbackFlow {
        val registration = db.collection(settingsPath()).document("permissions")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val staff = (snapshot.get("staff") as? Map<*, *>)?.entries
                    ?.associate { it.key.toString() to (it.value == true) }
                    ?: emptyMap()
                trySend(staff)
            }
        awaitClose { registration.remove() }
    }

    fun save(flags: Map<String, Boolean>, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (!CurrentUser.isAdmin) return onFailure("Only an administrator can change staff access.")
        val user = FacilityScope.auth().currentUser?.email.orEmpty()
        db.collection(settingsPath()).document("permissions")
            .set(mapOf("staff" to flags, "updatedAtMillis" to System.currentTimeMillis(), "updatedBy" to user))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it.message ?: "Could not save. Check the connection and try again.") }
    }
}

/** Keeps Permissions up to date while someone is signed in. Call it once, after sign-in. */
@Composable
fun rememberPermissionsLoaded() {
    val repository = remember { PermissionRepository() }
    LaunchedEffect(FacilityScope.facilityId) {
        repository.flow().collect { Permissions.update(it) }
    }
}
