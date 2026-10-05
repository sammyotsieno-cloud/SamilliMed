package core.domain.category

import core.domain.model.ProductCategory
import core.domain.persistence.ProductCategoryDao
import java.util.UUID

class ProductCategoryRepository(
    private val dao: ProductCategoryDao
) {
    private val validator = ProductCategoryValidator(dao)

    suspend fun ensureDefaultTaxonomy(): Int = ProductCategorySeeder.ensureDefaultTaxonomy(dao)
    suspend fun getAll(): List<ProductCategory> = dao.getAll()
    suspend fun getActive(): List<ProductCategory> = dao.getActive()
    suspend fun getById(id: String): ProductCategory? = dao.getById(id)

    suspend fun getAncestors(categoryId: String): List<ProductCategory> {
        val result = mutableListOf<ProductCategory>()
        val visited = mutableSetOf<String>()
        var current = dao.getById(categoryId)?.parentCategoryId
        while (current != null && visited.add(current)) {
            val category = dao.getById(current) ?: break
            result += category
            current = category.parentCategoryId
        }
        return result.asReversed()
    }

    suspend fun getPath(categoryId: String): String {
        val category = dao.getById(categoryId) ?: return ""
        return (getAncestors(categoryId) + category).joinToString(" → ") { it.name }
    }

    suspend fun create(name: String, parentId: String? = null, description: String? = null): ProductCategory {
        val id = UUID.randomUUID().toString()
        validator.validateParentChange(id, parentId, name)
        val now = System.currentTimeMillis()
        val siblingCount = parentId?.let { dao.countChildren(it) } ?: dao.getRoots().size
        val category = ProductCategory(
            id = id,
            parentCategoryId = parentId,
            name = name.trim(),
            description = description?.trim()?.ifBlank { null },
            isActive = true,
            sortOrder = siblingCount,
            isSystemDefault = false,
            createdAt = now,
            updatedAt = now
        )
        dao.insert(category)
        return category
    }

    suspend fun update(category: ProductCategory, name: String, parentId: String?, description: String?): ProductCategory {
        validator.validateParentChange(category.id, parentId, name)
        val updated = category.copy(
            parentCategoryId = parentId,
            name = name.trim(),
            description = description?.trim()?.ifBlank { null },
            updatedAt = System.currentTimeMillis()
        )
        dao.update(updated)
        return updated
    }

    suspend fun archive(categoryId: String) {
        val category = dao.getById(categoryId) ?: error("Category not found.")
        require(category.isActive) { "Category is already archived." }
        require(dao.countActiveChildren(categoryId) == 0) {
            "Move or archive active child categories before archiving this category."
        }
        dao.update(category.copy(isActive = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun restore(categoryId: String) {
        val category = dao.getById(categoryId) ?: error("Category not found.")
        val parent = category.parentCategoryId?.let { dao.getById(it) }
        require(parent == null || parent.isActive) {
            "Restore the parent category before restoring this category."
        }
        dao.update(category.copy(isActive = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteIfUnused(categoryId: String) {
        val category = dao.getById(categoryId) ?: return
        require(dao.countChildren(categoryId) == 0) {
            "Category has child categories. Move or delete them first."
        }
        require(dao.countProducts(categoryId) == 0) {
            "Category is assigned to products. Reassign those products before deleting it."
        }
        dao.delete(category)
    }
}
