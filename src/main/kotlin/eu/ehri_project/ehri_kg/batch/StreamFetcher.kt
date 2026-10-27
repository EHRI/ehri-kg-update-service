package eu.ehri_project.ehri_kg.batch

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Fetches each [BatchSource] to a file with one streamed GraphQL request. With the `X-Stream` header and no `first`
 * argument, the backend writes all of a list field's items as it reads them, in one transaction, rather than counting
 * the whole collection for each page of at most 100.
 */
class StreamFetcher(private val endpoint: String, private val client: HttpClient) {

    private val logger = KotlinLogging.logger {}

    /**
     * The file of the source's response in [dir], fetching it unless already there. The response is written to a
     * `.part` file first, so an interrupted fetch is never mistaken for a complete one.
     */
    suspend fun fetch(source: BatchSource, dir: File): File {
        val target = File(dir, "${source.name}.json")
        if (target.exists()) {
            logger.info { "Skipping ${source.name}, already fetched to $target" }
            return target
        }
        dir.mkdirs()
        val part = File(dir, "${source.name}.json.part")
        logger.info { "Fetching ${source.name} to $target" }
        client.preparePost(endpoint) {
            contentType(ContentType.Application.Json)
            header(STREAM_HEADER, "true")
            setBody(source.body)
        }.execute { response ->
            if (!response.status.isSuccess())
                error("Fetching ${source.name} failed with ${response.status}: ${response.bodyAsText().take(1000)}")
            part.outputStream().use { out -> response.bodyAsChannel().toInputStream().use { it.copyTo(out) } }
        }
        if (part.length() == 0L) {
            part.delete()
            throw BatchResponseException("Fetching ${source.name} gave an empty response. ${explainEmptyResponse(source)}")
        }
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        logger.info { "Fetched ${source.name}: ${target.length()} bytes" }
        return target
    }

    /**
     * Once streaming has begun, the backend can't report errors, e.g. a query exceeding its complexity limit, so it
     * sends nothing. Without streaming, it reports them, returning just the first page of items otherwise.
     */
    private suspend fun explainEmptyResponse(source: BatchSource): String {
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(source.body)
        }
        val body = response.bodyAsText()
        return if (!response.status.isSuccess() || body.contains("\"errors\""))
            "Without streaming, the query fails with ${response.status}: ${body.take(1000)}"
        else "The query succeeds without streaming, so the backend failed while streaming; see its logs."
    }

    companion object {
        const val STREAM_HEADER = "X-Stream"

        /** A client without the CIO engine's default 15s request timeout, which would cut off a long stream. */
        fun streamingClient(): HttpClient = HttpClient(CIO) { engine { requestTimeout = 0 } }
    }
}
