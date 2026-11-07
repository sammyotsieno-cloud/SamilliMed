package org.samillimed.app.data.inventory

import org.samillimed.app.data.facility.FacilityScope

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.samillimed.app.data.reports.ProductStat

private val STOCK_BATCHES: String get() = FacilityScope.path("stockBatches")
private val STOCK_MOVEMENTS: String get() = FacilityScope.path("stockMovements")
private val PRODUCT_MONTH_STATS: String get() = FacilityScope.path("productMonthStats")

/** The stock card of a product: its movements, or why they could not be read. */
sealed class MovementsResult {
    class Loaded(val movements: List<StockMovement>) : MovementsResult()

    /** needsIndex = the database needs the one-time index described in the app. */
    class Failed(val needsIndex: Boolean, val message: String) : MovementsResult()
}

/**
 * Live readers for the Inventory screens. Listener errors are ignored on purpose (as elsewhere in
 * the app): closing a flow with an error would crash the screen collecting it, for example when
 * someone signs out.
 */
class InventoryReader(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    /** Every batch that still holds stock. Used-up batches are left out to save reads. */
    fun activeBatchesFlow(): Flow<List<StockBatch>> = callbackFlow {
        val registration = db.collection(STOCK_BATCHES)
            .whereGreaterThan("remainingQuantity", 0)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toObject(StockBatch::class.java) })
            }
        awaitClose { registration.remove() }
    }

    /**
     * The latest movements of one product, newest first. Needs a composite index on
     * stockMovements (productId ascending, createdAtMillis descending), created once in the
     * Firebase console.
     */
    fun movementsFlow(productId: String, limit: Long = 100): Flow<MovementsResult> = callbackFlow {
        val registration = db.collection(STOCK_MOVEMENTS)
            .whereEqualTo("productId", productId)
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val missingIndex =
                        (error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    trySend(MovementsResult.Failed(missingIndex, error.message.orEmpty()))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                trySend(MovementsResult.Loaded(snapshot.documents.mapNotNull { it.toObject(StockMovement::class.java) }))
            }
        awaitClose { registration.remove() }
    }

    /** What each product sold in one month (monthKey like 2026-11). Quantities in the smallest unit. */
    fun monthStatsFlow(monthKey: String): Flow<List<ProductStat>> = callbackFlow {
        val registration = db.collection(PRODUCT_MONTH_STATS)
            .whereEqualTo("monthKey", monthKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toObject(ProductStat::class.java) })
            }
        awaitClose { registration.remove() }
    }
}
