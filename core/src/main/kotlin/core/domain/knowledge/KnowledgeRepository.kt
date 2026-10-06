package core.domain.knowledge

import core.domain.model.KnowledgeNode
import core.domain.persistence.KnowledgeDao

data class KnowledgeResolution(
    val node: KnowledgeNode,
    val ancestors: List<KnowledgeNode>,
    val inheritedRelations: Map<String, List<String>>
)

class KnowledgeRepository(private val dao: KnowledgeDao) {
    fun find(id: String): KnowledgeNode? = dao.findNode(id)

    fun search(query: String, limit: Int = 50): List<KnowledgeNode> =
        dao.searchNodes(query.trim(), limit.coerceIn(1, 200))

    fun children(parentId: String): List<KnowledgeNode> = dao.children(parentId)

    fun resolve(id: String): KnowledgeResolution? {
        val node = dao.findNode(id) ?: return null
        val ancestors = mutableListOf<KnowledgeNode>()
        val seen = mutableSetOf<String>()
        var parent = node.parentId
        while (parent != null && seen.add(parent)) {
            val next = dao.findNode(parent) ?: break
            ancestors += next
            parent = next.parentId
        }
        val chain = listOf(node) + ancestors
        val relations = linkedMapOf<String, MutableList<String>>()
        chain.asReversed().forEach { n ->
            dao.outgoing(n.id).forEach { r ->
                relations.getOrPut(r.predicate) { mutableListOf() }.add(r.objectId)
            }
        }
        return KnowledgeResolution(
            node = node,
            ancestors = ancestors,
            inheritedRelations = relations.mapValues { it.value.distinct() }
        )
    }
}
