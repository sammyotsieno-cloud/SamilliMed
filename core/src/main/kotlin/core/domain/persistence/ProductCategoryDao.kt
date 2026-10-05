package core.domain.persistence

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import core.domain.model.ProductCategory

@Dao
interface ProductCategoryDao {
    @Query("SELECT * FROM product_categories ORDER BY parent_category_id IS NOT NULL, sort_order ASC, name COLLATE NOCASE ASC")
    suspend fun getAll(): List<ProductCategory>

    @Query("SELECT * FROM product_categories WHERE is_active = 1 ORDER BY parent_category_id IS NOT NULL, sort_order ASC, name COLLATE NOCASE ASC")
    suspend fun getActive(): List<ProductCategory>

    @Query("SELECT * FROM product_categories WHERE parent_category_id IS NULL ORDER BY sort_order ASC, name COLLATE NOCASE ASC")
    suspend fun getRoots(): List<ProductCategory>

    @Query("SELECT * FROM product_categories WHERE parent_category_id = :parentId ORDER BY sort_order ASC, name COLLATE NOCASE ASC")
    suspend fun getChildren(parentId: String): List<ProductCategory>

    @Query("SELECT * FROM product_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProductCategory?

    @Query("""
        SELECT COUNT(*) FROM product_categories
        WHERE parent_category_id = :parentId
          AND lower(name) = lower(:name)
          AND id != :excludeId
    """)
    suspend fun countChildName(parentId: String, name: String, excludeId: String): Int

    @Query("""
        SELECT COUNT(*) FROM product_categories
        WHERE parent_category_id IS NULL
          AND lower(name) = lower(:name)
          AND id != :excludeId
    """)
    suspend fun countRootName(name: String, excludeId: String): Int

    @Query("SELECT COUNT(*) FROM product_categories WHERE parent_category_id = :parentId")
    suspend fun countChildren(parentId: String): Int

    @Query("SELECT COUNT(*) FROM product_categories WHERE parent_category_id = :parentId AND is_active = 1")
    suspend fun countActiveChildren(parentId: String): Int

    @Query("SELECT COUNT(*) FROM product_masters WHERE category_id = :categoryId")
    suspend fun countProducts(categoryId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: ProductCategory): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<ProductCategory>)

    @Update
    suspend fun update(category: ProductCategory)

    @Delete
    suspend fun delete(category: ProductCategory)
}
