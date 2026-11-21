package org.samillimed.app.data.finance

import org.samillimed.app.data.inventory.PaymentMethod

object ExpenseStatus {
    const val ACTIVE = "ACTIVE"
    const val VOIDED = "VOIDED"
}

object ExpenseCategory {
    val defaults = listOf(
        "Rent", "Salaries", "Electricity and water", "Transport", "Supplies",
        "Licences and fees", "Repairs", "Airtime and internet", "Marketing", "Other",
    )
}

/** Money paid out for running the business. Never deleted: a wrong one is voided with a reason. */
data class Expense(
    val id: String = "",
    val dateKey: String = "",
    val amountCents: Long = 0,
    val category: String = "",
    val note: String = "",
    val paymentMethod: String = PaymentMethod.CASH,
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
    val status: String = ExpenseStatus.ACTIVE,
    val voidedAtMillis: Long = 0,
    val voidedBy: String = "",
    val voidReason: String = "",
)
