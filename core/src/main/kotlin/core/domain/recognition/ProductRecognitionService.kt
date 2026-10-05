package core.domain.recognition

import core.domain.model.ProductCategory
import core.domain.model.ProductMaster
import core.domain.persistence.ProductCategoryDao
import core.domain.persistence.ProductMasterDao
import core.domain.persistence.ProductRecognitionDao

data class ProductRecognitionCandidate(
    val product: ProductMaster,
    val category: ProductCategory?,
    val confidenceScore: Double,
    val confidenceLevel: String,
    val explanation: String
)

class ProductRecognitionService(
    private val productDao: ProductMasterDao,
    private val recognitionDao: ProductRecognitionDao,
    private val categoryDao: ProductCategoryDao
) {
    suspend fun recognize(
        ocrText: String,
        barcodes: List<String>
    ): List<ProductRecognitionCandidate> {
        val normalizedBarcodes = barcodes.map(::normalize).filter { it.isNotBlank() }.distinct()
        val products = productDao.getAllProducts().filter { it.isActive }
        if (products.isEmpty()) return emptyList()

        val candidates = products.mapNotNull { product ->
            val identifierMatches = normalizedBarcodes.mapNotNull {
                recognitionDao.findIdentifier("BARCODE", it)
            }.filter { it.productId == product.id && it.isVerified }

            if (identifierMatches.isNotEmpty()) {
                val category = product.categoryId?.let { categoryDao.getById(it) }
                return@mapNotNull ProductRecognitionCandidate(
                    product, category, 1.0, "HIGH",
                    "Matched a previously verified product barcode."
                )
            }

            val normalizedText = normalize(ocrText)
            val terms = listOfNotNull(product.brandName, product.genericName, product.manufacturer)
                .map(::normalize)
                .filter { it.length >= 3 }

            val matches = terms.count { normalizedText.contains(it) }
            if (matches == 0) return@mapNotNull null

            val score = when (matches) {
                1 -> 0.55
                2 -> 0.75
                else -> 0.85
            }
            val category = product.categoryId?.let { categoryDao.getById(it) }
            ProductRecognitionCandidate(
                product,
                category,
                score,
                if (score >= 0.75) "HIGH" else "MEDIUM",
                "OCR matched @@{matches} known product identity field(s)."
            )
        }

        return candidates
            .sortedWith(compareByDescending<ProductRecognitionCandidate> { it.confidenceScore }.thenBy { it.product.displayName })
            .take(5)
    }

    companion object {
        fun normalize(value: String): String =
            value.lowercase()
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
    }
}
