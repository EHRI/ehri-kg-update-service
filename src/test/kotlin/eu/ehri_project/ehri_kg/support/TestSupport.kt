package eu.ehri_project.ehri_kg.support

import eu.ehri_project.ehri_kg.database.DatabaseManager
import eu.ehri_project.ehri_kg.graphql.GraphQLClient
import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.processors.EHRIUpdatesProcessor
import eu.ehri_project.ehri_kg.processors.UpdatesProcessorFactory
import eu.ehri_project.ehri_kg.shexml.ShExMLMappingLauncherProxy
import eu.ehri_project.ehri_kg.sparql.SparqlStore
import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.update.UpdateAction
import java.io.File
import java.util.Properties
import kotlin.test.assertEquals

class InMemorySparqlStore(val dataset: Dataset = DatasetFactory.create()) : SparqlStore {
    val updates = mutableListOf<String>()

    override fun construct(query: String): Model =
        QueryExecutionFactory.create(query, dataset).use { it.execConstruct() }

    override fun update(query: String) {
        updates += query
        UpdateAction.parseExecute(query, dataset)
    }
}

class FakeGraphQLClient(private val response: (EHRIEvent) -> String) : GraphQLClient {
    val requests = mutableListOf<Pair<EHRIEvent, String>>()

    override suspend fun download(event: EHRIEvent, query: String): String {
        requests += event to query
        return response(event)
    }
}

/** An events processor writing to [store], with the GraphQL API mocked by [graphQL]. */
fun testProcessor(databaseDir: File, graphQL: GraphQLClient, store: SparqlStore): EHRIUpdatesProcessor {
    val config = testConfig(databaseDir)
    return EHRIUpdatesProcessor(
        config,
        DatabaseManager(config),
        UpdatesProcessorFactory(config, graphQL, sparqlStore = store)::createUpdateProcessor
    )
}

/**
 * Processes events for one entity in an in-memory store, each with its GraphQL response, asserting that none fails.
 */
fun processEvents(databaseDir: File, id: String, type: String, vararg events: Pair<String, String>): InMemorySparqlStore {
    val store = InMemorySparqlStore()
    val responses = events.map { it.second }.iterator()
    val processor = testProcessor(databaseDir, FakeGraphQLClient { responses.next() }, store)
    events.forEachIndexed { i, (eventType, _) ->
        val report = processor.processEvent(EHRIEvent("e$i", eventType, "2026-01-01T00:00:00Z", id, type))
        assertEquals("", report.errors)
    }
    return store
}

fun testConfig(databaseDir: File): Config {
    val props = Properties()
    File("conf/config.properties").inputStream().use { props.load(it) }
    props.setProperty("databasePath", File(databaseDir, "eventsHistory.db").path)
    return Config(props)
}

/** Maps an SSE event, as the JSON document shexml-streaming builds from it, with the real SSE mapping rules. */
fun mapSseEvent(json: String): Dataset {
    val rules = File("conf/ehri_sse_mapping.shexml").readLines().joinToString("\n") {
        if (it.startsWith("STREAM")) "SOURCE ehri_sse <stdin>" else it
    }
    return ShExMLMappingLauncherProxy().convert(rules, json)
}

fun parseTurtle(turtle: String): Dataset =
    DatasetFactory.create().also { RDFParser.fromString(turtle).lang(Lang.TURTLE).parse(it) }

fun loadResource(path: String): String = File("src/test/resources/$path").readText()
