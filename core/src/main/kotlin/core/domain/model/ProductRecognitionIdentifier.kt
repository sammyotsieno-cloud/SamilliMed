package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_recognition_identifiers",
    foreignKeys = [
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["identifier_type", "normalized_value"], unique = true)
    ]
)
data class ProductRecognitionIdentifier(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "identifier_type") val identifierType: String,
    @ColumnInfo(name = "normalized_value") val normalizedValue: String,
    @ColumnInfo(name = "raw_value") val rawValue: String,
    @ColumnInfo(name = "format") val format: String? = null,
    @ColumnInfo(name = "is_verified") val isVerified: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {
    companion object { const val TYPE_BARCODE = "BARCODE" }
}
