package org.samillimed.app.ui.stockcheck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.data.catalog.genericNames
import org.samillimed.app.data.catalog.rankedForSearch
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.inventory.InventoryReader
import org.samillimed.app.data.inventory.StockBatch
import org.samillimed.app.data.inventory.formatMixedUnits
import org.samillimed.app.data.stockcheck.CountedInput
import org.samillimed.app.data.stockcheck.StockCheck
import org.samillimed.app.data.stockcheck.StockCheckDraft
import org.samillimed.app.data.stockcheck.StockCheckRepository
import org.samillimed.app.data.stockcheck.StockCheckStatus
import org.samillimed.app.data.stockcheck.StockCheckType
import org.samillimed.app.data.stockcheck.baseFromParts
import org.samillimed.app.data.stockcheck.partsFromBase
import org.samillimed.app.data.stockcheck.unitsLargestFirst
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.common.SearchField
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.inventory.InvHeaderPill
import org.samillimed.app.ui.inventory.fmtDayTime
import org.samillimed.app.ui.theme.SamilliTheme
import java.util.UUID

private const val DAILY_SIZE = 10

/** Counting what is on the shelves, without seeing what the app expects. An administrator reviews the result. */
@Composable
fun StockCheckHost(catalog: Catalog, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { StockCheckRepository() }
    val reader = remember { InventoryReader() }
    val batches by produceState<List<StockBatch>?>(null, reader) { reader.activeBatchesFlow().collect { value = it } }
    var page by rememberSaveable { mutableStateOf("start") }
    var openCheckId by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(StockCheckDraft.load(context)) }

    BackHandler(enabled = page != "start") { page = if (page == "reviewDetail") "reviewList" else "start" }

    when (page) {
        "count" -> {
            val d = draft
            if (d == null) {
                StartPage(catalog, batches, repository, null, { t, ids -> draft = StockCheckDraft.Draft(t, ids, emptyMap()); page = "count" }, { page = "count" }, { page = "reviewList" }, onBack, modifier)
            } else {
                CountPage(
                    catalog = catalog,
                    draft = d,
                    repository = repository,
                    onSubmitted = {
                        StockCheckDraft.clear(context)
                        draft = null
                        page = "done"
                    },
                    onLeave = { page = "start" },
                    onDiscard = {
                        StockCheckDraft.clear(context)
                        draft = null
                        page = "start"
                    },
                    modifier = modifier,
                )
            }
        }
        "done" -> DonePage(onDone = { page = "start" }, modifier = modifier)
        "reviewList" -> ReviewList(
            repository = repository,
            onOpen = { openCheckId = it; page = "reviewDetail" },
            onBack = { page = "start" },
            modifier = modifier,
        )
        "reviewDetail" -> ReviewDetail(
            catalog = catalog,
            batches = batches,
            repository = repository,
            checkId = openCheckId.orEmpty(),
            onBack = { page = "reviewList" },
            modifier = modifier,
        )
        else -> StartPage(
            catalog = catalog,
            batches = batches,
            repository = repository,
            draft = draft,
            onStart = { type, ids ->
                val d = StockCheckDraft.Draft(type, ids, emptyMap())
                StockCheckDraft.save(context, d)
                draft = d
                page = "count"
            },
            onContinue = { page = "count" },
            onReview = { page = "reviewList" },
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun StartPage(
    catalog: Catalog,
    batches: List<StockBatch>?,
    repository: StockCheckRepository,
    draft: StockCheckDraft.Draft?,
    onStart: (String, List<String>) -> Unit,
    onContinue: () -> Unit,
    onReview: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val c = SamilliTheme.colors
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val lastChecked by produceState<Map<String, Long>?>(null) {
        repository.loadLastChecked(onResult = { value = it }, onFailure = { value = emptyMap() })
    }
    val checks by produceState<List<StockCheck>?>(null) { repository.checksFlow().collect { value = it } }
    val stocked = remember(batches, catalog.products) {
        val ids = batches.orEmpty().filter { it.remainingQuantity > 0L }.map { it.productId }.toSet()
        catalog.products.filter { it.active && it.id in ids }
    }
    val waiting = checks.orEmpty().count { it.status == StockCheckStatus.SUBMITTED }
    val ready = batches != null && lastChecked != null

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Stock check",
            subtitle = "Count the shelves and compare",
            onBack = onBack,
            actions = {
                if (CurrentUser.isAdmin) InvHeaderPill(if (waiting > 0) "Review ($waiting)" else "Review", onClick = onReview)
            },
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (draft != null) {
                item {
                    SamilliCard(Modifier.fillMaxWidth().clickable(onClick = onContinue)) {
                        Text("Continue your unfinished count", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${StockCheckType.label(draft.type)}: ${draft.counts.size} of ${draft.productIds.size} products counted. Saved on this phone.",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            item {
                Text(
                    "You will not see how many the app expects. Type what you find on the shelf, then send it for review.",
                    color = c.textSecondary,
                    fontSize = 12.sp,
                )
            }
            item {
                StartCard(
                    "Daily check",
                    "The $DAILY_SIZE products that were checked longest ago.",
                    enabled = ready && stocked.isNotEmpty(),
                ) {
                    val ids = stocked.sortedBy { lastChecked?.get(it.id) ?: 0L }.take(DAILY_SIZE).map { it.id }
                    onStart(StockCheckType.DAILY, ids)
                }
            }
            item {
                StartCard(
                    "Full check",
                    "Every product that has stock. This takes a while, so choose a quiet time.",
                    enabled = ready && stocked.isNotEmpty(),
                ) { onStart(StockCheckType.FULL, stocked.map { it.id }) }
            }
            item {
                StartCard("Pick products", "Choose the products you want to check.", enabled = ready) {
                    onStart(StockCheckType.SPOT, emptyList())
                }
            }
            if (!ready) item { Text("Getting ready…", color = c.textSecondary, fontSize = 12.sp) }
            item {
                Text("Recent checks", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }
            val list = checks
            when {
                list == null -> item { Text("Loading…", color = c.textSecondary, fontSize = 12.sp) }
                list.isEmpty() -> item { Text("No checks yet.", color = c.textSecondary, fontSize = 12.sp) }
                else -> items(list, key = { it.id }) { ch ->
                    SamilliCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        Text(
                            "${StockCheckType.label(ch.type)} · ${ch.lineCount} products",
                            color = c.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${fmtDayTime(ch.createdAtMillis)} · by ${ch.createdBy.substringBefore('@')}",
                            color = c.textSecondary,
                            fontSize = 11.sp,
                        )
                        Text(
                            if (ch.status == StockCheckStatus.REVIEWED) "Reviewed" else "Waiting for an administrator to review",
                            color = if (ch.status == StockCheckStatus.REVIEWED) c.deltaText else c.accentText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartCard(title: String, text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Text(title, color = if (enabled) c.textPrimary else c.textMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text, color = c.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun DonePage(onDone: () -> Unit, modifier: Modifier) {
    val c = SamilliTheme.colors
    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(title = "Stock check", subtitle = "Sent", onBack = onDone)
        EmptyState(
            icon = SamilliIcons.Box,
            title = "Count sent for review",
            message = "An administrator will compare it with the stock records. Differences are not shown here.",
        )
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
        ) { Text("Done", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun CountPage(
    catalog: Catalog,
    draft: StockCheckDraft.Draft,
    repository: StockCheckRepository,
    onSubmitted: () -> Unit,
    onLeave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier,
) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val submitId = rememberSaveable { UUID.randomUUID().toString() }
    val productIds = remember { mutableStateListOf<String>().apply { addAll(draft.productIds) } }
    val counts = remember { mutableStateMapOf<String, Pair<Long, Long>>().apply { putAll(draft.counts) } }
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun persist() {
        StockCheckDraft.save(context, StockCheckDraft.Draft(draft.type, productIds.toList(), counts.toMap()))
    }

    val products = remember(productIds.toList(), catalog.products) {
        productIds.mapNotNull { id -> catalog.products.firstOrNull { it.id == id } }
            .sortedBy { it.name.lowercase() }
    }
    val q = query.trim().lowercase()
    val visible = if (q.isEmpty()) products else products.filter { it.name.lowercase().contains(q) || it.genericNames.lowercase().contains(q) }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = StockCheckType.label(draft.type),
            subtitle = "${counts.size} of ${products.size} counted",
            onBack = onLeave,
            actions = { InvHeaderPill("Add product") { showAdd = true } },
        )
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search the list",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        if (products.isEmpty()) {
            EmptyState(
                icon = SamilliIcons.Box,
                title = "No products yet",
                message = "Tap Add product to choose what you want to count.",
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(visible, key = { it.id }) { p ->
                CountRow(
                    product = p,
                    counted = counts[p.id]?.first,
                    onChange = { base ->
                        if (base == null) counts.remove(p.id) else counts[p.id] = base to System.currentTimeMillis()
                        persist()
                    },
                )
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp + navBottom)) {
            error?.let { Text(it, color = c.urgent, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = counts.isNotEmpty() && !saving,
                    onClick = { confirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
                    modifier = Modifier.weight(1f),
                ) { Text(if (saving) "Sending…" else "Send for review", fontWeight = FontWeight.Bold) }
                TextButton(onClick = onDiscard, enabled = !saving) { Text("Discard") }
            }
        }
    }

    if (showAdd) {
        AddProductDialog(
            products = catalog.products.filter { it.active },
            existing = productIds.toSet(),
            onPick = { p ->
                productIds.add(p.id)
                persist()
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    if (confirm) {
        val uncounted = products.size - counts.size
        AlertDialog(
            onDismissRequest = { if (!saving) confirm = false },
            title = { Text("Send this count?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You counted ${counts.size} products." +
                        (if (uncounted > 0) " $uncounted on the list were not counted and will be left out." else "") +
                        " An administrator will review it. You will not see the differences.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        confirm = false
                        saving = true
                        error = null
                        repository.submit(
                            id = submitId,
                            type = draft.type,
                            counted = counts.entries.mapNotNull { (id, v) ->
                                val p = catalog.products.firstOrNull { it.id == id } ?: return@mapNotNull null
                                CountedInput(id, p.name, v.first, v.second)
                            },
                            note = "",
                            onSuccess = onSubmitted,
                            onFailure = { message ->
                                saving = false
                                error = message
                            },
                        )
                    },
                ) { Text("Yes, send", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Go back") } },
        )
    }
}

@Composable
private fun CountRow(product: Product, counted: Long?, onChange: (Long?) -> Unit) {
    val c = SamilliTheme.colors
    val units = remember(product.id) { unitsLargestFirst(product.units) }
    val texts = remember(product.id) { mutableStateListOf<String>().apply { repeat(units.size) { add("") } } }
    LaunchedEffect(product.id) {
        if (counted != null && counted > 0L) {
            partsFromBase(counted, product.units).forEachIndexed { i, (_, n) -> texts[i] = if (n > 0L) n.toString() else "" }
        }
    }
    fun update() {
        val any = texts.any { it.isNotBlank() }
        val parts = units.mapIndexed { i, u -> u to (texts[i].toLongOrNull() ?: 0L) }
        onChange(if (any) baseFromParts(parts) else null)
    }
    SamilliCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Text(product.name, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        val detail = listOf(product.genericNames, product.dosageForm).filter { it.isNotBlank() }.joinToString(" · ")
        if (detail.isNotBlank()) Text(detail, color = c.textSecondary, fontSize = 11.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            units.forEachIndexed { i, u ->
                OutlinedTextField(
                    value = texts[i],
                    onValueChange = { v ->
                        texts[i] = v.filter { ch -> ch.isDigit() }.take(7)
                        update()
                    },
                    label = { Text(u.name) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    counted == null -> "Not counted yet"
                    counted == 0L -> "Counted: none on the shelf"
                    else -> "Counted: " + formatMixedUnits(counted, product.units)
                },
                color = if (counted == null) c.textMuted else c.deltaText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                for (i in texts.indices) texts[i] = ""
                onChange(0L)
            }) { Text("None on the shelf", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun AddProductDialog(
    products: List<Product>,
    existing: Set<String>,
    onPick: (Product) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SamilliTheme.colors
    var q by remember { mutableStateOf("") }
    val matches = remember(q, products, existing) {
        val needle = q.trim().lowercase()
        rankedForSearch(products.filter { it.id !in existing }, q)
            .filter { needle.isEmpty() || it.name.lowercase().contains(needle) || it.genericNames.lowercase().contains(needle) }
            .take(30)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a product", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                SearchField(value = q, onValueChange = { q = it }, placeholder = "Search products")
                Spacer(Modifier.padding(4.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(matches, key = { it.id }) { p ->
                        Text(
                            p.name,
                            color = c.textPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
