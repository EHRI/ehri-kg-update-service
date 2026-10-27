package eu.ehri_project.ehri_kg.batch

import com.sun.net.httpserver.HttpServer
import eu.ehri_project.ehri_kg.support.testConfig
import kotlinx.serialization.json.Json
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

    private fun run() = BatchRun(
        sources, StreamFetcher("http://localhost:${server.address.port}/graphql", client),
        BatchMapper("conf/config.properties", workers = 1), chunkSize = 2, dir = dir
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

}
