package core.domain.category

import android.content.Context
import core.domain.model.ProductCategory
import core.domain.persistence.ProductCategoryDao
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

object ProductCategorySeeder {
    suspend fun ensureDefaultTaxonomy(dao: ProductCategoryDao, context: Context? = null): Int {
        val existing = dao.getAll()
        val hasCategoryOne = existing.any { it.id == "1" }
        if (hasCategoryOne && existing.size > 20) return 0

        val now = System.currentTimeMillis()

        if (context != null) {
            try {
                val items = mutableListOf<ProductCategory>()
                context.assets.open("taxonomy_v2.json").use { input ->
                    val text = BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                    val jsonArray = JSONArray(text)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.getString("id")
                        val parentId = if (obj.isNull("parentId")) null else obj.getString("parentId")
                        val name = obj.getString("name")
                        val description = if (obj.isNull("description")) null else obj.getString("description")
                        items.add(
                            ProductCategory(
                                id = id,
                                parentCategoryId = parentId,
                                name = name,
                                description = description,
                                isActive = true,
                                sortOrder = i,
                                isSystemDefault = true,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }
                }
                if (items.isNotEmpty()) {
                    dao.insertAll(items)
                    return items.size
                }
            } catch (_: Exception) {
                // Fallback to in-code default roots
            }
        }

        if (existing.isNotEmpty()) return 0

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
