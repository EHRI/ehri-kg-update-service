package eu.ehri_project.ehri_kg.batch

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SourceReport(val name: String, val items: Int, val chunks: Int, val triples: Long, val violations: Int)

@Serializable
data class BatchReport(val sources: List<SourceReport>, val output: String, val violations: String)

/**
 * Converts all the entities of the [sources] to N-Triples: fetching each source's streamed response, splitting it into
 * chunks, mapping and validating the chunks, and writing their data to [output]. Each step resumes where an earlier
 * run left off, in [dir]: responses already fetched aren't fetched again, nor chunks already mapped. To fetch a source
 * again, delete its response in `<dir>/responses`.
 */
class BatchRun(
    private val sources: List<BatchSource>,
    private val fetcher: StreamFetcher,
    private val mapper: BatchMapper,
    private val chunkSize: Int,
    private val dir: File,
    private val output: File = File(dir, "kg.nt"),
) {
    private val logger = KotlinLogging.logger {}

    init {
        require(chunkSize > 0) { "The chunk size must be positive, not $chunkSize" }
    }

    private val responsesDir = File(dir, "responses")
    private val chunksDir = File(dir, "chunks")
    private val mappedDir = File(dir, "mapped")

    fun run(): BatchReport {
        val items = sources.associate { source ->
            val response = runBlocking { fetcher.fetch(source, responsesDir) }
            source.name to chunk(source, response)
        }
        forgetMappingsWithAnotherConfiguration()
        val jobs = MapJob.forChunks(sources, chunksDir, mappedDir)
        mapper.map(jobs, File(dir, "work"))
        val violations = File(dir, "violations.txt")
        val counts = BatchMapper.writeOutput(jobs, output, violations)
        val report = BatchReport(
            sources.map { source ->
                val (triples, sourceViolations) = counts[source.name] ?: (0L to 0)
                SourceReport(source.name, items.getValue(source.name), jobs.count { it.source == source.name },
                    triples, sourceViolations)
            },
            output.path, violations.path)
        File(dir, "report.json").writeText(Json { prettyPrint = true }.encodeToString(report))
        return report
    }

    /**
     * Removes the data mapped by an earlier run if its mapping configuration differs from this run's, e.g. because the
     * sets in the KG changed, so that the chunks are mapped again rather than mixing two configurations' data.
     */
    private fun forgetMappingsWithAnotherConfiguration() {
        val recorded = File(mappedDir, "configuration.txt")
        logger.debug { "Mapping configuration:\n${mapper.configuration}" }
        if (mappedDir.exists() && recorded.takeIf { it.exists() }?.readText() != mapper.configuration) {
            logger.warn { "The mapping configuration changed since the chunks in $mappedDir were mapped, so all " +
                "the chunks will be mapped again. It was:\n${recorded.takeIf { it.exists() }?.readText() ?: "not recorded"}" }
            mappedDir.deleteRecursively()
        }
        mappedDir.mkdirs()
        recorded.writeText(mapper.configuration)
    }

    /**
     * Splits the source's response into chunks, unless it already was since it was fetched, returning the number of
     * items. Chunking again removes the chunks mapped from the earlier response.
     */
    private fun chunk(source: BatchSource, response: File): Int {
        val sourceChunks = File(chunksDir, source.name)
        val complete = File(chunksDir, "${source.name}.items")
        if (complete.exists() && complete.lastModified() >= response.lastModified()) {
            logger.info { "Skipping chunking ${source.name}, already chunked" }
            return complete.readText().trim().toInt()
        }
        complete.delete()
        File(mappedDir, source.name).deleteRecursively()
        val (_, items) = try {
            ItemChunker.writeChunks(response, source, chunkSize, sourceChunks)
        } catch (e: BatchResponseException) {
            // An invalid response, e.g. from a stream the backend aborted, is fetched again by the next run; other
            // errors, e.g. writing the chunks to a full disk, leave it to be chunked again
            response.delete()
            sourceChunks.deleteRecursively()
            throw BatchResponseException("The response for ${source.name} is incomplete or invalid, so it was deleted " +
                "to be fetched again: ${e.message}")
        }
        complete.writeText("$items\n")
        return items
    }

}
