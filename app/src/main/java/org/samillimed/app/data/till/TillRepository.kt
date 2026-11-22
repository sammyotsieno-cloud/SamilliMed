package org.samillimed.app.data.till

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.samillimed.app.data.facility.FacilityScope
import org.samillimed.app.data.facility.Permission
import org.samillimed.app.data.facility.Permissions
import org.samillimed.app.data.finance.ExpenseStatus
import org.samillimed.app.data.inventory.PaymentMethod
import java.time.LocalDate
import java.time.ZoneId

private val TILL_COUNTS: String get() = FacilityScope.path("tillCounts")
private val BILLS: String get() = FacilityScope.path("dispensingRecords")
private val RETURNS: String get() = FacilityScope.path("salesReturns")
private val EXPENSES: String get() = FacilityScope.path("expenses")

/** What happened to the cash since the last count, worked out from the bills, refunds and expenses. */
class TillSummary(
    val last: TillCount?,
    val sinceMillis: Long,
    val windowEndMillis: Long,
    val cashBills: Int,
    val cashSalesCents: Long,
    val cashRefundsCents: Long,
    val cashExpensesCents: Long,
)

private fun friendlyMessage(error: Exception, fallback: String): String =
    when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "There is no internet connection. The cash count reads today's bills, so it needs a connection. Try again when you are back online."
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You are not allowed to do this. Ask an administrator to switch on \"Count the till\" in Staff Access."
        else -> error.message ?: fallback
    }

private fun startOfTodayMillis(): Long =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

class TillRepository {

    private val db get() = FacilityScope.firestore()

    /** Works out the cash that should be in the till right now. Reads the bills made since the last count. */
    fun loadSummary(onResult: (TillSummary) -> Unit, onFailure: (String) -> Unit) {
        val database = db
        val windowEnd = System.currentTimeMillis()
        database.collection(TILL_COUNTS)
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { lastSnapshot ->
                val last = lastSnapshot.documents.firstOrNull()?.toObject(TillCount::class.java)
                val since = last?.windowEndMillis?.takeIf { it > 0L } ?: startOfTodayMillis()
                Tasks.whenAllSuccess<QuerySnapshot>(
                    listOf(
                        database.collection(BILLS).whereGreaterThan("dispensedAtMillis", since).get(),
                        database.collection(RETURNS).whereGreaterThan("createdAtMillis", since).get(),
                        database.collection(EXPENSES).whereGreaterThan("createdAtMillis", since).get(),
                    ),
                ).addOnSuccessListener { results ->
                    val bills = results[0].documents.filter {
                        (it.getLong("dispensedAtMillis") ?: 0L) <= windowEnd &&
                            it.getString("paymentMethod") == PaymentMethod.CASH
                    }
                    val refunds = results[1].documents.filter {
                        (it.getLong("createdAtMillis") ?: 0L) <= windowEnd &&
                            it.getString("refundMethod") == PaymentMethod.CASH
                    }
                    val expenses = results[2].documents.filter {
                        (it.getLong("createdAtMillis") ?: 0L) <= windowEnd &&
                            it.getString("paymentMethod") == PaymentMethod.CASH &&
                            it.getString("status") != ExpenseStatus.VOIDED
                    }
                    onResult(
                        TillSummary(
                            last = last,
                            sinceMillis = since,
                            windowEndMillis = windowEnd,
                            cashBills = bills.size,
                            cashSalesCents = bills.sumOf { it.getLong("totalAmountCents") ?: 0L },
                            cashRefundsCents = refunds.sumOf { it.getLong("refundCents") ?: 0L },
                            cashExpensesCents = expenses.sumOf { it.getLong("amountCents") ?: 0L },
                        ),
                    )
                }.addOnFailureListener { onFailure(friendlyMessage(it, "Today's cash could not be worked out.")) }
            }
            .addOnFailureListener { onFailure(friendlyMessage(it, "The last count could not be read.")) }
    }

    /** Saves a count. It can never be changed afterwards. id stays the same for one count, so saving twice is safe. */
    fun saveCount(
        id: String,
        summary: TillSummary,
        openingCents: Long,
        countedCents: Long,
        withdrawnCents: Long,
        note: String,
        onSuccess: (TillCount) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val user = FacilityScope.auth().currentUser?.email.orEmpty()
        when {
            user.isBlank() -> return onFailure("Sign in first.")
            !Permissions.can(Permission.TILL_COUNT) -> return onFailure("You are not allowed to count the till.")
            openingCents < 0L -> return onFailure("The cash at the start cannot be below zero.")
            countedCents < 0L -> return onFailure("The cash you counted cannot be below zero.")
            withdrawnCents < 0L || withdrawnCents > countedCents ->
                return onFailure("The cash you take out cannot be more than the cash you counted.")
        }
        val expected = openingCents + summary.cashSalesCents - summary.cashRefundsCents - summary.cashExpensesCents
        val difference = countedCents - expected
        if (difference != 0L && note.isBlank()) return onFailure("Write a short reason for the difference.")

        val count = TillCount(
            id = id,
            createdAtMillis = System.currentTimeMillis(),
            createdBy = user,
            dateKey = LocalDate.now().toString(),
            sinceMillis = summary.sinceMillis,
            windowEndMillis = summary.windowEndMillis,
            openingCents = openingCents,
            cashSalesCents = summary.cashSalesCents,
            cashBills = summary.cashBills,
            cashRefundsCents = summary.cashRefundsCents,
            cashExpensesCents = summary.cashExpensesCents,
            expectedCents = expected,
            countedCents = countedCents,
            differenceCents = difference,
            withdrawnCents = withdrawnCents,
            leftInTillCents = countedCents - withdrawnCents,
            note = note.trim(),
        )
        db.collection(TILL_COUNTS).document(id).set(count)
            .addOnSuccessListener { onSuccess(count) }
            .addOnFailureListener { onFailure(friendlyMessage(it, "The count could not be saved.")) }
    }

    /** The latest counts, newest first. */
    fun historyFlow(): Flow<List<TillCount>> = callbackFlow {
        val registration = db.collection(TILL_COUNTS)
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(60)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toObject(TillCount::class.java) })
            }
        awaitClose { registration.remove() }
    }
}
