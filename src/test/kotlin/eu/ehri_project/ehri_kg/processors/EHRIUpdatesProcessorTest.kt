package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.consumers.ResumeCheck
import eu.ehri_project.ehri_kg.database.DatabaseManager
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRIUpdateReport
import eu.ehri_project.ehri_kg.sparql.LoggingSparqlStore
import eu.ehri_project.ehri_kg.support.FakeGraphQLClient
import eu.ehri_project.ehri_kg.support.InMemorySparqlStore
import eu.ehri_project.ehri_kg.support.loadResource
import eu.ehri_project.ehri_kg.support.mapSseEvent
import eu.ehri_project.ehri_kg.support.parseTurtle
import eu.ehri_project.ehri_kg.support.testConfig
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.riot.RDFDataMgr
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EHRIUpdatesProcessorTest {

    @TempDir
    lateinit var dir: File

    private lateinit var store: InMemorySparqlStore
    private lateinit var graphQL: FakeGraphQLClient
    private lateinit var database: DatabaseManager
    private lateinit var processor: EHRIUpdatesProcessor

    @BeforeTest
    fun setUp() {
        val config = testConfig(dir)
        store = InMemorySparqlStore()
        graphQL = FakeGraphQLClient { event ->
            if (event.id == "fail") throw IOException("GraphQL endpoint unavailable")
            if (event.id == "illegal-state") error("connection pool closed")
            loadResource("countries/ukJsonGraphQL.json")
        }
        database = DatabaseManager(config)
        processor = EHRIUpdatesProcessor(
            config,
            database,
            UpdatesProcessorFactory(config, graphQL, sparqlStore = store)::createUpdateProcessor
        )
        RDFDataMgr.read(store.dataset, "src/test/resources/countries/uk.ttl")
    }

    private fun sseEvent(eventId: String, eventType: String, vararg idsAndTypes: Pair<String, String>): Dataset {
        val ids = idsAndTypes.joinToString(",") { "\"${it.first}\"" }
        val types = idsAndTypes.joinToString(",") { "\"${it.second}\"" }
        return mapSseEvent("""
            {"id":"$eventId","event":"$eventType","retry":-1,
             "data":{"datetime":"2026-01-01T00:00:00Z","ids":[$ids],"types":[$types]}}
        """.trimIndent())
    }

    private fun run(vararg datasets: Dataset): List<EHRIUpdateReport> =
        processor.process(Single.just(Flowable.fromArray(*datasets))).blockingGet().toList().blockingGet()

    private fun isEmptyReport(report: EHRIUpdateReport) = report.receivedEvent.eventId.isEmpty()

    @Test
    fun `an update event is downloaded, mapped, applied and reported`() {
        val report = run(sseEvent("evt-1", "update-event", "gb" to "Country")).single()

        assertEquals(EHRIEvent("evt-1", "update-event", "2026-01-01T00:00:00Z", "gb", "Country"), report.receivedEvent)
        assertEquals("", report.errors)
        assertFalse(report.dryRun)
        assertEquals(2, report.executedQueries.size)
        assertTrue(report.rdfDiff.any { it.startsWith("Modified:") && "researchSummary" in it })
        assertEquals(1, graphQL.requests.size)
    }

    @Test
    fun `event ids are kept intact`() {
        // A real Portal event id: a UUIDv7, so it starts with digits
        // NB: these are not Java- or XML-compatible identifiers.
        val report = run(sseEvent("01a0f817-c642-7fa7-a13b-4857724cbeeb", "update-event", "gb" to "Country")).single()

        assertEquals("01a0f817-c642-7fa7-a13b-4857724cbeeb", report.receivedEvent.eventId)
    }

    @Test
    fun `heartbeats without events yield no report`() {
        val heartbeat = parseTurtle("@prefix : <http://example.com/> . :x :y :z .")

        val reports = run(heartbeat, sseEvent("evt-1", "update-event", "gb" to "Country"), DatasetFactory.create())

        assertEquals(listOf("evt-1"), reports.map { it.receivedEvent.eventId })
    }

    @Test
    fun `each id and type pair of an event is processed separately`() {
        val reports = run(sseEvent("evt-1", "update-event", "gb" to "Country", "ann-1" to "Annotation"))

        assertEquals(2, reports.size)
        assertEquals(listOf("gb"), reports.filterNot(::isEmptyReport).map { it.receivedEvent.id })
    }

    @Test
    fun `unsupported entity types are ignored`() {
        val report = run(sseEvent("evt-1", "update-event", "ann-1" to "Annotation")).single()

        assertTrue(isEmptyReport(report))
        assertTrue(graphQL.requests.isEmpty())
        assertTrue(store.updates.isEmpty())
    }

    @Test
    fun `concepts and historical agents of sets not configured to be in the KG are ignored`() {
        val reports = run(sseEvent("evt-1", "update-event",
            "terezin_terms-keyword_jmp_874" to "CvocConcept", "terezin_victims-person_iti_995281" to "HistoricalAgent",
            "ehri_other-1" to "HistoricalAgent", "ehri_pers-000001" to "CvocConcept"))

        assertTrue(reports.all(::isEmptyReport))
        assertTrue(graphQL.requests.isEmpty())
        assertTrue(store.updates.isEmpty())
    }

    @Test
    fun `failures are captured in the report`() {
        val report = run(sseEvent("evt-1", "update-event", "fail" to "Country")).single()

        assertFalse(report.dryRun)

        assertEquals("fail", report.receivedEvent.id)
        assertTrue("GraphQL endpoint unavailable" in report.errors)
        assertTrue(report.executedQueries.isEmpty())
    }

    @Test
    fun `IllegalStateExceptions during processing are reported, not swallowed`() {
        val report = run(sseEvent("evt-1", "update-event", "illegal-state" to "Country")).single()

        assertEquals("illegal-state", report.receivedEvent.id)
        assertTrue("connection pool closed" in report.errors)
    }

    @Test
    fun `unknown event types produce an error report`() {
        val report = run(sseEvent("evt-1", "other-event", "gb" to "Country")).single()

        assertEquals("gb", report.receivedEvent.id)
        assertTrue("Event other-event not supported" in report.errors)
        assertTrue(store.updates.isEmpty())
    }

    @Test
    fun `a dry run logs the statements without a triple store`() {
        val config = testConfig(dir)
        val logged = mutableListOf<String>()
        processor = EHRIUpdatesProcessor(
            config,
            database,
            UpdatesProcessorFactory(config, graphQL, sparqlStore = LoggingSparqlStore(logged::add))::createUpdateProcessor,
            dryRun = true
        )

        val (report, failed) = run(
            sseEvent("evt-1", "update-event", "gb" to "Country"),
            sseEvent("evt-2", "update-event", "fail" to "Country"),
        )

        assertEquals("", report.errors)
        assertTrue(report.dryRun)
        assertTrue("GraphQL endpoint unavailable" in failed.errors)
        assertTrue(failed.dryRun)
        assertEquals(report.executedQueries.map(LoggingSparqlStore::formatStatement), logged)
        assertTrue(logged[0].contains("DELETE"))
        assertTrue(logged[1].startsWith("INSERT DATA"))
        assertTrue(report.rdfDiff.isEmpty())
    }

    @Test
    fun `events successfully processed in a previous run are skipped`() {
        val event = EHRIEvent("evt-1", "update-event", "2026-01-01T00:00:00Z", "gb", "Country")
        database.insertReport(EHRIUpdateReport(event, emptyList(), emptyList()))

        val report = run(sseEvent("evt-1", "update-event", "gb" to "Country")).single()

        assertTrue(isEmptyReport(report))
        assertTrue(graphQL.requests.isEmpty())
    }

    @Test
    fun `events that errored in a previous run are retried`() {
        val event = EHRIEvent("evt-1", "update-event", "2026-01-01T00:00:00Z", "gb", "Country")
        database.insertReport(EHRIUpdateReport(event, emptyList(), emptyList(), errors = "boom"))

        val report = run(sseEvent("evt-1", "update-event", "gb" to "Country")).single()

        assertEquals("", report.errors)
        assertEquals(1, graphQL.requests.size)
    }

    @Test
    fun `once an event is processed, later events are no longer skipped`() {
        // evt-2 was processed before, but follows an event that was not, so it must be replayed in order...
        val processedLater = EHRIEvent("evt-2", "update-event", "2026-01-01T00:00:00Z", "gb", "Country")
        database.insertReport(EHRIUpdateReport(processedLater, emptyList(), emptyList()))

        val reports = run(
            sseEvent("evt-1", "update-event", "gb" to "Country"),
            sseEvent("evt-2", "update-event", "gb" to "Country"),
        )

        assertEquals(listOf("evt-1", "evt-2"), reports.map { it.receivedEvent.eventId })
        assertEquals(2, graphQL.requests.size)
    }

    @Test
    fun `the event resumed from is checked, even when it is skipped`() {
        val event = EHRIEvent("evt-1", "update-event", "2026-01-01T00:00:00Z", "gb", "Country")
        database.insertReport(EHRIUpdateReport(event, emptyList(), emptyList()))
        val config = testConfig(dir)
        val check = ResumeCheck("evt-1")
        processor = EHRIUpdatesProcessor(config, database,
            UpdatesProcessorFactory(config, graphQL, sparqlStore = store)::createUpdateProcessor, resumeCheck = check)

        run(sseEvent("evt-1", "update-event", "gb" to "Country"), sseEvent("evt-2", "update-event", "gb" to "Country"))

        assertEquals(true, check.replayed)
    }
}
