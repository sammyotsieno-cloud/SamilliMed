package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_recognition_observations",
    foreignKeys = [
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["created_at"]),
        Index(value = ["candidate_product_id"])
    ]
)
data class ProductRecognitionObservation(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "product_id") val productId: String? = null,
    @ColumnInfo(name = "candidate_product_id") val candidateProductId: String? = null,
    @ColumnInfo(name = "candidate_category_id") val candidateCategoryId: String? = null,
    @ColumnInfo(name = "source") val source: String = SOURCE_SCANNER,
    @ColumnInfo(name = "source_image_uris") val sourceImageUris: String? = null,
    @ColumnInfo(name = "ocr_text") val ocrText: String? = null,
    @ColumnInfo(name = "barcode_values") val barcodeValues: String? = null,
    @ColumnInfo(name = "visual_labels") val visualLabels: String? = null,
    @ColumnInfo(name = "confidence_score") val confidenceScore: Double? = null,
    @ColumnInfo(name = "confidence_level") val confidenceLevel: String = CONFIDENCE_UNKNOWN,
    @ColumnInfo(name = "verification_status") val verificationStatus: String = STATUS_UNVERIFIED,
    @ColumnInfo(name = "corrected") val corrected: Boolean = false,
    @ColumnInfo(name = "explanation") val explanation: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
) {
    companion object {
        const val SOURCE_SCANNER = "SCANNER"
        const val STATUS_UNVERIFIED = "UNVERIFIED"
        const val STATUS_CONFIRMED = "CONFIRMED"
        const val STATUS_CORRECTED = "CORRECTED"
        const val STATUS_REJECTED = "REJECTED"
        const val CONFIDENCE_HIGH = "HIGH"
        const val CONFIDENCE_MEDIUM = "MEDIUM"
        const val CONFIDENCE_LOW = "LOW"
        const val CONFIDENCE_UNKNOWN = "UNKNOWN"
    }
}
