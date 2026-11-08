package org.samillimed.app.data.reports

import org.samillimed.app.data.facility.FacilityScope

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.samillimed.app.data.inventory.DispensingLine
import org.samillimed.app.data.inventory.DispensingRecord
import org.samillimed.app.data.inventory.DispensingServiceLine

private val DAILY_SUMMARIES: String get() = FacilityScope.path("dailySummaries")
private val PRODUCT_DAY_STATS: String get() = FacilityScope.path("productDayStats")
private val PRODUCT_MONTH_STATS: String get() = FacilityScope.path("productMonthStats")
private val DISPENSING_RECORDS: String get() = FacilityScope.path("dispensingRecords")
private val DISPENSING_LINES: String get() = FacilityScope.path("dispensingLines")
private val DISPENSING_SERVICE_LINES: String get() = FacilityScope.path("dispensingServiceLines")

/**
 * Live readers for Reports. Each one reads as little as possible: day totals, product totals, or
 * the bills of a single day. Listener errors are ignored on purpose (as elsewhere in the app).
 */
class ReportsReader(
    private val db: FirebaseFirestore = FacilityScope.firestore(),
) {

    /** The day totals from fromKey to toKey (yyyy-MM-dd), both included. One read per day. */
    fun summariesFlow(fromKey: String, toKey: String): Flow<List<DailySummary>> = callbackFlow {
        val registration = db.collection(DAILY_SUMMARIES)
            .whereGreaterThanOrEqualTo("dateKey", fromKey)
            .whereLessThanOrEqualTo("dateKey", toKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.map { it.toDailySummary() })
            }
        awaitClose { registration.remove() }
    }

    /** What each product sold on each day of the range (one document per product per day). */
    fun dayStatsFlow(fromKey: String, toKey: String): Flow<List<ProductStat>> = callbackFlow {
        val registration = db.collection(PRODUCT_DAY_STATS)
            .whereGreaterThanOrEqualTo("dateKey", fromKey)
            .whereLessThanOrEqualTo("dateKey", toKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toObject(ProductStat::class.java) })
            }
        awaitClose { registration.remove() }
    }

    /** What each product sold in one month (monthKey like 2026-11). One document per product. */
    fun monthStatsFlow(monthKey: String): Flow<List<ProductStat>> = callbackFlow {
        val registration = db.collection(PRODUCT_MONTH_STATS)
            .whereEqualTo("monthKey", monthKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toObject(ProductStat::class.java) })
            }
        awaitClose { registration.remove() }
    }

    /** The bills of one day, newest first. Sorted here so no database index is needed. */
    fun billsFlow(dateKey: String): Flow<List<DispensingRecord>> = callbackFlow {
        val registration = db.collection(DISPENSING_RECORDS)
            .whereEqualTo("dateKey", dateKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents
                        .mapNotNull { it.toObject(DispensingRecord::class.java) }
                        .sortedByDescending { it.dispensedAtMillis },
                )
            }
        awaitClose { registration.remove() }
    }

    /** The medicine lines of one bill. Read only when the bill is opened. */
    fun billLinesFlow(dispensingId: String): Flow<List<DispensingLine>> = callbackFlow {
        val registration = db.collection(DISPENSING_LINES)
            .whereEqualTo("dispensingId", dispensingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toObject(DispensingLine::class.java) })
            }
        awaitClose { registration.remove() }
    }

    /** The service lines of one bill. Read only when the bill is opened. */
    fun billServiceLinesFlow(dispensingId: String): Flow<List<DispensingServiceLine>> = callbackFlow {
        val registration = db.collection(DISPENSING_SERVICE_LINES)
            .whereEqualTo("dispensingId", dispensingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toObject(DispensingServiceLine::class.java) })
            }
        awaitClose { registration.remove() }
    }
}
