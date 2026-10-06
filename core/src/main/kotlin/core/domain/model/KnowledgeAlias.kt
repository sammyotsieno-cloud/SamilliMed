package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_aliases",
    indices = [
        Index(value = ["entity_id"]),
        Index(value = ["normalized_alias"], unique = true)
    ]
)
data class KnowledgeAlias(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val alias: String,
    @ColumnInfo(name = "normalized_alias") val normalizedAlias: String,
    @ColumnInfo(name = "alias_type") val aliasType: String = "SYNONYM",
    val source: String = "GLOBAL"
)
