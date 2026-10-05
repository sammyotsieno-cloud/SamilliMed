package core.domain.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import core.domain.model.ProductRecognitionIdentifier
import core.domain.model.ProductRecognitionObservation

@Dao
interface ProductRecognitionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIdentifier(identifier: ProductRecognitionIdentifier): Long

    @Query("SELECT * FROM product_recognition_identifiers WHERE identifier_type = :type AND normalized_value = :normalizedValue LIMIT 1")
    suspend fun findIdentifier(type: String, normalizedValue: String): ProductRecognitionIdentifier?

    @Query("SELECT * FROM product_recognition_identifiers WHERE product_id = :productId ORDER BY created_at ASC")
    suspend fun getIdentifiersForProduct(productId: String): List<ProductRecognitionIdentifier>

    @Query("SELECT * FROM product_recognition_identifiers ORDER BY created_at ASC")
    suspend fun getAllIdentifiers(): List<ProductRecognitionIdentifier>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObservation(observation: ProductRecognitionObservation)

    @Update
    suspend fun updateObservation(observation: ProductRecognitionObservation)

    @Query("SELECT * FROM product_recognition_observations WHERE product_id = :productId ORDER BY created_at DESC")
    suspend fun getObservationsForProduct(productId: String): List<ProductRecognitionObservation>

    @Query("SELECT * FROM product_recognition_observations ORDER BY created_at DESC")
    suspend fun getAllObservations(): List<ProductRecognitionObservation>
}
