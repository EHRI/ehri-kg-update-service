package eu.ehri_project.ehri_kg.batch

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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
        logger.debug { "POST $endpoint with $STREAM_HEADER: true: ${source.body}" }
        val start = System.nanoTime()
        client.preparePost(endpoint) {
            contentType(ContentType.Application.Json)
            header(STREAM_HEADER, "true")
            setBody(source.body)
        }.execute { response ->
            if (!response.status.isSuccess())
                error("Fetching ${source.name} failed with ${response.status}: ${response.bodyAsText().take(1000)}")
            logger.debug { "Response for ${source.name}: ${response.status}, ${response.headers.entries()}" }
            part.outputStream().use { out -> response.bodyAsChannel().toInputStream().use { copyLoggingProgress(source, it, out) } }
        }
        if (part.length() == 0L) {
            part.delete()
            throw BatchResponseException("Fetching ${source.name} gave an empty response. ${explainEmptyResponse(source)}")
        }
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        logger.info { "Fetched ${source.name}: ${target.length()} bytes" }
        logger.debug { "Fetched ${source.name} in ${(System.nanoTime() - start) / 1_000_000}ms" }
        return target
    }

    /** Copies the response, logging how much has been received every [PROGRESS_INTERVAL_MS], when debugging. */
    private fun copyLoggingProgress(source: BatchSource, input: InputStream, out: OutputStream) {
        if (!logger.isDebugEnabled()) {
            input.copyTo(out)
            return
        }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        var logged = System.nanoTime()
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            total += read
            if (System.nanoTime() - logged > PROGRESS_INTERVAL_MS * 1_000_000) {
                logger.debug { "Received $total bytes of ${source.name}" }
                logged = System.nanoTime()
            }
        }
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
        private const val PROGRESS_INTERVAL_MS = 10_000L

        /** A client without the CIO engine's default 15s request timeout, which would cut off a long stream. */
        fun streamingClient(): HttpClient = HttpClient(CIO) { engine { requestTimeout = 0 } }
    }
}
