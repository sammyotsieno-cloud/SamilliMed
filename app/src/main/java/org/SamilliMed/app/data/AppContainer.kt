package org.SamilliMed.app.data

import android.content.Context
import core.domain.consumption.ConsumptionService
import core.domain.persistence.CoreDatabase
import core.domain.persistence.GoodsReceiptDao
import core.domain.persistence.InventoryCostLayerDao
import core.domain.persistence.ProductMasterDao
import core.domain.category.ProductCategoryRepository
import core.domain.persistence.ProductCategoryDao
import core.domain.persistence.ProductRecognitionDao
import core.domain.recognition.ProductRecognitionService
import core.domain.research.ProductResearchExportService
import core.domain.knowledge.KnowledgeSeeder
import kotlinx.coroutines.launch
import core.domain.persistence.SaleDao
import core.domain.persistence.StockAllocationDao
import core.domain.persistence.StockBatchDao
import core.domain.persistence.StockMovementDao
import core.domain.receiving.GoodsReceiptPersistenceService
import core.domain.reporting.InventoryValuationService

class AppContainer(context: Context) {
    val database: CoreDatabase = CoreDatabase.getInstance(context)

    val productMasterDao: ProductMasterDao = database.productMasterDao()
    val productCategoryDao: ProductCategoryDao = database.productCategoryDao()
    val productCategoryRepository: ProductCategoryRepository = ProductCategoryRepository(productCategoryDao, context.applicationContext)
    val productRecognitionDao: ProductRecognitionDao = database.productRecognitionDao()
    val productRecognitionService: ProductRecognitionService = ProductRecognitionService(productMasterDao, productRecognitionDao, productCategoryDao)
    val productResearchExportService: ProductResearchExportService = ProductResearchExportService(productMasterDao, productCategoryDao, productRecognitionDao)
    val goodsReceiptDao: GoodsReceiptDao = database.goodsReceiptDao()
    val stockBatchDao: StockBatchDao = database.stockBatchDao()
    val inventoryCostLayerDao: InventoryCostLayerDao = database.inventoryCostLayerDao()
    val stockMovementDao: StockMovementDao = database.stockMovementDao()
    val stockAllocationDao: StockAllocationDao = database.stockAllocationDao()
    val saleDao: SaleDao = database.saleDao()

    val receivingService: GoodsReceiptPersistenceService =
        GoodsReceiptPersistenceService(database)

    val consumptionService: ConsumptionService =
        ConsumptionService(database)

    val inventoryValuationService: InventoryValuationService =
        InventoryValuationService(inventoryCostLayerDao)

    init {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        ).launch {
            KnowledgeSeeder.seed(context.applicationContext, database)
            productCategoryRepository.ensureDefaultTaxonomy()
        }
    }
}
