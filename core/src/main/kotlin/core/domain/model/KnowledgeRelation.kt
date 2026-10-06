package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_relations",
    indices = [
        Index(value = ["subject_id"]),
        Index(value = ["object_id"]),
        Index(value = ["predicate"]),
        Index(value = ["scope", "version"]),
        Index(value = ["subject_id", "predicate", "object_id"], unique = true)
    ]
)
data class KnowledgeRelation(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    val predicate: String,
    @ColumnInfo(name = "object_id") val objectId: String,
    @ColumnInfo(name = "qualifiers_json") val qualifiersJson: String = "{}",
    @ColumnInfo(name = "evidence_id") val evidenceId: String? = null,
    val scope: String = "GLOBAL",
    val version: String = "2026.10",
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
