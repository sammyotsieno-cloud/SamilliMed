package org.samillimed.app.data.inventory

/**
 * Central calculations for receiving, dispensing and stock accounting.
 *
 * All stock quantities are converted into the product's smallest
 * registered unit before being used in stock calculations.
 *
 * This object does not perform database operations.
 */
object InventoryCalculations {

    /**
     * Converts a transaction quantity into the smallest registered unit.
     *
     * Example:
     * 5 boxes × 100 tablets = 500 tablets.
     *
     * Returns null if the supplied values are invalid or overflow Long.
     */
    fun toBaseQuantity(
        quantity: Long,
        baseUnitsPerUnit: Long
    ): Long? {
        if (quantity <= 0L || baseUnitsPerUnit <= 0L) {
            return null
        }

        return try {
            Math.multiplyExact(quantity, baseUnitsPerUnit)
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Converts a base-unit quantity into a selected transaction unit.
     *
     * Only exact conversions are accepted.
     *
     * Example:
     * 500 tablets ÷ 100 tablets per box = 5 boxes.
     *
     * Returns null if the conversion would leave a remainder.
     */
    fun fromBaseQuantity(
        baseQuantity: Long,
        baseUnitsPerUnit: Long
    ): Long? {
        if (baseQuantity < 0L || baseUnitsPerUnit <= 0L) {
            return null
        }

        if (baseQuantity % baseUnitsPerUnit != 0L) {
            return null
        }

        return baseQuantity / baseUnitsPerUnit
    }

    /**
     * Calculates the total purchase cost of a receipt line.
     *
     * The purchase cost is expressed in cents per receiving unit.
     */
    fun calculateLineCostCents(
        quantity: Long,
        costPerUnitCents: Long
    ): Long? {
        if (quantity <= 0L || costPerUnitCents < 0L) {
            return null
        }

        return try {
            Math.multiplyExact(quantity, costPerUnitCents)
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Adds a receipt line's base quantity to an existing stock balance.
     */
    fun addReceivedStock(
        currentBalance: Long,
        receivedBaseQuantity: Long
    ): Long? {
        if (currentBalance < 0L || receivedBaseQuantity <= 0L) {
            return null
        }

        return try {
            Math.addExact(currentBalance, receivedBaseQuantity)
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Calculates the remaining stock after dispensing.
     *
     * Prevents negative stock.
     */
    fun deductDispensedStock(
        currentBalance: Long,
        dispensedBaseQuantity: Long
    ): Long? {
        if (currentBalance < 0L || dispensedBaseQuantity <= 0L) {
            return null
        }

        if (dispensedBaseQuantity > currentBalance) {
            return null
        }

        return currentBalance - dispensedBaseQuantity
    }

    /**
     * Calculates the remaining quantity in an individual batch.
     */
    fun deductFromBatch(
        batch: StockBatch,
        dispensedBaseQuantity: Long
    ): StockBatch? {
        if (batch.remainingQuantity < 0L ||
            batch.receivedQuantity < 0L ||
            batch.remainingQuantity > batch.receivedQuantity
        ) {
            return null
        }

        val remaining = deductDispensedStock(
            currentBalance = batch.remainingQuantity,
            dispensedBaseQuantity = dispensedBaseQuantity
        ) ?: return null

        return batch.copy(remainingQuantity = remaining)
    }

    /**
     * Calculates the total value of a dispensing line.
     *
     * The selling price is expressed in cents per selected dispensing unit.
     */
    fun calculateDispensingLineTotalCents(
        quantity: Long,
        sellingPricePerUnitCents: Long
    ): Long? {
        if (quantity <= 0L || sellingPricePerUnitCents < 0L) {
            return null
        }

        return try {
            Math.multiplyExact(quantity, sellingPricePerUnitCents)
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Calculates the total cost of multiple receipt lines.
     *
     * Returns null if any line has a negative cost or if addition overflows.
     */
    fun calculateReceiptTotalCents(
        lineTotalsCents: List<Long>
    ): Long? {
        if (lineTotalsCents.any { it < 0L }) {
            return null
        }

        return try {
            lineTotalsCents.fold(0L) { total, lineTotal ->
                Math.addExact(total, lineTotal)
            }
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Calculates the total amount of multiple dispensing lines.
     */
    fun calculateDispensingTotalCents(
        lineTotalsCents: List<Long>
    ): Long? {
        if (lineTotalsCents.any { it < 0L }) {
            return null
        }

        return try {
            lineTotalsCents.fold(0L) { total, lineTotal ->
                Math.addExact(total, lineTotal)
            }
        } catch (_: ArithmeticException) {
            null
        }
    }
}
