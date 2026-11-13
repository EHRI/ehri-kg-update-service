package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.support.FakeGraphQLClient
import eu.ehri_project.ehri_kg.support.InMemorySparqlStore
import eu.ehri_project.ehri_kg.support.loadResource
import eu.ehri_project.ehri_kg.support.testConfig
import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatesProcessorTest {

    @TempDir
    lateinit var dir: File

    private lateinit var store: InMemorySparqlStore
    private lateinit var graphQL: FakeGraphQLClient
    private lateinit var factory: UpdatesProcessorFactory

    private val gbUri = "http://lod.ehri-project-test.eu/countries/gb"
    private val researchSummary = "http://lod.ehri-project-test.eu/ontology#researchSummary"

    private fun event(eventType: String, id: String = "gb", type: String = "Country") =
        EHRIEvent("evt-1", eventType, "2026-01-01T00:00:00Z", id, type)

    @BeforeTest
    fun setUp() {
        store = InMemorySparqlStore()
        graphQL = FakeGraphQLClient { loadResource("countries/ukJsonGraphQL.json") }
        factory = UpdatesProcessorFactory(testConfig(dir), graphQL, sparqlStore = store)
    }

    private fun countries() = factory.createUpdateProcessor(EHRITypes.COUNTRY)

    @Test
    fun `fetchGraphQLData substitutes the entity id and escapes newlines`() {
        val response = countries().fetchGraphQLData(event("update-event"))

        assertEquals(loadResource("countries/ukJsonGraphQL.json"), response)
        val (_, query) = graphQL.requests.single()
        assertTrue(query.contains("""countries(ids: [\"gb\"])"""))
        assertFalse(query.contains("\n"))
    }

    @Test
    fun `create inserts the dataset into the store`() {
        val data = RDFDataMgr.loadDataset("src/test/resources/countries/uk.ttl")

        val queries = countries().create(data)

        assertEquals(1, queries.size)
        assertTrue(queries.single().startsWith("INSERT DATA"))
        assertTrue(store.dataset.defaultModel.isIsomorphicWith(data.defaultModel))
    }

    @Test
    fun `getDataStatus returns only the triples of the entity`() {
        val processor = countries()
        processor.create(RDFDataMgr.loadDataset("src/test/resources/countries/uk.ttl"))
        processor.create(RDFDataMgr.loadDataset("src/test/resources/countries/be.ttl"))

        val status = processor.getDataStatus(event("update-event"))

        assertFalse(status.isEmpty)
        assertTrue(status.listSubjects().toList().all { it.uri == gbUri })
    }

    @Test
    fun `delete event removes the entity triples`() {
        val processor = countries()
        processor.create(RDFDataMgr.loadDataset("src/test/resources/countries/uk.ttl"))

        val queries = processor.update(event("delete-event"), DatasetFactory.create())

        assertEquals(1, queries.size)
        assertTrue(queries.single().contains("DELETE"))
        val preserved = setOf(
            "https://www.ica.org/standards/RiC/ontology#containsTransitive",
            "https://www.ica.org/standards/RiC/ontology#isOrWasLocationOfAgent"
        )
        val remaining = processor.getDataStatus(event("delete-event")).listStatements().toList()
        assertTrue(remaining.all { it.predicate.uri in preserved }, "Unexpected triples left: $remaining")
    }

    @Test
    fun `update event replaces the entity with the mapped GraphQL data`() {
        val processor = countries()
        processor.create(RDFDataMgr.loadDataset("src/test/resources/countries/uk.ttl"))
        val updated = processor.transformToRDF(processor.fetchGraphQLData(event("update-event")))

        val queries = processor.update(event("update-event"), updated)

        assertEquals(2, queries.size)
        assertTrue(queries[0].contains("DELETE"))
        assertTrue(queries[1].startsWith("INSERT DATA"))
        val summaries = processor.getDataStatus(event("update-event"))
            .listStatements(null, store.dataset.defaultModel.createProperty(researchSummary), null as String?)
            .toList()
        assertTrue(summaries.isNotEmpty())
        assertTrue(summaries.all { it.literal.string.startsWith("[Test update]") })
    }

    @Test
    fun `unsupported event types are rejected without touching the store`() {
        assertFailsWith<IllegalStateException> {
            countries().update(event("merge-event"), DatasetFactory.create())
        }
        assertTrue(store.updates.isEmpty())
    }

    @Test
    fun `compareGraphs reports added, removed and modified properties`() {
        val before = ModelFactory.createDefaultModel()
        val after = ModelFactory.createDefaultModel()
        val s = before.createResource(gbUri)
        val kept = before.createProperty("http://example.com/kept")
        val changed = before.createProperty("http://example.com/changed")
        val removed = before.createProperty("http://example.com/removed")
        val added = before.createProperty("http://example.com/added")
        before.add(s, kept, "same").add(s, changed, "old").add(s, removed, "gone")
        after.add(s, kept, "same").add(s, changed, "new").add(s, added, "fresh")

        val diff = countries().compareGraphs(before, after)

        assertEquals(3, diff.size)
        assertTrue(diff.any { it.startsWith("Modified:") && "old" in it && "new" in it })
        assertTrue(diff.any { it.startsWith("Removed:") && "gone" in it })
        assertTrue(diff.any { it.startsWith("Added:") && "fresh" in it })
    }

    @Test
    fun `compareGraphs reports nothing for identical graphs`() {
        val model = RDFDataMgr.loadModel("src/test/resources/countries/uk.ttl")

        assertEquals(emptyList(), countries().compareGraphs(model, model))
    }

    @Test
    fun `vocabulary and historical agent ids are rewritten to their URI form`() {
        val vocabularies = factory.createUpdateProcessor(EHRITypes.VOCABULARY)
        val persons = factory.createUpdateProcessor(EHRITypes.PERSON)
        val corporateBodies = factory.createUpdateProcessor(EHRITypes.CORPORATE_BODY)

        assertEquals("""x ehri-terms\/1062 x""",
            vocabularies.replaceEntityId(event("update-event", "ehri_terms-1062", "CvocConcept"), "x <\$entityId> x"))
        assertEquals("""ehri-pers\/001774""",
            persons.replaceEntityId(event("update-event", "ehri_pers-001774", "HistoricalAgent"), "<\$entityId>"))
        assertEquals("""ehri-cb\/004284""",
            corporateBodies.replaceEntityId(event("update-event", "ehri_cb-004284", "HistoricalAgent"), "<\$entityId>"))
    }

    @Test
    fun `other entity ids are used verbatim`() {
        assertEquals("gb", countries().replaceEntityId(event("update-event"), "<\$entityId>"))
    }
}
