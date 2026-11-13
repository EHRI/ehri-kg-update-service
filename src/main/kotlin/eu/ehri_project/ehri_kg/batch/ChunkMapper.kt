package eu.ehri_project.ehri_kg.batch

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.timed
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.processors.UpdatesProcessor
import eu.ehri_project.ehri_kg.processors.UpdatesProcessorFactory
import eu.ehri_project.ehri_kg.sparql.LoggingSparqlStore
import eu.ehri_project.ehri_kg.validation.TypeValidators
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A chunk of a source's items to map, and the N-Triples file its data is written to. The violations of the type's
 * shapes are written next to it, and the error, if mapping it failed.
 */
data class MapJob(val source: String, val type: EHRITypes, val chunk: File, val output: File) {
    val violations get() = File(output.path.removeSuffix(".nt") + ".violations")
    val error get() = File(output.path.removeSuffix(".nt") + ".error")
    val done get() = output.exists()

    /** The job as a line of a worker's jobs file. */
    fun toLine() = listOf(source, type.name, chunk.path, output.path).joinToString("\t")

    companion object {
        fun fromLine(line: String) = line.split("\t").let { (source, type, chunk, output) ->
            MapJob(source, EHRITypes.valueOf(type), File(chunk), File(output))
        }

        /** A job for each chunk the [ItemChunker] wrote to `<chunksDir>/<source>`, writing to `<mappedDir>/<source>`. */
        fun forChunks(sources: List<BatchSource>, chunksDir: File, mappedDir: File): List<MapJob> = sources.flatMap { source ->
            File(chunksDir, source.name).listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.map {
                MapJob(source.name, source.type, it, File(mappedDir, "${source.name}/${it.nameWithoutExtension}.nt"))
            }
        }
    }
}

/**
 * Maps chunks as the update stream maps an entity's response, with the type's [UpdatesProcessor], validating the data
 * against the type's shapes.
 */
class ChunkMapper(config: Config) {

    // Only used for mapping, so without a GraphQL endpoint or triple store
    private val factory = UpdatesProcessorFactory(config, { _, _ -> error("Chunks are mapped, not fetched") },
        sparqlStore = LoggingSparqlStore())
    private val processors = mutableMapOf<EHRITypes, UpdatesProcessor>()
    private val validators = TypeValidators(config)
    private val logger = KotlinLogging.logger {}

    /** Maps the job's chunk, returning the number of triples written. */
    fun map(job: MapJob): Long {
        val processor = processors.getOrPut(job.type) { factory.createUpdateProcessor(job.type) }
        val (model, mapMs) = timed { processor.transformToRDF(job.chunk.readText()).defaultModel }
        job.output.parentFile.mkdirs()
        val (violations, validateMs) = timed { validators.validate(job.type, model.graph) }
        job.violations.writeText(violations.joinToString("") { "$it\n" })
        logger.debug { "Mapped ${job.chunk} (${job.chunk.length()} bytes) to ${model.size()} triples in ${mapMs}ms, " +
            "validated with ${violations.size} violations in ${validateMs}ms" }
        // Written last, so that the output only exists once the job is complete
        val part = File(job.output.path + ".part")
        part.outputStream().use { RDFDataMgr.write(it, model, Lang.NTRIPLES) }
        Files.move(part.toPath(), job.output.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return model.size()
    }
}
