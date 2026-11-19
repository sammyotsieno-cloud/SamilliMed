package org.samillimed.app.data.inventory

/** Where received stock came from. */
object ReceiptType {
    /** A delivery from a supplier. */
    const val SUPPLIER = "SUPPLIER"

    /** Stock already on the shelves when the facility started using the app. Not a purchase. */
    const val OPENING = "OPENING"
}
