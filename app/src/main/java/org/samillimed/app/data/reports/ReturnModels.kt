package org.samillimed.app.data.reports

/** One line of a return, as saved. */
data class ReturnLineRecord(
    val kind: String = "",
    val lineId: String = "",
    val name: String = "",
    val quantity: Long = 0,
    val unitName: String = "",
    val refundCents: Long = 0,
    val restockedBase: Long = 0,
    val restockedCostCents: Long = 0,
)

/** A return or void recorded against a bill. Never edited afterwards. */
data class SalesReturn(
    val id: String = "",
    val billId: String = "",
    val billReference: String = "",
    val dateKey: String = "",
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
    val reason: String = "",
    val refundMethod: String = "",
    val refundCents: Long = 0,
    val restocked: Boolean = false,
    val restockedCostCents: Long = 0,
    val fullVoid: Boolean = false,
    val lines: List<ReturnLineRecord> = emptyList(),
)
