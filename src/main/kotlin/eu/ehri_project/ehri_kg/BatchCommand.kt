package eu.ehri_project.ehri_kg

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.int
import eu.ehri_project.ehri_kg.batch.BatchMapper
import eu.ehri_project.ehri_kg.batch.BatchMappingException
import eu.ehri_project.ehri_kg.batch.BatchResponseException
import eu.ehri_project.ehri_kg.batch.BatchRun
import eu.ehri_project.ehri_kg.batch.BatchSource
import eu.ehri_project.ehri_kg.batch.StreamFetcher
import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.DebugLogging
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import kotlin.time.Duration.Companion.seconds

class BatchCommand : CliktCommand(name = "batch") {
    private val logger = KotlinLogging.logger {}

    override fun help(context: Context) =
        "Convert all the Portal's entities to N-Triples, for loading into an empty triple store."

    val entitiesConfig by option("-c", "--conf",
            help = "The properties file with the entities configurations. Default: conf/config.properties")
        .default("conf/config.properties")
    val dir by option("-d", "--dir",
            help = "Directory for the fetched responses, chunks and mapped data, so that a failed run can be resumed. Default: batch")
        .default("batch")
    val output by option("-o", "--output", help = "N-Triples file to write the data to. Default: <dir>/kg.nt")
    val sources by option("-s", "--sources",
            help = "Comma-separated sources to convert, e.g. countries,repositories,ehri_pers. Default: all of them")
        .split(",")
    val workers by option("-w", "--workers",
            help = "Number of processes mapping in parallel. Default: half the available processors")
        .int().default(maxOf(1, Runtime.getRuntime().availableProcessors() / 2))
    val workerHeap by option("--workerHeap", help = "Maximum heap of each mapping process. Default: 1g").default("1g")
    val graphQLEndpoint by option("-g", "--graphQLEndpoint",
            help = "GraphQL endpoint to fetch the entities from, overriding the graphQLEndpoint property. Example: http://localhost:9000/api/graphql")
    val debug by option("--debug",
            help = "Log what the batch does in detail, with timings, including in the mapping workers' logs")
        .flag()

    override fun run() {
        if (debug) DebugLogging.enable()
        val config = Config(entitiesConfig)
        val allSources = BatchSource.fromConfig(config)
        val selected = sources?.let { names ->
            val unknown = names - allSources.map { it.name }.toSet()
            if (unknown.isNotEmpty()) {
                logger.error { "Unknown sources $unknown; the sources are ${allSources.map { it.name }}" }
                throw ProgramResult(1)
            }
            allSources.filter { it.name in names }
        } ?: allSources
        logger.debug { "Converting ${selected.map { it.name }} from ${graphQLEndpoint ?: config.get("graphQLEndpoint")} " +
            "in $dir, in chunks of ${config.get("batchChunkSize")} with $workers workers of $workerHeap" }
        val report = try {
            StreamFetcher.streamingClient().use { client ->
                BatchRun(selected, StreamFetcher(graphQLEndpoint ?: config.get("graphQLEndpoint"), client),
                    BatchMapper(entitiesConfig, workers, listOf("-Xmx$workerHeap") + DebugLogging.jvmArgs()),
                    config.get("batchChunkSize").toInt(), File(dir), output?.let(::File) ?: File(dir, "kg.nt"),
                    config.getOptional("batchStaticFiles").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        .map(::File)
                ).run()
            }
        } catch (e: Exception) {
            if (e !is BatchMappingException && e !is BatchResponseException) logger.error(e) { "The batch conversion failed" }
            else logger.error { "The batch conversion failed: ${e.message}" }
            logger.error { "Run the same command again to resume it" }
            throw ProgramResult(1)
        }

        report.sources.forEach {
            logger.info { "${it.name}: ${it.items} items in ${it.chunks} chunks, ${it.triples} triples, ${it.violations} violations" }
        }
        if (report.staticTriples > 0) logger.info { "Added ${report.staticTriples} triples from the static files" }
        with(report.seconds) {
            logger.info { "Wrote ${report.triples} triples in ${total.seconds} (fetching ${fetching.seconds}, " +
                "chunking ${chunking.seconds}, mapping ${mapping.seconds}, writing ${writing.seconds})" }
        }
        logger.info { "Wrote the data to ${report.output}, the violations to ${report.violations} and this report to $dir/report.json" }
    }
}
