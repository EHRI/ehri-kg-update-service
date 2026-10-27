package eu.ehri_project.ehri_kg.batch

import com.sun.net.httpserver.HttpServer
import eu.ehri_project.ehri_kg.model.EHRITypes
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamFetcherTest {

    private val dir = createTempDirectory().toFile()
    private val source = BatchSource("countries", EHRITypes.COUNTRY, "countries", """{"query": "{ countries { items { id } } }"}""",
        listOf("data", "countries", "items"))

    /** The requests the server received, as their X-Stream header and body. */
    private val requests = mutableListOf<Pair<String?, String>>()
    private var status = 200
    private val parts = listOf("""{"data": {"countries": {"items": [""", """{"id": "be"}, """, """{"id": "nl"}]}}}""")

    /** Whether the server behaves as the backend does for a query it aborts, e.g. for its complexity. */
    private var abortsQuery = false
    private val abortError = """{"errors":[{"message":"maximum query complexity exceeded 59 > 50"}]}"""

    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/graphql") { exchange ->
            val streamed = exchange.requestHeaders.getFirst(StreamFetcher.STREAM_HEADER)
            requests += streamed to exchange.requestBody.readAllBytes().decodeToString()
            when {
                // Streaming has begun, so the error can't be reported
                abortsQuery && streamed != null -> exchange.sendResponseHeaders(200, -1)
                abortsQuery -> {
                    exchange.sendResponseHeaders(400, 0)
                    exchange.responseBody.use { it.write(abortError.toByteArray()) }
                }
                else -> {
                    exchange.sendResponseHeaders(status, 0) // chunked, as the backend streams it
                    exchange.responseBody.use { out -> parts.forEach { out.write(it.toByteArray()); out.flush() } }
                }
            }
            exchange.close()
        }
        start()
    }

    private val client = StreamFetcher.streamingClient()
    private val fetcher = StreamFetcher("http://localhost:${server.address.port}/graphql", client)

    @AfterTest
    fun stop() {
        client.close()
        server.stop(0)
    }

    @Test
    fun `fetches a streamed response to a file`() {
        val file = runBlocking { fetcher.fetch(source, dir) }
        assertEquals(File(dir, "countries.json"), file)
        assertEquals(parts.joinToString(""), file.readText())
        assertEquals(listOf<Pair<String?, String>>("true" to source.body), requests)
        assertFalse(File(dir, "countries.json.part").exists())
    }

    @Test
    fun `a source already fetched is not fetched again`() {
        runBlocking { fetcher.fetch(source, dir) }
        runBlocking { fetcher.fetch(source, dir) }
        assertEquals(1, requests.size)
    }

    @Test
    fun `an empty response fails with the error the query gives without streaming`() {
        abortsQuery = true
        val e = assertFailsWith<BatchResponseException> { runBlocking { fetcher.fetch(source, dir) } }
        assertTrue(e.message!!.contains("maximum query complexity exceeded"), e.message)
        assertEquals(listOf("true", null), requests.map { it.first })
        assertEquals(emptyList(), dir.listFiles()!!.toList())
    }

    @Test
    fun `an error response fails, leaving no response file`() {
        status = 400
        assertFailsWith<IllegalStateException> { runBlocking { fetcher.fetch(source, dir) } }
        assertFalse(File(dir, "countries.json").exists())
    }
}
