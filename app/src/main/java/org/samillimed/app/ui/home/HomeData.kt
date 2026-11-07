package org.samillimed.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.inventory.InventoryReader
import org.samillimed.app.data.inventory.StockAlerts
import org.samillimed.app.data.inventory.StockBatch
import org.samillimed.app.data.inventory.buildStockAlerts
import org.samillimed.app.data.reports.DailySummary
import org.samillimed.app.data.reports.ReportsReader
import java.time.LocalDate

/** Everything the Home screen shows. Alerts is null while the stock is still loading. */
class HomeData(
    val date: LocalDate,
    val salesLoaded: Boolean,
    val today: DailySummary,
    val yesterday: DailySummary,
    /** Last 7 days, oldest first, today last. */
    val weekSalesCents: List<Long>,
    val weekProfitCents: List<Long>,
    val alerts: StockAlerts?,
)

/**
 * Live data for Home and Alerts. Call it once while someone is signed in (the database refuses
 * signed-out requests) and keep the result for the whole session: it reads the last 7 day totals
 * and the batches that still hold stock, and the app updates it as sales and receipts happen.
 * The day rolls over by itself if the app stays open past midnight.
 */
@Composable
fun rememberHomeData(catalog: Catalog): HomeData {
    val reports = remember { ReportsReader() }
    val inventory = remember { InventoryReader() }

    val today by produceState(LocalDate.now()) {
        while (true) {
            delay(60_000L)
            val now = LocalDate.now()
            if (now != value) value = now
        }
    }
    val summaries by produceState<List<DailySummary>?>(null, today) {
        reports.summariesFlow(today.minusDays(6).toString(), today.toString()).collect { value = it }
    }
    val batches by produceState<List<StockBatch>?>(null, inventory) {
        inventory.activeBatchesFlow().collect { value = it }
    }

    val alerts = remember(catalog.products, catalog.loaded, batches, today) {
        val list = batches
        if (list == null || !catalog.loaded) null else buildStockAlerts(catalog.products, list, today)
    }

    val byDate = (summaries ?: emptyList()).associateBy { it.dateKey }
    val days = (0..6).map { today.minusDays((6 - it).toLong()) }
    return HomeData(
        date = today,
        salesLoaded = summaries != null,
        today = byDate[today.toString()] ?: DailySummary(),
        yesterday = byDate[today.minusDays(1).toString()] ?: DailySummary(),
        weekSalesCents = days.map { byDate[it.toString()]?.totalSalesCents ?: 0L },
        weekProfitCents = days.map { byDate[it.toString()]?.profitCents ?: 0L },
        alerts = alerts,
    )
}
