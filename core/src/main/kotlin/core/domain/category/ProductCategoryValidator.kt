package core.domain.category

import core.domain.persistence.ProductCategoryDao

class ProductCategoryValidator(
    private val dao: ProductCategoryDao
) {
    suspend fun validateParentChange(
        categoryId: String,
        newParentId: String?,
        newName: String
    ) {
        val name = newName.trim()
        require(name.isNotBlank()) { "Category name is required." }

        if (newParentId == categoryId) {
            throw IllegalArgumentException("A category cannot be its own parent.")
        }

        val parent = newParentId?.let { dao.getById(it) }
        if (newParentId != null && parent == null) {
            throw IllegalArgumentException("Selected parent category no longer exists.")
        }
        if (parent != null && !parent.isActive) {
            throw IllegalArgumentException("An active category cannot be placed under an archived parent.")
        }

        val duplicateCount =
            if (newParentId == null) {
                dao.countRootName(name, categoryId)
            } else {
                dao.countChildName(newParentId, name, categoryId)
            }
        require(duplicateCount == 0) {
            "A category with this name already exists at this level."
        }

        var cursor = newParentId
        val visited = mutableSetOf<String>()
        while (cursor != null) {
            require(visited.add(cursor)) {
                "The category hierarchy already contains a cycle."
            }
            require(cursor != categoryId) {
                "This move would create a circular hierarchy."
            }
            cursor = dao.getById(cursor)?.parentCategoryId
        }
    }
}
