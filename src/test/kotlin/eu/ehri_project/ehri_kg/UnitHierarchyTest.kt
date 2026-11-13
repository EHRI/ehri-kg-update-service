package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.support.FakeGraphQLClient
import eu.ehri_project.ehri_kg.support.InMemorySparqlStore
import eu.ehri_project.ehri_kg.support.loadResource
import eu.ehri_project.ehri_kg.support.testProcessor
import org.apache.jena.rdf.model.ResourceFactory.createProperty
import org.apache.jena.rdf.model.ResourceFactory.createResource
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Each unit owns the links to its parent; updating either the parent or the child keeps them. */
class UnitHierarchyTest {

    @TempDir
    lateinit var dir: File

    val parentId = "be-000001-kd_00001"
    val childId = "be-000001-kd_00001-1"
    val parent = createResource("http://lod.ehri-project-test.eu/units/$parentId")
    val child = createResource("http://lod.ehri-project-test.eu/units/$childId")
    val includesOrIncluded = createProperty("https://www.ica.org/standards/RiC/ontology#includesOrIncluded")
    val isOrWasIncludedIn = createProperty("https://www.ica.org/standards/RiC/ontology#isOrWasIncludedIn")

    @Test
    fun `updating a parent or child unit keeps the links between them`() {
        val store = InMemorySparqlStore()
        val processor = testProcessor(dir, FakeGraphQLClient { loadResource("example/graphql/${it.id}.json") }, store)
        val kg = store.dataset.defaultModel

        listOf("create-event" to parentId, "create-event" to childId, "update-event" to parentId, "update-event" to childId)
            .forEachIndexed { i, (eventType, id) ->
                val report = processor.processEvent(EHRIEvent("e$i", eventType, "2026-01-01T00:00:00Z", id, "DocumentaryUnit"))
                assertEquals("", report.errors)
                if (i > 0) {
                    assertTrue(kg.contains(parent, includesOrIncluded, child), "after $eventType $id")
                    assertTrue(kg.contains(child, isOrWasIncludedIn, parent), "after $eventType $id")
                }
            }
    }
}
