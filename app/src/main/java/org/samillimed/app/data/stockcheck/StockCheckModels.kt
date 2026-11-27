package org.samillimed.app.data.stockcheck

object StockCheckType {
    const val DAILY = "DAILY"
    const val FULL = "FULL"
    const val SPOT = "SPOT"

    fun label(code: String): String = when (code) {
        DAILY -> "Daily check"
        FULL -> "Full check"
        else -> "Spot check"
    }
}

object StockCheckStatus {
    const val SUBMITTED = "SUBMITTED"
    const val REVIEWED = "REVIEWED"
}

object CheckLineStatus {
    const val PENDING = "PENDING"
    const val APPLIED = "APPLIED"
    const val SKIPPED = "SKIPPED"
    const val MATCHED = "MATCHED"
}

/** One stock check: who counted, when, and how it was reviewed. */
data class StockCheck(
    val id: String = "",
    val type: String = StockCheckType.SPOT,
    val status: String = StockCheckStatus.SUBMITTED,
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
    val lineCount: Int = 0,
    val note: String = "",
    val reviewedAtMillis: Long = 0,
    val reviewedBy: String = "",
    val appliedLines: Int = 0,
    val skippedLines: Int = 0,
)

/** What was counted for one product. Quantities are in the product's smallest unit. */
data class StockCheckLine(
    val id: String = "",
    val checkId: String = "",
    val productId: String = "",
    val productName: String = "",
    val countedBase: Long = 0,
    val countedAtMillis: Long = 0,
    val status: String = CheckLineStatus.PENDING,
    val expectedBase: Long = 0,
    val differenceBase: Long = 0,
    val valueCents: Long = 0,
    val appliedAtMillis: Long = 0,
)
