package core.domain.research

import core.domain.persistence.ProductCategoryDao
import core.domain.persistence.ProductMasterDao
import core.domain.persistence.ProductRecognitionDao
import org.json.JSONArray
import org.json.JSONObject

class ProductResearchExportService(
    private val productDao: ProductMasterDao,
    private val categoryDao: ProductCategoryDao,
    private val recognitionDao: ProductRecognitionDao
) {
    suspend fun exportJson(): String {
        val root = JSONObject()
            .put("schemaVersion", 1)
            .put("exportType", "SamilliMedProductResearch")
            .put("exportedAt", System.currentTimeMillis())
            .put(
                "privacy",
                JSONObject()
                    .put("patientDataIncluded", false)
                    .put("financialTransactionDataIncluded", false)
                    .put("staffDataIncluded", false)
                    .put("facilitySecretsIncluded", false)
            )

        root.put("taxonomy", JSONArray().apply {
            categoryDao.getAll().forEach { category ->
                put(
                    JSONObject()
                        .put("id", category.id)
                        .put("parentCategoryId", category.parentCategoryId)
                        .put("name", category.name)
                        .put("description", category.description)
                        .put("isActive", category.isActive)
                        .put("sortOrder", category.sortOrder)
                        .put("isSystemDefault", category.isSystemDefault)
                        .put("createdAt", category.createdAt)
                        .put("updatedAt", category.updatedAt)
                )
            }
        })

        root.put("products", JSONArray().apply {
            productDao.getAllProducts().forEach { product ->
                put(
                    JSONObject()
                        .put("id", product.id)
                        .put("brandName", product.brandName)
                        .put("genericName", product.genericName)
                        .put("productType", product.productType)
                        .put("categoryId", product.categoryId)
                        .put("manufacturer", product.manufacturer)
                        .put("description", product.description)
                        .put("isActive", product.isActive)
                        .put("createdAt", product.createdAt)
                        .put("updatedAt", product.updatedAt)
                )
            }
        })

        root.put("recognitionIdentifiers", JSONArray().apply {
            recognitionDao.getAllIdentifiers().forEach { identifier ->
                put(
                    JSONObject()
                        .put("id", identifier.id)
                        .put("productId", identifier.productId)
                        .put("identifierType", identifier.identifierType)
                        .put("normalizedValue", identifier.normalizedValue)
                        .put("rawValue", identifier.rawValue)
                        .put("format", identifier.format)
                        .put("isVerified", identifier.isVerified)
                        .put("createdAt", identifier.createdAt)
                        .put("updatedAt", identifier.updatedAt)
                )
            }
        })

        root.put("observations", JSONArray().apply {
            recognitionDao.getAllObservations().forEach { observation ->
                put(
                    JSONObject()
                        .put("id", observation.id)
                        .put("productId", observation.productId)
                        .put("candidateProductId", observation.candidateProductId)
                        .put("candidateCategoryId", observation.candidateCategoryId)
                        .put("source", observation.source)
                        .put("sourceImageUris", observation.sourceImageUris)
                        .put("ocrText", observation.ocrText)
                        .put("barcodeValues", observation.barcodeValues)
                        .put("visualLabels", observation.visualLabels)
                        .put("confidenceScore", observation.confidenceScore)
                        .put("confidenceLevel", observation.confidenceLevel)
                        .put("verificationStatus", observation.verificationStatus)
                        .put("corrected", observation.corrected)
                        .put("explanation", observation.explanation)
                        .put("createdAt", observation.createdAt)
                )
            }
        })

        return root.toString(2)
    }
}
