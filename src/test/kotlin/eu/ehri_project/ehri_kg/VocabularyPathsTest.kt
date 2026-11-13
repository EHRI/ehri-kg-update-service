package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.processors.vocabularyPath
import eu.ehri_project.ehri_kg.shexml.ShExMLMappingLauncherProxy
import eu.ehri_project.ehri_kg.support.loadResource
import eu.ehri_project.ehri_kg.support.processEvents
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.ResourceFactory.createProperty
import org.apache.jena.rdf.model.ResourceFactory.createResource
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VocabularyPathsTest {

    @TempDir
    lateinit var dir: File

    private val paths = mapOf(
        "ehri_pers-000001" to "ehri-pers/000001",
        "ehri_cb-000002" to "ehri-cb/000002",
        "ehri_fm-000004" to "ehri-fm/000004",
        "ehri_terms-000003" to "ehri-terms/000003",
        "ehri_some_terms-1-a" to "ehri-some-terms/1-a",
        "be" to "be/",
    )

    @Test
    fun `agent and concept paths follow their Portal ids, in the service and the mappings`() {
        // The same rule as conf/functions/EHRIFunctions.scala gives, as used by the mappings
        val rules = """
            PREFIX ex: <http://example.org/>
            FUNCTIONS helpers <scala: conf/functions/EHRIFunctions.scala>
            SOURCE ids <stdin>
            ITERATOR id_iterator <jsonpath: $.ids[*]> {
                FIELD id <id>
            }
            EXPRESSION item <ids.id_iterator>
            ex:Item ex:[item.id] {
                ex:path [helpers.vocabularyPath(item.id)] ;
            }
        """.trimIndent()
        val json = paths.keys.joinToString(",", """{"ids": [""", "]}") { """{"id": "$it"}""" }
        val mapped = ShExMLMappingLauncherProxy().convert(rules, json).defaultModel
            .listStatements(null, createProperty("http://example.org/path"), null as RDFNode?).toList()
            .associate { it.subject.localName to it.`object`.asLiteral().string }

        assertEquals(paths, paths.keys.associateWith(::vocabularyPath))
        assertEquals(paths, mapped)
    }

    @Test
    fun `a family's triples are replaced on update and removed on delete`() {
        val id = "ehri_fm-000002"
        val family = createResource("http://lod.ehri-project-test.eu/vocabularies/ehri-fm/000002")
        val original = loadResource("example/graphql/$id.json")
        val renamed = original.replace("\"Example family\"", "\"Renamed family\"")
        assertTrue(renamed != original)

        fun triples(name: String, vararg events: Pair<String, String>) =
            processEvents(File(dir, name).apply { mkdirs() }, id, "HistoricalAgent", *events)
                .dataset.defaultModel.listStatements(family, null, null as RDFNode?).toList()

        val names = triples("update", "create-event" to original, "update-event" to renamed)
            .filter { it.predicate.localName == "name" }.map { it.`object`.asLiteral().string }
        assertEquals(listOf("Renamed family"), names)

        val deleted = """{"data": {"historicalAgents": {"items": [null]}}}"""
        assertEquals(emptyList(), triples("delete", "create-event" to original, "delete-event" to deleted).map { it.toString() })
    }
}
