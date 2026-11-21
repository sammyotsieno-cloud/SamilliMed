package org.samillimed.app.data.finance

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.facility.FacilityScope
import org.samillimed.app.data.inventory.PaymentMethod
import java.time.LocalDate
import org.samillimed.app.data.facility.Permission
import org.samillimed.app.data.facility.Permissions

private val EXPENSES: String get() = FacilityScope.path("expenses")
private val DAILY_SUMMARIES: String get() = FacilityScope.path("dailySummaries")

private fun friendlyMessage(error: Exception, fallback: String): String =
    when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "No internet connection. Expenses update the day totals, so they need a connection. Try again when you are back online."
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Only an administrator can record expenses."
        else -> error.message ?: fallback
    }

/** Field names inside the day totals cannot contain these characters. */
private fun safeKey(text: String): String = text.trim().replace(Regex("[./*\\[\\]`~]"), " ").trim()

class ExpenseRepository {

    private val db get() = FacilityScope.firestore()

    /** The expenses from fromKey to toKey (yyyy-MM-dd), both included. Sorted by the screen. */
    fun expensesFlow(fromKey: String, toKey: String): Flow<List<Expense>> = callbackFlow {
        val registration = db.collection(EXPENSES)
            .whereGreaterThanOrEqualTo("dateKey", fromKey)
            .whereLessThanOrEqualTo("dateKey", toKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toObject(Expense::class.java) })
            }
        awaitClose { registration.remove() }
    }

    /** Saves one expense and adds it to that day's totals in one step. id stays the same for one expense. */
    fun addExpense(
        id: String,
        dateKey: String,
        amountCents: Long,
        category: String,
        note: String,
        method: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val user = FacilityScope.auth().currentUser?.email.orEmpty()
        val cleanCategory = safeKey(category)
        when {
            user.isBlank() -> return onFailure("Sign in first.")
            !Permissions.can(Permission.EXPENSES) -> return onFailure("Only an administrator can record expenses.")
            amountCents <= 0L -> return onFailure("Enter an amount above zero.")
            cleanCategory.isBlank() -> return onFailure("Choose what the money was spent on.")
            method !in PaymentMethod.all -> return onFailure("Choose how it was paid.")
            runCatching { LocalDate.parse(dateKey) }.isFailure -> return onFailure("The date is not valid.")
        }
        val now = System.currentTimeMillis()
        val expenseRef = db.collection(EXPENSES).document(id)
        val summaryRef = db.collection(DAILY_SUMMARIES).document(dateKey)
        db.runTransaction { tx ->
            if (!tx.get(expenseRef).exists()) {
                tx.set(
                    expenseRef,
                    mapOf(
                        "id" to id,
                        "dateKey" to dateKey,
                        "amountCents" to amountCents,
                        "category" to cleanCategory,
                        "note" to note.trim(),
                        "paymentMethod" to method,
                        "createdAtMillis" to now,
                        "createdBy" to user,
                        "status" to ExpenseStatus.ACTIVE,
                    ),
                )
                tx.set(
                    summaryRef,
                    mapOf(
                        "dateKey" to dateKey,
                        "expensesCents" to FieldValue.increment(amountCents),
                        "expenseCount" to FieldValue.increment(1L),
                        "expenseByCategory" to mapOf(cleanCategory to FieldValue.increment(amountCents)),
                        "expensePayments" to mapOf(method to FieldValue.increment(amountCents)),
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
            }
            true
        }.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(friendlyMessage(it, "The expense could not be saved.")) }
    }

    /** Voids an expense and takes it off its day's totals. */
    fun voidExpense(id: String, reason: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val user = FacilityScope.auth().currentUser?.email.orEmpty()
        when {
            user.isBlank() -> return onFailure("Sign in first.")
            !Permissions.can(Permission.EXPENSES) -> return onFailure("Only an administrator can void expenses.")
            reason.isBlank() -> return onFailure("Write the reason for voiding it.")
        }
        val now = System.currentTimeMillis()
        val expenseRef = db.collection(EXPENSES).document(id)
        db.runTransaction { tx ->
            val snap = tx.get(expenseRef)
            if (snap.exists() && snap.getString("status") != ExpenseStatus.VOIDED) {
                val amount = snap.getLong("amountCents") ?: 0L
                val dateKey = snap.getString("dateKey").orEmpty()
                val category = safeKey(snap.getString("category").orEmpty())
                val method = snap.getString("paymentMethod").orEmpty()
                tx.update(
                    expenseRef,
                    mapOf(
                        "status" to ExpenseStatus.VOIDED,
                        "voidedAtMillis" to now,
                        "voidedBy" to user,
                        "voidReason" to reason.trim(),
                    ),
                )
                tx.set(
                    db.collection(DAILY_SUMMARIES).document(dateKey),
                    mapOf(
                        "dateKey" to dateKey,
                        "expensesCents" to FieldValue.increment(-amount),
                        "expenseCount" to FieldValue.increment(-1L),
                        "expenseByCategory" to mapOf(category to FieldValue.increment(-amount)),
                        "expensePayments" to mapOf(method to FieldValue.increment(-amount)),
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
            }
            true
        }.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(friendlyMessage(it, "The expense could not be voided.")) }
    }
}
