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

object KnowledgeSeeder {
    const val VERSION = "2026.10"
    private const val MANIFEST_ID = "manifest.samillimed.knowledge"

    fun seed(context: Context, database: CoreDatabase) {
        val dao = database.knowledgeDao()
        if (dao.findNode(MANIFEST_ID) != null) return
        val now = System.currentTimeMillis()
        val nodes = mutableListOf<KnowledgeNode>()
        val relations = mutableListOf<KnowledgeRelation>()
        val evidence = mutableListOf<KnowledgeEvidence>()
        val aliases = mutableListOf<KnowledgeAlias>()

        context.assets.open("knowledge/knowledge_seed.tsv").use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                    val c = line.split("\t".toRegex(), limit = 9)
                    when (c[0]) {
                        "N" -> if (c.size >= 9) nodes += KnowledgeNode(
                            id=c[1], nodeType=c[2], parentId=c[3].ifBlank { null },
                            canonicalName=c[4], description=c[5].ifBlank { null },
                            attributesJson=c[6].ifBlank { "{}" }, scope=c[7], version=c[8],
                            createdAt=now, updatedAt=now
                        )
                        "R" -> if (c.size >= 9) relations += KnowledgeRelation(
                            id=c[1], subjectId=c[2], predicate=c[3], objectId=c[4],
                            qualifiersJson=c[5].ifBlank { "{}" }, evidenceId=c[6].ifBlank { null },
                            scope=c[7], version=c[8], createdAt=now
                        )
                        "E" -> if (c.size >= 8) evidence += KnowledgeEvidence(
                            id=c[1], source=c[2], title=c[3], url=c[4].ifBlank { null },
                            publicationDate=c[5].ifBlank { null }, jurisdiction=c[6],
                            version=c[7], retrievedAt=now
                        )
                        "A" -> if (c.size >= 8) {
                            val normalized = c[3].trim().lowercase(Locale.ROOT)
                            aliases += KnowledgeAlias(
                                id=c[1], entityId=c[2], alias=c[3],
                                normalizedAlias=normalized, aliasType=c[4], source=c[5]
                            )
                        }
                    }
                }
            }
        }
        nodes += KnowledgeNode(
            id=MANIFEST_ID, nodeType="manifest", canonicalName="SamilliMed Knowledge",
            description="Versioned master health-product knowledge graph.",
            attributesJson="""{"schemaVersion":"1.0","seedVersion":"$VERSION","scope":"GLOBAL"}""",
            version=VERSION, createdAt=now, updatedAt=now
        )
        database.runInTransaction {
            dao.insertEvidence(evidence)
            dao.insertNodes(nodes)
            dao.insertRelations(relations)
            dao.insertAliases(aliases)
        }
    }
}
