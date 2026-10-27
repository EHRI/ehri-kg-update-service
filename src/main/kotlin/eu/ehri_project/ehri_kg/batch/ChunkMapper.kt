package eu.ehri_project.ehri_kg.batch

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.shexml.ShExMLMappingLauncherProxy
import eu.ehri_project.ehri_kg.validation.TypeValidators
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

/** Maps chunks with their type's mapping rules, validating the data against the type's shapes. */
class ChunkMapper(private val config: Config) {

    private val rules = mutableMapOf<EHRITypes, String>()
    private val validators = TypeValidators(config)

    /** Maps the job's chunk, returning the number of triples written. */
    fun map(job: MapJob): Long {
        val mappingRules = rules.getOrPut(job.type) { SourceHelper.readFile(config.get(mappingRulesProperty(job.type))) }
        val model = ShExMLMappingLauncherProxy().convert(mappingRules, job.chunk.readText()).defaultModel
        job.output.parentFile.mkdirs()
        job.violations.writeText(validators.validate(job.type, model.graph).joinToString("") { "$it\n" })
        // Written last, so that the output only exists once the job is complete
        val part = File(job.output.path + ".part")
        part.outputStream().use { RDFDataMgr.write(it, model, Lang.NTRIPLES) }
        Files.move(part.toPath(), job.output.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return model.size()
    }

    companion object {
        fun mappingRulesProperty(type: EHRITypes): String = when (type) {
            EHRITypes.COUNTRY -> "countriesShexmlMappingRules"
            EHRITypes.INSTITUTION -> "institutionsShexmlMappingRules"
            EHRITypes.ARCHIVAL_DESCRIPTION -> "archivalDescriptionsShexmlMappingRules"
            EHRITypes.VOCABULARY -> "vocabulariesShexmlMappingRules"
            EHRITypes.CORPORATE_BODY -> "corporateBodiesShexmlMappingRules"
            EHRITypes.PERSON -> "personsShexmlMappingRules"
            EHRITypes.LINK -> "linksShexmlMappingRules"
        }
    }
}
