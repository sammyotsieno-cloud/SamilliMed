package core.domain.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import core.domain.model.KnowledgeAlias
import core.domain.model.KnowledgeEvidence
import core.domain.model.KnowledgeNode
import core.domain.model.KnowledgeRelation

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertNodes(rows: List<KnowledgeNode>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertRelations(rows: List<KnowledgeRelation>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEvidence(rows: List<KnowledgeEvidence>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAliases(rows: List<KnowledgeAlias>)

    @Query("SELECT * FROM knowledge_nodes WHERE id = :id LIMIT 1")
    fun findNode(id: String): KnowledgeNode?

    @Query("SELECT * FROM knowledge_nodes WHERE node_type = :type AND is_active = 1 ORDER BY canonical_name")
    fun findByType(type: String): List<KnowledgeNode>

    @Query("SELECT * FROM knowledge_nodes WHERE parent_id = :parentId AND is_active = 1 ORDER BY canonical_name")
    fun children(parentId: String): List<KnowledgeNode>

    @Query("SELECT * FROM knowledge_nodes WHERE canonical_name LIKE '%' || :query || '%' AND is_active = 1 ORDER BY canonical_name LIMIT :limit")
    fun searchNodes(query: String, limit: Int = 50): List<KnowledgeNode>

    @Query("SELECT * FROM knowledge_relations WHERE subject_id = :subjectId AND is_active = 1")
    fun outgoing(subjectId: String): List<KnowledgeRelation>

    @Query("SELECT * FROM knowledge_relations WHERE object_id = :objectId AND is_active = 1")
    fun incoming(objectId: String): List<KnowledgeRelation>

    @Query("SELECT * FROM knowledge_aliases WHERE normalized_alias = :normalized LIMIT 1")
    fun findAlias(normalized: String): KnowledgeAlias?

    @Query("SELECT * FROM knowledge_evidence WHERE id = :id LIMIT 1")
    fun findEvidence(id: String): KnowledgeEvidence?

    @Query("SELECT COUNT(*) FROM knowledge_nodes")
    fun nodeCount(): Int

    @Query("SELECT COUNT(*) FROM knowledge_relations")
    fun relationCount(): Int

    @Query("SELECT COUNT(*) FROM knowledge_evidence")
    fun evidenceCount(): Int

    @Query("SELECT COUNT(*) FROM knowledge_aliases")
    fun aliasCount(): Int
}
