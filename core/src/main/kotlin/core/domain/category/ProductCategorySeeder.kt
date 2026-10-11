package core.domain.category

import android.content.Context
import core.domain.model.ProductCategory
import core.domain.persistence.ProductCategoryDao
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object ProductCategorySeeder {
    private const val SEED_ASSET = "knowledge/knowledge_seed.tsv"

    suspend fun ensureDefaultTaxonomy(dao: ProductCategoryDao, context: Context? = null): Int {
        requireNotNull(context) { "Context is required to load knowledge_seed.tsv" }
        val now = System.currentTimeMillis()
        val items = mutableListOf<ProductCategory>()

        context.assets.open(SEED_ASSET).use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                    val c = line.split("\\t".toRegex(), limit = 9)
                    if (c.size >= 9 && c[0] == "N" && c[2] == "product_category") {
                        val attrs = JSONObject(c[6])
                        val id = attrs.optString("productCategoryId").trim()
                        require(id.isNotBlank()) { "Missing productCategoryId on ${c[1]}" }
                        val parentValue = attrs.opt("parentProductCategoryId")
                        val parent = if (parentValue == null || parentValue == JSONObject.NULL) null else parentValue.toString().takeIf { it.isNotBlank() }
                        items += ProductCategory(
                            id = id,
                            parentCategoryId = parent,
                            name = c[4],
                            description = c[5].ifBlank { null },
                            isActive = true,
                            sortOrder = attrs.optInt("sortOrder", items.size),
                            isSystemDefault = true,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                }
            }
        }

        require(items.isNotEmpty()) { "No product_category records in knowledge_seed.tsv" }
        val ids = items.map { it.id }.toSet()
        require(ids.size == items.size) { "Duplicate product category IDs in knowledge_seed.tsv" }
        items.filter { it.parentCategoryId != null }.forEach { item ->
            require(item.parentCategoryId in ids) { "Missing category parent ${item.parentCategoryId} for ${item.id}" }
        }
        dao.upsertAll(items)
        return items.size
    }
}
