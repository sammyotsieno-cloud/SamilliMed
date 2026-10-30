package org.samillimed.app.data.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val PRODUCTS = "products"
private const val SERVICES = "services"
private const val LISTS = "lists"

/** Which records use each list, so a renamed value can be updated in them too. */
private val listUsage: Map<String, Pair<String, String>> = mapOf(
    ListType.PRODUCT_CATEGORIES to (PRODUCTS to "category"),
    ListType.SERVICE_CATEGORIES to (SERVICES to "category"),
    ListType.DOSAGE_FORMS to (PRODUCTS to "dosageForm"),
    ListType.DEPARTMENTS to (SERVICES to "department"),
    ListType.PRESCRIPTION_STATUS to (PRODUCTS to "prescriptionStatus"),
    ListType.STORAGE_CONDITIONS to (PRODUCTS to "storage"),
)

/** Reads and writes products, services and the user-managed lists in Firestore. */
class CatalogRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun who(): String = FirebaseAuth.getInstance().currentUser?.email.orEmpty()

    // ---- live data -------------------------------------------------------------------------

    fun productsFlow(): Flow<List<Product>> = callbackFlow {
        val reg = db.collection(PRODUCTS).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener
            val items = snap.documents
                .mapNotNull { it.toObject(Product::class.java) }
                .sortedBy { it.name.lowercase() }
            trySend(items)
        }
        awaitClose { reg.remove() }
    }

    fun servicesFlow(): Flow<List<ServiceItem>> = callbackFlow {
        val reg = db.collection(SERVICES).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener
            val items = snap.documents
                .mapNotNull { it.toObject(ServiceItem::class.java) }
                .sortedBy { it.name.lowercase() }
            trySend(items)
        }
        awaitClose { reg.remove() }
    }

    /**
     * The user-managed lists. A list that has never been saved shows the starting suggestions,
     * and is saved for real only once the server confirms it does not exist yet.
     */
    fun listsFlow(): Flow<Map<String, List<String>>> = callbackFlow {
        val reg = db.collection(LISTS).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener
            val saved: Map<String, List<String>> = snap.documents.associate { doc ->
                doc.id to ((doc.get("items") as? List<*>)?.filterIsInstance<String>() ?: emptyList())
            }
            if (!snap.metadata.isFromCache) {
                defaultLists.forEach { (type, defaults) ->
                    if (type !in saved) {
                        db.collection(LISTS).document(type).set(mapOf("items" to defaults))
                    }
                }
            }
            trySend(defaultLists.mapValues { (type, defaults) -> saved[type] ?: defaults })
        }
        awaitClose { reg.remove() }
    }

    // ---- products and services ---------------------------------------------------------------

    /**
     * Saves a new product (id blank) or changes an existing one. Returns the product id.
     * Works offline: the change shows at once and reaches the server when back online.
     */
    fun saveProduct(product: Product, onFailed: (String) -> Unit = {}): String {
        val now = System.currentTimeMillis()
        val isNew = product.id.isBlank()
        val ref = if (isNew) db.collection(PRODUCTS).document() else db.collection(PRODUCTS).document(product.id)
        val data = product.toMap().toMutableMap()
        data["updatedAtMillis"] = now
        if (isNew) {
            data["createdAtMillis"] = now
            data["createdBy"] = who()
        }
        ref.set(data, SetOptions.merge()).addOnFailureListener { onFailed("Could not save the product.") }
        return ref.id
    }

    fun saveService(service: ServiceItem, onFailed: (String) -> Unit = {}): String {
        val now = System.currentTimeMillis()
        val isNew = service.id.isBlank()
        val ref = if (isNew) db.collection(SERVICES).document() else db.collection(SERVICES).document(service.id)
        val data = service.toMap().toMutableMap()
        data["updatedAtMillis"] = now
        if (isNew) {
            data["createdAtMillis"] = now
            data["createdBy"] = who()
        }
        ref.set(data, SetOptions.merge()).addOnFailureListener { onFailed("Could not save the service.") }
        return ref.id
    }

    fun setProductActive(id: String, active: Boolean) {
        db.collection(PRODUCTS).document(id)
            .update(mapOf("active" to active, "updatedAtMillis" to System.currentTimeMillis()))
    }

    fun setServiceActive(id: String, active: Boolean) {
        db.collection(SERVICES).document(id)
            .update(mapOf("active" to active, "updatedAtMillis" to System.currentTimeMillis()))
    }

    // ---- user-managed lists ------------------------------------------------------------------

    /** Adds a value to a list (does nothing if it is already there, ignoring capital letters). */
    fun addToList(type: String, value: String, current: List<String>) {
        val clean = value.trim()
        if (clean.isEmpty() || current.any { it.equals(clean, ignoreCase = true) }) return
        db.collection(LISTS).document(type)
            .set(mapOf("items" to FieldValue.arrayUnion(clean)), SetOptions.merge())
    }

    fun removeFromList(type: String, value: String) {
        db.collection(LISTS).document(type)
            .set(mapOf("items" to FieldValue.arrayRemove(value)), SetOptions.merge())
    }

    /**
     * Renames a value in a list and in every product or service that uses it.
     * (Units are not changed inside products: each product keeps its own unit names.)
     */
    fun renameInList(type: String, old: String, new: String) {
        val clean = new.trim()
        if (clean.isEmpty() || clean == old) return
        val listRef = db.collection(LISTS).document(type)
        db.runTransaction { tx ->
            val items = (tx.get(listRef).get("items") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            tx.set(listRef, mapOf("items" to items.map { if (it == old) clean else it }))
            null
        }
        val usage = listUsage[type] ?: return
        db.collection(usage.first).whereEqualTo(usage.second, old).get().addOnSuccessListener { snap ->
            snap.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.update(it.reference, usage.second, clean) }
                batch.commit()
            }
        }
    }
}

// ---- what is written to Firestore ----------------------------------------------------------

private fun Product.toMap(): Map<String, Any> = mapOf(
    "name" to name,
    "genericName" to genericName,
    "brandName" to brandName,
    "strength" to strength,
    "dosageForm" to dosageForm,
    "manufacturer" to manufacturer,
    "category" to category,
    "units" to units.map { mapOf("name" to it.name, "baseQty" to it.baseQty) },
    "code" to code,
    "barcode" to barcode,
    "prescriptionStatus" to prescriptionStatus,
    "controlled" to controlled,
    "trackExpiry" to trackExpiry,
    "expiryAlertDays" to expiryAlertDays,
    "reorderLevel" to reorderLevel,
    "storage" to storage,
    "location" to location,
    "notes" to notes,
    "active" to active,
)

private fun ServiceItem.toMap(): Map<String, Any> = mapOf(
    "name" to name,
    "code" to code,
    "category" to category,
    "department" to department,
    "priceCents" to priceCents,
    "notes" to notes,
    "active" to active,
)

// ---- for screens --------------------------------------------------------------------------

/** Everything a screen needs: the live lists, plus repo for saving. loaded = first data has arrived. */
class Catalog(
    val repo: CatalogRepository,
    val products: List<Product>,
    val services: List<ServiceItem>,
    val lists: Map<String, List<String>>,
    val loaded: Boolean,
) {
    fun list(type: String): List<String> = lists[type] ?: defaultLists[type].orEmpty()
}

/** Call this once, only while someone is signed in, and pass the result to the screens. */
@Composable
fun rememberCatalog(): Catalog {
    val repo = remember { CatalogRepository() }
    val products by remember(repo) { repo.productsFlow() }.collectAsState(initial = null)
    val services by remember(repo) { repo.servicesFlow() }.collectAsState(initial = null)
    val lists by remember(repo) { repo.listsFlow() }.collectAsState(initial = null)
    return Catalog(
        repo = repo,
        products = products ?: emptyList(),
        services = services ?: emptyList(),
        lists = lists ?: defaultLists,
        loaded = products != null && services != null && lists != null,
    )
}
