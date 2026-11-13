package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.support.FakeGraphQLClient
import eu.ehri_project.ehri_kg.support.InMemorySparqlStore
import eu.ehri_project.ehri_kg.support.testProcessor
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.jena.query.*
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.ResultSetMgr
import org.apache.jena.riot.resultset.ResultSetLang
import org.apache.jena.sparql.resultset.ResultSetCompare
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs a set of competency questions against a KG built from mock GraphQL responses,
 * comparing their results with what we expect.
 *
 * The KG is written to build/e2e/kg.ttl for inspection.
 */
class CompetencyQuestionsTest {

    companion object {
        val examplesDir = File("src/test/resources/example")

        /** The KG, with mock federated query endpoints as named graphs. */
        val store = InMemorySparqlStore().apply {
            dataset.addNamedModel("https://dbpedia.org/sparql/", RDFDataMgr.loadModel("$examplesDir/services/dbpedia.ttl"))
            dataset.addNamedModel("http://lod.xdams.org/sparql", RDFDataMgr.loadModel("$examplesDir/services/cdec.ttl"))
        }

        /** Processes each entity in graphql/events.csv as a create event, with the response in graphql/<id>.json. */
        val reports = run {
            val graphQL = FakeGraphQLClient { File(examplesDir, "graphql/${it.id}.json").readText() }
            val processor = testProcessor(createTempDirectory().toFile(), graphQL, store)
            readCsv("graphql/events.csv")
                .map { EHRIEvent("e2e", "create-event", "2026-01-01T00:00:00Z", it.getValue("id"), it.getValue("type")) }
                .map(processor::processEvent)
        }

        val processingErrors = reports
            .filter { it.errors.isNotEmpty() }
            .map { "${it.receivedEvent.id}: ${it.errors.lineSequence().first()}" }

        init {
            File("build/e2e").mkdirs()
            File("build/e2e/kg.ttl").outputStream().use { RDFDataMgr.write(it, store.dataset.defaultModel, Lang.TURTLE) }
        }

        fun readCsv(path: String): List<Map<String, String>> =
            CSVParser.parse(File(examplesDir, path), Charsets.UTF_8, CSVFormat.DEFAULT.withFirstRecordAsHeader())
                .use { parser -> parser.records.map { it.toMap() } }

        fun debug(q: String?, actual: ResultSetRewindable) {
            println("Q: $q")
            ResultSetFormatter.out(System.out, actual)
            actual.reset()
        }
    }

    @Test
    fun `the example GraphQL responses are processed without errors`() {
        assertEquals(emptyList(), processingErrors)
    }

    @Test
    fun `the example GraphQL responses are mapped to data conforming to the shapes`() {
        assertEquals(emptyList(), reports.flatMap { report -> report.violations.map { "${report.receivedEvent.id}: $it" } })
    }

    @Test
    fun `generated KG matches example KG`() {
        val example = RDFDataMgr.loadModel("src/test/resources/example/example.ttl")
        store.dataset.defaultModel.listStatements().forEach {
            println("Checking generated triple: ${it.subject} ${it.predicate} ${it.`object`}")
            assertTrue (example.contains(it), "Generated statement not found $it")
        }

        example.listStatements().forEach {
            println("Checking example triple: ${it.subject} ${it.predicate} ${it.`object`}")
            assertTrue (store.dataset.defaultModel.contains(it), "Example statement not found: $it" )
        }
    }

    @TestFactory
    fun `competency questions pass on example KG`() {
        val example = RDFDataMgr.loadModel("src/test/resources/example.ttl")
        readCsv("competency_questions.csv")
            .filter { !it["SPARQL"].isNullOrBlank() }
            .map { cq ->
                dynamicTest("${cq["#"]}: ${cq["Competency Question"]}") {
                    // Query the mock endpoints' named graphs instead of the remote services
                    val query = QueryFactory.create(cq["SPARQL"]!!.replace(Regex("""\bSERVICE\s+<"""), "GRAPH <"))
                    val actual: ResultSetRewindable = QueryExecutionFactory.create(query, example)
                        .use { ResultSetFactory.copyResults(it.execSelect()) }
                    val expected = File(examplesDir, "expected/${cq["#"]}.tsv").inputStream()
                        .use { ResultSetFactory.copyResults(ResultSetMgr.read(it, ResultSetLang.RS_TSV)) }
                    val matches = if (query.hasOrderBy()) ResultSetCompare.equalsByValueAndOrder(expected, actual)
                    else ResultSetCompare.equalsByValue(expected, actual)
                    debug(cq["Competency Question"], actual)
                    if (!matches) {
                        expected.reset()
                        actual.reset()
                        fail("expected:\n${ResultSetFormatter.asText(expected)}\nactual:\n${ResultSetFormatter.asText(actual)}")
                    }
                }
            }
    }

    @TestFactory
    fun `competency questions pass on generated KG`() = readCsv("competency_questions.csv")
        .filter { !it["SPARQL"].isNullOrBlank() }
        .map { cq ->
            dynamicTest("${cq["#"]}: ${cq["Competency Question"]}") {
                // Query the mock endpoints' named graphs instead of the remote services
                val query = QueryFactory.create(cq["SPARQL"]!!.replace(Regex("""\bSERVICE\s+<"""), "GRAPH <"))
                val actual: ResultSetRewindable = QueryExecutionFactory.create(query, store.dataset)
                    .use { ResultSetFactory.copyResults(it.execSelect()) }
                val expected = File(examplesDir, "expected/${cq["#"]}.tsv").inputStream()
                    .use { ResultSetFactory.copyResults(ResultSetMgr.read(it, ResultSetLang.RS_TSV)) }
                val matches = if (query.hasOrderBy()) ResultSetCompare.equalsByValueAndOrder(expected, actual)
                    else ResultSetCompare.equalsByValue(expected, actual)
                debug(cq["Competency Question"], actual)
                if (!matches) {
                    expected.reset()
                    actual.reset()
                    fail("expected:\n${ResultSetFormatter.asText(expected)}\nactual:\n${ResultSetFormatter.asText(actual)}")
                }
            }
        }
}
