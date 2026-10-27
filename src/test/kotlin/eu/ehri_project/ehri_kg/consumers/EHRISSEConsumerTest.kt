package eu.ehri_project.ehri_kg.consumers

import com.sun.net.httpserver.HttpServer
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.processors.EventExtractor
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class EHRISSEConsumerTest {

    @Test
    fun `events are read from an SSE stream`() {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/events") { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use {
                    it.write(("id: e1\nevent: update-event\n" +
                        "data: {\"datetime\":\"2026-01-01T00:00:00Z\",\"ids\":[\"be\"],\"types\":[\"Country\"]}\n\n").toByteArray())
                }
            }
            start()
        }
        try {
            val consumer = EHRISSEConsumer(mappingPath, sseEndpoint = "http://localhost:${server.address.port}/events")
            val extractor = EventExtractor("conf/sparqlGetEventDetails.rq")
            val event = consumer.processEvents().blockingGet()
                .concatMapIterable { extractor.events(it) }
                .timeout(30, TimeUnit.SECONDS)
                .blockingFirst()
            assertEquals(EHRIEvent("e1", "update-event", "2026-01-01T00:00:00Z", "be", "Country"), event)
        } finally {
            server.stop(0)
        }
    }

    private val mappingPath = "conf/ehri_sse_mapping.shexml"
    private val streamLine = "STREAM ehri_sse <https://portal.ehri-project.eu/admin/monitor/_events>"

    private fun changedLines(consumer: EHRISSEConsumer): List<Pair<String, String>> {
        val original = consumer.mappingRules.lines()
        val rewritten = consumer.buildMappingRules().lines()
        assertEquals(original.size, rewritten.size)
        return original.zip(rewritten).filter { (a, b) -> a != b }
    }

    @Test
    fun `mapping rules are unchanged without overrides`() {
        val consumer = EHRISSEConsumer(mappingPath)

        assertEquals(consumer.mappingRules, consumer.buildMappingRules())
    }

    @Test
    fun `last event id is appended to the stream URL only`() {
        val consumer = EHRISSEConsumer(mappingPath, lastEventId = "12345")

        assertEquals(
            listOf(streamLine to "STREAM ehri_sse <https://portal.ehri-project.eu/admin/monitor/_events?Last-Event-Id=12345>"),
            changedLines(consumer)
        )
    }

    @Test
    fun `sse endpoint replaces the stream URL`() {
        val consumer = EHRISSEConsumer(mappingPath, sseEndpoint = "http://localhost:9000/events")

        assertEquals(
            listOf(streamLine to "STREAM ehri_sse <http://localhost:9000/events>"),
            changedLines(consumer)
        )
    }

    @Test
    fun `sse endpoint and last event id are combined`() {
        val consumer = EHRISSEConsumer(mappingPath, lastEventId = "12345", sseEndpoint = "http://localhost:9000/events")

        assertEquals(
            listOf(streamLine to "STREAM ehri_sse <http://localhost:9000/events?Last-Event-Id=12345>"),
            changedLines(consumer)
        )
    }

    @Test
    fun `last event id is added to an existing query string`() {
        val consumer = EHRISSEConsumer(mappingPath, lastEventId = "12345", sseEndpoint = "http://localhost:9000/events?type=all")

        assertEquals(
            listOf(streamLine to "STREAM ehri_sse <http://localhost:9000/events?type=all&Last-Event-Id=12345>"),
            changedLines(consumer)
        )
    }

    @Test
    fun `last event id is URL-encoded`() {
        val consumer = EHRISSEConsumer(mappingPath, lastEventId = "a b&c")

        assertEquals(
            listOf(streamLine to "STREAM ehri_sse <https://portal.ehri-project.eu/admin/monitor/_events?Last-Event-Id=a+b%26c>"),
            changedLines(consumer)
        )
    }
}
