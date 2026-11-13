package eu.ehri_project.ehri_kg.batch

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration
import kotlin.time.TimeSource

@Serializable
data class SourceReport(val name: String, val items: Int, val chunks: Int, val triples: Long, val violations: Int)

/**
 * The time each step of a run took, in whole seconds. A step that an earlier run completed takes no time, e.g. fetching
 * the responses a resumed run already has.
 */
@Serializable
data class StepTimes(val fetching: Long = 0, val chunking: Long = 0, val mapping: Long = 0, val writing: Long = 0,
                     val total: Long = 0)

/**
 * @param triples the number of triples written to the output, the sources' and the static files', which may include
 * triples that more than one chunk mapped, e.g. those about the entities they link to
 */
@Serializable
data class BatchReport(val sources: List<SourceReport>, val output: String, val violations: String,
                       val staticTriples: Long = 0, val triples: Long = 0, val seconds: StepTimes = StepTimes())

/**
 * Converts all the entities of the [sources] to N-Triples: fetching each source's streamed response, splitting it into
 * chunks, mapping and validating the chunks, and writing their data to [output]. Each step resumes where an earlier
 * run left off, in [dir]: responses already fetched aren't fetched again, nor chunks already mapped. To fetch a source
 * again, delete its response in `<dir>/responses`.
 *
 * @param staticFiles RDF files whose data is added to the output, such as instances that the mapped data refers to but
 * doesn't define
 */
class BatchRun(
    private val sources: List<BatchSource>,
    private val fetcher: StreamFetcher,
    private val mapper: BatchMapper,
    private val chunkSize: Int,
    private val dir: File,
    private val output: File = File(dir, "kg.nt"),
    private val staticFiles: List<File> = emptyList(),
) {
    private val logger = KotlinLogging.logger {}

    init {
        require(chunkSize > 0) { "The chunk size must be positive, not $chunkSize" }
    }

    private val responsesDir = File(dir, "responses")
    private val chunksDir = File(dir, "chunks")
    private val mappedDir = File(dir, "mapped")

    fun run(): BatchReport {
        val start = TimeSource.Monotonic.markNow()
        // Read first, so that an invalid file fails the run before the fetching and mapping
        val staticData = staticFiles.map { file -> file to RDFDataMgr.loadModel(file.path) }

        var fetching = Duration.ZERO
        var chunking = Duration.ZERO
        val items = sources.associate { source ->
            val fetchStart = TimeSource.Monotonic.markNow()
            val response = runBlocking { fetcher.fetch(source, responsesDir) }
            fetching += fetchStart.elapsedNow()
            val chunkStart = TimeSource.Monotonic.markNow()
            source.name to chunk(source, response).also { chunking += chunkStart.elapsedNow() }
        }
        val mapStart = TimeSource.Monotonic.markNow()
        forgetMappingsWithAnotherConfiguration()
        val jobs = MapJob.forChunks(sources, chunksDir, mappedDir)
        mapper.map(jobs, File(dir, "work"))
        val mapping = mapStart.elapsedNow()
        val writeStart = TimeSource.Monotonic.markNow()
        val violations = File(dir, "violations.txt")
        val counts = BatchMapper.writeOutput(jobs, output, violations)
        val staticTriples = appendStaticData(staticData)
        val writing = writeStart.elapsedNow()
        val report = BatchReport(
            sources.map { source ->
                val (triples, sourceViolations) = counts[source.name] ?: (0L to 0)
                SourceReport(source.name, items.getValue(source.name), jobs.count { it.source == source.name },
                    triples, sourceViolations)
            },
            output.path, violations.path, staticTriples,
            counts.values.sumOf { it.first } + staticTriples,
            StepTimes(fetching.inWholeSeconds, chunking.inWholeSeconds, mapping.inWholeSeconds, writing.inWholeSeconds,
                start.elapsedNow().inWholeSeconds))
        File(dir, "report.json").writeText(Json { prettyPrint = true; encodeDefaults = true }.encodeToString(report))
        return report
    }

    /** Appends the static files' data to the output as N-Triples, returning the number of triples. */
    private fun appendStaticData(staticData: List<Pair<File, Model>>): Long =
        FileOutputStream(output, true).buffered().use { out ->
            staticData.sumOf { (file, model) ->
                logger.info { "Adding the ${model.size()} triples of $file" }
                RDFDataMgr.write(out, model, RDFFormat.NTRIPLES)
                model.size()
            }
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
