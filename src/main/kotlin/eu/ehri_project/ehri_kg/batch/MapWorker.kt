package eu.ehri_project.ehri_kg.batch

import eu.ehri_project.ehri_kg.helpers.Config
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Maps the chunks listed in a jobs file, in a process of its own: ShExML reads its input from `System.in` and
 * compiles mapping functions with one global compiler, so mapping only runs in parallel in separate processes.
 * A chunk that fails is recorded in its job's error file, and the others are still mapped.
 *
 * Arguments: the properties file with the entities configurations, and the jobs file.
 */
fun main(args: Array<String>) {
    val (configPath, jobsFile) = args
    val mapper = ChunkMapper(Config(configPath))
    val jobs = File(jobsFile).readLines().filter { it.isNotBlank() }.map(MapJob::fromLine)
    jobs.filterNot { it.done }.forEach { job ->
        try {
            val triples = mapper.map(job)
            logger.info { "Mapped ${job.chunk} to $triples triples" }
        } catch (e: Exception) {
            logger.error(e) { "Mapping ${job.chunk} failed" }
            job.error.parentFile.mkdirs()
            job.error.writeText(e.stackTraceToString())
        }
    }
}
