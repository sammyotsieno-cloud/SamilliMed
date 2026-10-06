package core.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_evidence",
    indices = [Index(value = ["source"]), Index(value = ["jurisdiction"]), Index(value = ["version"])]
)
data class KnowledgeEvidence(
    @PrimaryKey val id: String,
    val source: String,
    val title: String,
    val url: String? = null,
    val publicationDate: String? = null,
    val effectiveDate: String? = null,
    val jurisdiction: String = "GLOBAL",
    val version: String = "2026.10",
    val evidenceLevel: String? = null,
    val retrievedAt: Long
)
