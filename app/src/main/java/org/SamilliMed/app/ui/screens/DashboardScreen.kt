package org.SamilliMed.app.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.domain.model.ProductCategory
import core.domain.model.Sale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.SamilliMed.app.data.AppContainer
import org.SamilliMed.app.scanner.ProductScanDraft
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ============================================================================
// Authoritative Color Palette matching the visual reference
// ============================================================================
private val WarmCreamBackground = Color(0xFFF4F0E6)
private val DeepForestGreen = Color(0xFF244A38)
private val SalesCardGreenTop = Color(0xFF1E4332)
private val SalesCardGreenBottom = Color(0xFF143324)
private val PrimarySageGreen = Color(0xFF5C7A65)
private val LightSage = Color(0xFFDCE4D2)
private val WarmAmber = Color(0xFFD4A05B)
private val PaleCreamCard = Color(0xFFFAF7EF)
private val CardBorderColor = Color(0xFFEBE6DC)
private val DarkText = Color(0xFF1A2C23)
private val SubtitleText = Color(0xFF5C7A65)
private val AlertRed = Color(0xFFD34538)
private val MutedChevron = Color(0xFFA39E93)

private const val PREFS_NAME = "samillimed_dashboard"
private const val FACILITY_NAME_KEY = "facility_name"
private const val DEFAULT_FACILITY_NAME = "SamilliMed Medical Centre"

// 9 Required Tiles specification
private enum class TileIconType {
    GreenCircle,
    AmberCircle,
    GreenSquircle
}

private data class DashboardTileSpec(
    val id: String,
    val title: String,
    val iconType: TileIconType,
    val vectorIcon: ImageVector,
    val route: String
)

private val DASHBOARD_TILES = listOf(
    // Row 1
    DashboardTileSpec("adjustments", "Stock\nAdjustments", TileIconType.GreenCircle, Icons.Default.SwapVert, "adjustments"),
    DashboardTileSpec("receiving", "Goods\nReceiving", TileIconType.AmberCircle, Icons.Default.Inventory2, "receiving"),
    DashboardTileSpec("dispensing", "Dispensing", TileIconType.GreenCircle, Icons.Default.Medication, "dispensing"),
    // Row 2
    DashboardTileSpec("inventory", "Inventory", TileIconType.GreenCircle, Icons.Default.Layers, "inventory"),
    DashboardTileSpec("drug_index", "Drug Index", TileIconType.AmberCircle, Icons.Default.Vaccines, "products"),
    DashboardTileSpec("goods_reg", "Goods\nRegistration", TileIconType.GreenCircle, Icons.Default.AssignmentAdd, "goods-registration"),
    // Row 3
    DashboardTileSpec("reports", "Reports", TileIconType.AmberCircle, Icons.Default.BarChart, "reports"),
    DashboardTileSpec("suppliers", "Suppliers", TileIconType.GreenCircle, Icons.Default.Groups, "suppliers"),
    DashboardTileSpec("services", "Services Index", TileIconType.GreenSquircle, Icons.Default.MedicalServices, "services")
)

@Composable
fun DashboardScreen(
    onFeatureClick: (String) -> Unit,
    container: AppContainer? = null,
    onScanProduct: (() -> Unit)? = null,
    initialScanDraft: ProductScanDraft? = null,
    onScanDraftConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var facility by rememberSaveable {
        mutableStateOf(
            prefs.getString(FACILITY_NAME_KEY, DEFAULT_FACILITY_NAME)
                ?.takeIf { it.isNotBlank() }
                ?.take(48)
                ?: DEFAULT_FACILITY_NAME
        )
    }

    var showFacilityDialog by remember { mutableStateOf(false) }
    var showRegisterProductDialog by remember { mutableStateOf(initialScanDraft != null) }
    var showCatalogDialog by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<ProductCategory>>(emptyList()) }

    // Real Data States
    var todaysSalesMinor by remember { mutableLongStateOf(0L) }
    var yesterdaySalesMinor by remember { mutableLongStateOf(0L) }
    var salesComparisonText by remember { mutableStateOf("No sales yet today") }
    var salesSparklinePoints by remember { mutableStateOf<List<Float>>(listOf(0.1f, 0.25f, 0.5f, 0.65f, 0.9f)) }

    var todaysExpensesMinor by remember { mutableLongStateOf(0L) }
    var yesterdayExpensesMinor by remember { mutableLongStateOf(0L) }
    var expensesComparisonText by remember { mutableStateOf("— vs. yesterday") }

    var expiryAlertText by remember { mutableStateOf("Checking expiry...") }
    var hasActiveExpiring by remember { mutableStateOf(false) }

    val formattedTodayDate = remember {
        val sdf = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        sdf.format(Date())
    }

    // Query real sales & expiry data from Room DAOs
    LaunchedEffect(container) {
        if (container != null) {
            withContext(Dispatchers.IO) {
                // Ensure categories for registration dialog
                container.productCategoryRepository.ensureDefaultTaxonomy()
                categories = container.productCategoryRepository.getActive()

                // Calculate Today's Sales
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfToday = cal.timeInMillis

                cal.add(Calendar.DAY_OF_YEAR, -1)
                val startOfYesterday = cal.timeInMillis

                val allSales = try {
                    container.saleDao.getAllSales().filter { it.status == Sale.STATUS_COMPLETED }
                } catch (_: Exception) {
                    emptyList()
                }

                val todaySales = allSales.filter { it.occurredAt >= startOfToday }
                val yesterdaySales = allSales.filter { it.occurredAt in startOfYesterday until startOfToday }

                val todaySum = todaySales.sumOf { it.totalSellingAmount.amountMinorUnits }
                val yesterdaySum = yesterdaySales.sumOf { it.totalSellingAmount.amountMinorUnits }

                todaysSalesMinor = todaySum
                yesterdaySalesMinor = yesterdaySum

                salesComparisonText = when {
                    yesterdaySum > 0L -> {
                        val pct = ((todaySum - yesterdaySum) * 100) / yesterdaySum
                        if (pct >= 0) "↑ $pct% vs. yesterday" else "↓ ${kotlin.math.abs(pct)}% vs. yesterday"
                    }
                    todaySum > 0L -> "↑ 100% vs. yesterday"
                    else -> "No sales yet today"
                }

                // Calculate cumulative curve points for sparkline if sales exist
                if (todaySales.isNotEmpty()) {
                    val sorted = todaySales.sortedBy { it.occurredAt }
                    var running = 0L
                    val points = mutableListOf<Float>()
                    val total = todaySum.coerceAtLeast(1L).toFloat()
                    for (s in sorted) {
                        running += s.totalSellingAmount.amountMinorUnits
                        points.add(running.toFloat() / total)
                    }
                    if (points.size >= 3) {
                        salesSparklinePoints = points
                    }
                }

                // Calculate Real Expiry Alerts
                try {
                    val batches = container.stockBatchDao.getAllBatches()
                    val movements = container.stockMovementDao.getAllMovements()
                    val movementsByBatch = movements.filter { it.stockBatchId != null }.groupBy { it.stockBatchId!! }

                    val nowCal = Calendar.getInstance()
                    val y = nowCal.get(Calendar.YEAR)
                    val m = nowCal.get(Calendar.MONTH) + 1
                    val d = nowCal.get(Calendar.DAY_OF_MONTH)
                    val todayInt = (y * 10000) + (m * 100) + d

                    nowCal.add(Calendar.DAY_OF_YEAR, 90)
                    val in90DaysInt = (nowCal.get(Calendar.YEAR) * 10000) + ((nowCal.get(Calendar.MONTH) + 1) * 100) + nowCal.get(Calendar.DAY_OF_MONTH)

                    var expiredCount = 0
                    var expiringSoonCount = 0

                    for (batch in batches) {
                        val batchMovements = movementsByBatch[batch.id] ?: emptyList()
                        val physicalUnits = batchMovements.sumOf { it.quantityDeltaStorageUnits }
                        if (physicalUnits > 0L && batch.expiryDateInt != null) {
                            if (batch.expiryDateInt < todayInt) {
                                expiredCount++
                            } else if (batch.expiryDateInt in todayInt..in90DaysInt) {
                                expiringSoonCount++
                            }
                        }
                    }

                    if (expiredCount > 0 && expiringSoonCount > 0) {
                        expiryAlertText = "$expiredCount expired, $expiringSoonCount expiring soon"
                        hasActiveExpiring = true
                    } else if (expiredCount > 0) {
                        expiryAlertText = "$expiredCount items expired"
                        hasActiveExpiring = true
                    } else if (expiringSoonCount > 0) {
                        expiryAlertText = "$expiringSoonCount items expiring soon"
                        hasActiveExpiring = true
                    } else {
                        expiryAlertText = "No items expiring soon"
                        hasActiveExpiring = false
                    }
                } catch (_: Exception) {
                    expiryAlertText = "No items expiring soon"
                    hasActiveExpiring = false
                }
            }
        }
    }

    LaunchedEffect(initialScanDraft) {
        if (initialScanDraft != null) {
            showRegisterProductDialog = true
        }
    }

    // Three Vertically Coordinated Regions:
    // 1. Fixed Header
    // 2. Independently scrollable main content (LazyColumn)
    // 3. Fixed Bottom Navigation
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WarmCreamBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ----------------------------------------------------------------
            // 1. FIXED HEADER
            // ----------------------------------------------------------------
            DashboardAuthoritativeHeader(
                facilityName = facility,
                onFacilityClick = { showFacilityDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )

            // ----------------------------------------------------------------
            // 2. INDEPENDENTLY SCROLLABLE CONTENT REGION
            // ----------------------------------------------------------------
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // A. 3x3 Tile Grid (9 Tiles)
                item(key = "nine_dashboard_tiles") {
                    AuthoritativeTilesGrid(
                        tiles = DASHBOARD_TILES,
                        onTileClick = { route ->
                            if (route == "goods-registration") {
                                showRegisterProductDialog = true
                            } else {
                                onFeatureClick(route)
                            }
                        }
                    )
                }

                // B. Today's Sales Card
                item(key = "todays_sales_card") {
                    TodaysSalesInformationCard(
                        dateText = formattedTodayDate,
                        salesMinor = todaysSalesMinor,
                        comparisonText = salesComparisonText,
                        sparklinePoints = salesSparklinePoints,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // C. Today's Expenses Card
                item(key = "todays_expenses_card") {
                    TodaysExpensesInformationCard(
                        dateText = formattedTodayDate,
                        expensesMinor = todaysExpensesMinor,
                        comparisonText = expensesComparisonText,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // D. Expiry Alerts Card
                item(key = "expiry_alerts_card") {
                    ExpiryAlertsInformationCard(
                        dateText = formattedTodayDate,
                        statusText = expiryAlertText,
                        onClick = { onFeatureClick("alerts") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Extra breathing spacer at the bottom of the scrollable content
                item(key = "scroll_bottom_spacer") {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // ----------------------------------------------------------------
            // 3. FIXED BOTTOM NAVIGATION BAR
            // ----------------------------------------------------------------
            DashboardAuthoritativeBottomBar(
                onSettingsClick = { onFeatureClick("settings") },
                onDashboardClick = { /* Already on dashboard */ },
                onReceivingClick = { onFeatureClick("receiving") },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }

    // Dialog: Facility Name Selector / Editor
    if (showFacilityDialog) {
        FacilitySelectorDialog(
            currentName = facility,
            onDismiss = { showFacilityDialog = false },
            onSave = { newName ->
                facility = newName
                prefs.edit().putString(FACILITY_NAME_KEY, newName).apply()
                showFacilityDialog = false
            }
        )
    }

    // Dialog: Goods Registration
    if (showRegisterProductDialog && container != null) {
        AddProductDialog(
            container = container,
            categories = categories,
            initialScanDraft = initialScanDraft,
            onDismiss = {
                showRegisterProductDialog = false
                onScanDraftConsumed()
            },
            onProductSaved = {
                showRegisterProductDialog = false
                onScanDraftConsumed()
            }
        )
    }

    // Dialog: Product Catalog
    if (showCatalogDialog && container != null) {
        ProductCatalogManagementDialog(
            container = container,
            onDismiss = { showCatalogDialog = false },
            onScanProduct = {
                showCatalogDialog = false
                if (onScanProduct != null) onScanProduct() else onFeatureClick("product-scanner")
            }
        )
    }
}

// ============================================================================
// Header Component matching Authoritative Visual Reference
// ============================================================================
@Composable
private fun DashboardAuthoritativeHeader(
    facilityName: String,
    onFacilityClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color(0xFF6B5844).copy(alpha = 0.10f),
                spotColor = Color(0xFF6B5844).copy(alpha = 0.12f)
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = PaleCreamCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Background 3D Spheres peeking behind the facility selector
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(130.dp, 60.dp)
            ) {
                val w = size.width
                val h = size.height

                // Amber sphere
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFD59E), Color(0xFFD4A05B), Color(0xFF9E6520)),
                        center = Offset(w * 0.35f, h * 0.40f),
                        radius = 28f
                    ),
                    radius = 26f,
                    center = Offset(w * 0.38f, h * 0.55f)
                )

                // Large sage/green sphere
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF8BB79B), Color(0xFF4A6B53), Color(0xFF244A38)),
                        center = Offset(w * 0.75f, h * 0.30f),
                        radius = 42f
                    ),
                    radius = 38f,
                    center = Offset(w * 0.80f, h * 0.45f)
                )

                // Small white pearl sphere
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFFE5E5E5), Color(0xFFAAAAAA)),
                        center = Offset(w * 0.55f, h * 0.65f),
                        radius = 16f
                    ),
                    radius = 14f,
                    center = Offset(w * 0.58f, h * 0.72f)
                )
            }

            // Left Content: Logo + Brand + Slogan
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Medical Cross with Leaf
                SamilliMedCrossLogo(modifier = Modifier.size(44.dp))

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "SamilliMed",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DarkText,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = "Better Health • Brighter Tomorrow",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = SubtitleText,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Right Content: Facility Pill Dropdown
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onFacilityClick)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Facility: $facilityName. Tap to change."
                    },
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0DDD5)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalHospital,
                        contentDescription = null,
                        tint = DeepForestGreen,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Column {
                        Text(
                            text = "Facility",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF6B7280)
                        )
                        Text(
                            text = facilityName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 135.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = DarkText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * SamilliMed Cross Logo with Leaf Accent
 */
@Composable
private fun SamilliMedCrossLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barThickness = w * 0.38f
        val cornerRadius = CornerRadius(w * 0.12f, h * 0.12f)

        // Draw deep green medical cross
        drawRoundRect(
            color = DeepForestGreen,
            topLeft = Offset((w - barThickness) / 2f, h * 0.08f),
            size = Size(barThickness, h * 0.84f),
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            color = DeepForestGreen,
            topLeft = Offset(w * 0.08f, (h - barThickness) / 2f),
            size = Size(w * 0.84f, barThickness),
            cornerRadius = cornerRadius
        )

        // Leaf motif inside the center of cross
        val path = Path().apply {
            moveTo(w * 0.38f, h * 0.58f)
            quadraticBezierTo(w * 0.38f, h * 0.34f, w * 0.62f, h * 0.34f)
            quadraticBezierTo(w * 0.62f, h * 0.58f, w * 0.38f, h * 0.58f)
            close()
        }
        drawPath(
            path = path,
            color = Color(0xFFDCE4D2)
        )
    }
}

// ============================================================================
// 3x3 Tile Grid Component (9 Required Tiles in exact order)
// ============================================================================
@Composable
private fun AuthoritativeTilesGrid(
    tiles: List<DashboardTileSpec>,
    onTileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val row1 = tiles.take(3)
        val row2 = tiles.drop(3).take(3)
        val row3 = tiles.drop(6).take(3)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row1.forEach { tile ->
                DashboardTileItem(
                    spec = tile,
                    onClick = { onTileClick(tile.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row2.forEach { tile ->
                DashboardTileItem(
                    spec = tile,
                    onClick = { onTileClick(tile.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row3.forEach { tile ->
                DashboardTileItem(
                    spec = tile,
                    onClick = { onTileClick(tile.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DashboardTileItem(
    spec: DashboardTileSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(0.98f)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color(0xFF6B5844).copy(alpha = 0.08f),
                spotColor = Color(0xFF6B5844).copy(alpha = 0.10f)
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = spec.title.replace("\n", " ")
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PaleCreamCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(
                        if (spec.iconType == TileIconType.GreenSquircle)
                            RoundedCornerShape(14.dp)
                        else
                            CircleShape
                    )
                    .background(
                        when (spec.iconType) {
                            TileIconType.GreenCircle, TileIconType.GreenSquircle -> DeepForestGreen
                            TileIconType.AmberCircle -> WarmAmber
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spec.vectorIcon,
                    contentDescription = null,
                    tint = if (spec.id == "services") Color(0xFFFFD59E) else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Title Label
            Text(
                text = spec.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkText,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            // Small Chevron at bottom
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MutedChevron,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ============================================================================
// 1. Today's Sales Information Card (Dark Forest Green with Area Sparkline)
// ============================================================================
@Composable
private fun TodaysSalesInformationCard(
    dateText: String,
    salesMinor: Long,
    comparisonText: String,
    sparklinePoints: List<Float>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = DeepForestGreen.copy(alpha = 0.20f),
                spotColor = DeepForestGreen.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SalesCardGreenTop)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SalesCardGreenTop, SalesCardGreenBottom)
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Figures & Trends
                Column(modifier = Modifier.weight(1.1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF335C47)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "Today's Sales",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = dateText,
                                fontSize = 11.sp,
                                color = Color(0xFF9ABCA6)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val formattedShillings = String.format(Locale.US, "KSh %,d", salesMinor / 100)
                    Text(
                        text = if (salesMinor > 0) formattedShillings else "KSh 0",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = comparisonText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF86EFAC)
                    )
                }

                // Right Column: Glowing Area Sparkline with Timestamps
                Column(
                    modifier = Modifier.weight(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SalesAreaSparkline(
                        points = sparklinePoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("8am", "10am", "12pm", "2pm", "4pm").forEach { hour ->
                            Text(
                                text = hour,
                                fontSize = 9.sp,
                                color = Color(0xFF9ABCA6),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesAreaSparkline(
    points: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        if (points.isEmpty()) return@Canvas

        val stepX = w / (points.size - 1).coerceAtLeast(1)
        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { i, p ->
            val x = i * stepX
            val y = h - (p.coerceIn(0.08f, 0.95f) * h)
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(w, h)
        fillPath.close()

        // Draw gradient area fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF4ADE80).copy(alpha = 0.35f),
                    Color(0xFF4ADE80).copy(alpha = 0.05f)
                )
            )
        )

        // Draw line
        drawPath(
            path = path,
            color = Color(0xFF4ADE80),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw glowing point on the last dot
        val lastX = (points.size - 1) * stepX
        val lastY = h - (points.last().coerceIn(0.08f, 0.95f) * h)
        drawCircle(
            color = Color(0xFF86EFAC),
            radius = 4.dp.toPx(),
            center = Offset(lastX, lastY)
        )
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = Offset(lastX, lastY)
        )
    }
}

// ============================================================================
// 2. Today's Expenses Information Card (Pale Cream with Amber Bar Chart)
// ============================================================================
@Composable
private fun TodaysExpensesInformationCard(
    dateText: String,
    expensesMinor: Long,
    comparisonText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0xFF6B5844).copy(alpha = 0.08f),
                spotColor = Color(0xFF6B5844).copy(alpha = 0.10f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PaleCreamCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column
            Column(modifier = Modifier.weight(1.1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(WarmAmber),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Today's Expenses",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Text(
                            text = dateText,
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val formattedShillings = String.format(Locale.US, "KSh %,d", expensesMinor / 100)
                Text(
                    text = if (expensesMinor > 0) formattedShillings else "KSh 0",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkText,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = comparisonText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = PrimarySageGreen
                )
            }

            // Right Column: Amber Vertical Bar Chart with Timestamps
            Column(
                modifier = Modifier.weight(0.9f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ExpensesBarChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("8am", "10am", "12pm", "2pm", "4pm").forEach { hour ->
                        Text(
                            text = hour,
                            fontSize = 9.sp,
                            color = Color(0xFF8C8479),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensesBarChart(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 7 representative bars matching reference
        val barHeights = listOf(0.20f, 0.40f, 0.55f, 0.45f, 0.52f, 0.70f, 0.58f, 0.85f)
        val barWidth = 8.dp.toPx()
        val spacing = (w - (barWidth * barHeights.size)) / (barHeights.size - 1).coerceAtLeast(1)

        barHeights.forEachIndexed { i, frac ->
            val barH = frac * h
            val x = i * (barWidth + spacing)
            val y = h - barH
            drawRoundRect(
                color = WarmAmber.copy(alpha = if (i % 2 == 0) 0.65f else 0.95f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

// ============================================================================
// 3. Expiry Alerts Information Card (Pale Warm with Red Alert Badge)
// ============================================================================
@Composable
private fun ExpiryAlertsInformationCard(
    dateText: String,
    statusText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0xFF6B5844).copy(alpha = 0.08f),
                spotColor = Color(0xFF6B5844).copy(alpha = 0.10f)
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Expiry Alerts: $statusText. Tap to view batches."
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PaleCreamCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circular Red Alert Badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AlertRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PriorityHigh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Expiry Alerts",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text(
                        text = dateText,
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MutedChevron,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ============================================================================
// Fixed Bottom Navigation Bar (Deep Forest Green with Raised Dashboard Badge)
// ============================================================================
@Composable
private fun DashboardAuthoritativeBottomBar(
    onSettingsClick: () -> Unit,
    onDashboardClick: () -> Unit,
    onReceivingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = DeepForestGreen.copy(alpha = 0.35f),
                spotColor = DeepForestGreen.copy(alpha = 0.45f)
            ),
        shape = RoundedCornerShape(28.dp),
        color = DeepForestGreen
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Settings Destination
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSettingsClick)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFFC5D6CC),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Settings",
                    fontSize = 11.sp,
                    color = Color(0xFFC5D6CC),
                    fontWeight = FontWeight.Medium
                )
            }

            // 2. Dashboard Destination (Prominently Raised Green Icon)
            Column(
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .clickable(onClick = onDashboardClick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF3B6647),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF528562)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Dashboard",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Dashboard",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // 3. Receiving Destination
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onReceivingClick)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = "Receiving",
                    tint = Color(0xFFC5D6CC),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Receiving",
                    fontSize = 11.sp,
                    color = Color(0xFFC5D6CC),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ============================================================================
// Facility Selector Dialog
// ============================================================================
@Composable
private fun FacilitySelectorDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draftName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalHospital,
                    contentDescription = null,
                    tint = DeepForestGreen
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Facility Profile", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Configure or select the active clinic/hospital dispensary facility name:",
                    fontSize = 13.sp,
                    color = Color(0xFF4B5563)
                )
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it.take(48) },
                    label = { Text("Facility Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = draftName.trim().ifBlank { DEFAULT_FACILITY_NAME }
                    onSave(trimmed)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DeepForestGreen)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF6B7280))
            }
        }
    )
}
