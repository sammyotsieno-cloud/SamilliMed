package core.domain.knowledge

import android.content.Context
import core.domain.model.KnowledgeAlias
import core.domain.model.KnowledgeEvidence
import core.domain.model.KnowledgeNode
import core.domain.model.KnowledgeRelation
import core.domain.persistence.CoreDatabase
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

/**
 * Loads the single authoritative knowledge dataset from knowledge/knowledge_seed.tsv.
 * The file is read on each application startup; no manifest or seed-version gate is used.
 */
object KnowledgeSeeder {
    private const val SEED_ASSET = "knowledge/knowledge_seed.tsv"

    fun seed(context: Context, database: CoreDatabase) {
        synchronized(database) {
            val dao = database.knowledgeDao()
            val now = System.currentTimeMillis()
            val nodes = mutableListOf<KnowledgeNode>()
            val relations = mutableListOf<KnowledgeRelation>()
            val evidence = mutableListOf<KnowledgeEvidence>()
            val aliases = mutableListOf<KnowledgeAlias>()

            context.assets.open(SEED_ASSET).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                    lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                        val c = line.split("\\t".toRegex(), limit = 9)
                        when (c[0]) {
                            "N" -> if (c.size >= 9) nodes += KnowledgeNode(
                                id = c[1], nodeType = c[2], parentId = c[3].ifBlank { null },
                                canonicalName = c[4], description = c[5].ifBlank { null },
                                attributesJson = c[6].ifBlank { "{}" }, scope = c[7].ifBlank { "GLOBAL" },
                                version = "unversioned", createdAt = now, updatedAt = now
                            )
                            "R" -> if (c.size >= 9) relations += KnowledgeRelation(
                                id = c[1], subjectId = c[2], predicate = c[3], objectId = c[4],
                                qualifiersJson = c[5].ifBlank { "{}" }, evidenceId = c[6].ifBlank { null },
                                scope = c[7].ifBlank { "GLOBAL" }, version = "unversioned",
                                createdAt = now
                            )
                            "E" -> if (c.size >= 8) evidence += KnowledgeEvidence(
                                id = c[1], source = c[2], title = c[3], url = c[4].ifBlank { null },
                                publicationDate = c[5].ifBlank { null }, jurisdiction = c[6].ifBlank { "GLOBAL" },
                                version = "unversioned", retrievedAt = now
                            )
                            "A" -> if (c.size >= 8) aliases += KnowledgeAlias(
                                id = c[1], entityId = c[2], alias = c[3],
                                normalizedAlias = c[3].trim().lowercase(Locale.ROOT),
                                aliasType = c[4], source = c[5]
                            )
                        }
                    }
                }
            }

            val nodeIds = nodes.map { it.id }
            require(nodeIds.size == nodeIds.toSet().size) { "Duplicate knowledge node IDs in knowledge_seed.tsv" }
            val nodeIdSet = nodeIds.toSet()
            nodes.filter { it.parentId != null }.forEach { node ->
                require(node.parentId in nodeIdSet) { "Missing parent ${node.parentId} for ${node.id}" }
            }
            relations.forEach { relation ->
                require(relation.subjectId in nodeIdSet) { "Missing relation subject ${relation.subjectId}" }
                require(relation.objectId in nodeIdSet) { "Missing relation object ${relation.objectId}" }
            }

            database.runInTransaction {
                dao.clearAliases()
                dao.clearRelations()
                dao.clearEvidence()
                dao.clearNodes()
                dao.insertEvidence(evidence)
                dao.insertNodes(nodes)
                dao.insertRelations(relations)
                dao.insertAliases(aliases)
            }
        }
    }
}
