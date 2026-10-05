package core.domain.category

import core.domain.model.ProductCategory
import core.domain.persistence.ProductCategoryDao

object ProductCategorySeeder {
    suspend fun ensureDefaultTaxonomy(dao: ProductCategoryDao): Int {
        if (dao.getAll().isNotEmpty()) return 0

        val now = System.currentTimeMillis()
        val categories = DefaultProductTaxonomyV1.roots.mapIndexed { index, node ->
            ProductCategory(
                id = node.id,
                parentCategoryId = null,
                name = node.name,
                description = node.description,
                isActive = true,
                sortOrder = index,
                isSystemDefault = true,
                createdAt = now,
                updatedAt = now
            )
        }
        dao.insertAll(categories)
        return categories.size
    }
}
