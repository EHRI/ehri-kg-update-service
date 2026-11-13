package eu.ehri_project.ehri_kg.batch

import com.sun.net.httpserver.HttpServer
import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.support.testConfig
import kotlinx.serialization.json.Json
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RiotException
import java.io.File
import java.net.InetSocketAddress
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Converts the example entities, fetched from a GraphQL server serving their responses, as the batch command does. */
class BatchRunTest {

    private val dir = createTempDirectory().toFile()
    private val sources = BatchSource.fromConfig(testConfig(dir))
    private val responses = exampleResponses(sources).mapKeys { it.key.body }

    /** The names of the sources the server was asked for. */
    private val requested = mutableListOf<String>()

    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/graphql") { exchange ->
            val body = exchange.requestBody.readAllBytes().decodeToString()
            requested += sources.single { it.body == body }.name
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(responses.getValue(body).toByteArray()) }
        }
        start()
    }
    private val client = StreamFetcher.streamingClient()

    @AfterTest
    fun stop() {
        client.close()
        server.stop(0)
    }

    private fun run(resumeFromEventId: String? = null, staticFiles: List<File> = emptyList()) = BatchRun(
        sources, StreamFetcher("http://localhost:${server.address.port}/graphql", client),
        BatchMapper("conf/config.properties", workers = 1), chunkSize = 2, dir = dir, resumeFromEventId = resumeFromEventId,
        staticFiles = staticFiles
    ).run()

    @Test
    fun `converts all the sources' entities to the example KG`() {
        val report = run()
        assertMatchesExampleKG(File(dir, "kg.nt"))
        assertEquals(sources.map { it.name }, requested)
        assertEquals(sources.map { it.name }, report.sources.map { it.name })
        // Two entities of each of these, and a null item for each source
        assertEquals(listOf(2, 2, 1), report.sources.filter { it.name in setOf("countries", "repositories", "links") }.map { it.items })
        assertEquals(report, Json.decodeFromString<BatchReport>(File(dir, "report.json").readText()))
        assertEquals(null, report.resumeFromEventId)
    }

    @Test
    fun `reports the event the update stream resumes from`() {
        assertEquals("e1", run(resumeFromEventId = "e1").resumeFromEventId)
        assertTrue(File(dir, "report.json").readText().contains("\"resumeFromEventId\": \"e1\""))
    }

    @Test
    fun `a run resumes from the responses fetched and chunks mapped before`() {
        run()
        val mapped = File(dir, "mapped").walk().filter { it.extension == "nt" }.associateWith { it.lastModified() }
        requested.clear()
        run()
        assertEquals(emptyList(), requested)
        assertEquals(mapped, File(dir, "mapped").walk().filter { it.extension == "nt" }.associateWith { it.lastModified() })
        assertMatchesExampleKG(File(dir, "kg.nt"))
    }

    @Test
    fun `chunks are mapped again when the sets in the KG change`() {
        run()
        val withCamps = File(dir, "kg.nt").readText()
        assertTrue(withCamps.contains("/vocabularies/ehri-camps/1>"))

        // The same configuration, without ehri_camps
        val config = File(dir, "config.properties").apply {
            writeText(File("conf/config.properties").readText()
                .replace("vocabularySets=ehri_terms,ehri_camps,ehri_ghettos", "vocabularySets=ehri_terms,ehri_ghettos"))
        }
        requested.clear()
        BatchRun(BatchSource.fromConfig(Config(config.path)),
            StreamFetcher("http://localhost:${server.address.port}/graphql", client),
            BatchMapper(config.path, workers = 1), chunkSize = 2, dir = dir).run()

        assertEquals(emptyList(), requested)
        assertFalse(File(dir, "kg.nt").readText().contains("/vocabularies/ehri-camps/1>"))
        assertTrue(File(dir, "mapped/configuration.txt").readText().contains("vocabularySets=ehri_terms,ehri_ghettos\n"))
    }

    @Test
    fun `an incomplete response is deleted, to be fetched again by the next run`() {
        val response = File(dir, "responses/countries.json")
        response.parentFile.mkdirs()
        response.writeText("""{"data": {"countries": {"items": [{"id": "be"}, """) // as from an aborted stream
        val e = assertFailsWith<BatchResponseException> { run() }
        assertTrue(e.message!!.contains("countries"), e.message)
        assertFalse(response.exists())
        run()
        assertTrue(requested.contains("countries"))
        assertMatchesExampleKG(File(dir, "kg.nt"))
    }

    @Test
    fun `adds the static files' data to the output`() {
        val static = File(createTempDirectory().toFile(), "static.ttl").apply {
            writeText("<http://example.org/s> <http://example.org/p> \"o\" .")
        }
        val report = run(staticFiles = listOf(static))

        val kg = RDFDataMgr.loadModel(File(dir, "kg.nt").path)
        val staticData = RDFDataMgr.loadModel(static.path)
        assertFalse(staticData.isEmpty)
        assertTrue(kg.containsAll(staticData))
        assertEquals(staticData.size(), report.staticTriples)
        assertEquals(staticData.size(), kg.size() - kg.difference(staticData).size())
    }

    @Test
    fun `reports the number of triples written and the time each step took`() {
        val static = File(createTempDirectory().toFile(), "static.ttl").apply {
            writeText("<http://example.org/s> <http://example.org/p> \"o\" .")
        }
        val report = run(staticFiles = listOf(static))

        assertEquals(File(dir, "kg.nt").readLines().count { it.isNotBlank() }.toLong(), report.triples)
        assertEquals(report.sources.sumOf { it.triples } + 1, report.triples)
        with(report.seconds) { assertTrue(total >= fetching + chunking + mapping + writing - 4, "$this") }
    }

    @Test
    fun `an invalid static file fails the run before anything is fetched`() {
        val invalid = File(createTempDirectory().toFile(), "invalid.ttl").apply { writeText("not turtle") }
        assertFailsWith<RiotException> { run(staticFiles = listOf(invalid)) }
        assertEquals(emptyList(), requested)
    }

    @Test
    fun `a response is kept when its chunks can't be written`() {
        File(dir, "chunks").apply { parentFile.mkdirs(); writeText("not a directory") }
        assertFailsWith<java.io.IOException> { run() }
        assertTrue(File(dir, "responses/countries.json").exists())
    }

    @Test
    fun `a chunk size that isn't positive is refused`() {
        assertFailsWith<IllegalArgumentException> {
            BatchRun(sources, StreamFetcher("http://localhost:${server.address.port}/graphql", client),
                BatchMapper("conf/config.properties", workers = 1), chunkSize = 0, dir = dir)
        }
    }
}
