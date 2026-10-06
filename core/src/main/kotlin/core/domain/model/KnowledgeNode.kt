package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_nodes",
    indices = [
        Index(value = ["node_type"]),
        Index(value = ["parent_id"]),
        Index(value = ["canonical_name"]),
        Index(value = ["scope", "version"])
    ]
)
data class KnowledgeNode(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "node_type") val nodeType: String,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "canonical_name") val canonicalName: String,
    val description: String? = null,
    @ColumnInfo(name = "attributes_json") val attributesJson: String = "{}",
    val scope: String = "GLOBAL",
    val version: String = "2026.10",
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {
    init {
        require(id.isNotBlank())
        require(nodeType.isNotBlank())
        require(canonicalName.isNotBlank())
        require(scope.isNotBlank())
        require(version.isNotBlank())
    }
}
