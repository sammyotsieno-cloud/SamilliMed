package org.samillimed.app.data.till

/** One cash count: what the bills say should be in the till, what was really counted, and the difference. */
data class TillCount(
    val id: String = "",
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
    val dateKey: String = "",
    /** The cash movements in this count run from sinceMillis to windowEndMillis. */
    val sinceMillis: Long = 0,
    val windowEndMillis: Long = 0,
    val openingCents: Long = 0,
    val cashSalesCents: Long = 0,
    val cashBills: Int = 0,
    val cashRefundsCents: Long = 0,
    val cashExpensesCents: Long = 0,
    val expectedCents: Long = 0,
    val countedCents: Long = 0,
    /** Counted minus expected: below zero means short, above zero means over. */
    val differenceCents: Long = 0,
    val withdrawnCents: Long = 0,
    val leftInTillCents: Long = 0,
    val note: String = "",
)
