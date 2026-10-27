package eu.ehri_project.ehri_kg.batch

import eu.ehri_project.ehri_kg.helpers.Config
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.util.concurrent.TimeUnit

/** Some chunks could not be mapped. */
class BatchMappingException(message: String) : Exception(message)

/**
 * Maps chunks with [ChunkMapper], in [workers] processes of their own (see [main] in MapWorker), or in this process
 * when there is one. Chunks already mapped are skipped, so a failed run can be resumed.
 *
 * @param configPath the properties file with the entities configurations
 * @param workerJvmArgs the JVM arguments of the worker processes, e.g. their heap size
 */
class BatchMapper(
    private val configPath: String,
    private val workers: Int,
    private val workerJvmArgs: List<String> = emptyList(),
) {
    private val logger = KotlinLogging.logger {}

    /** Maps the jobs not done yet, failing if any of them couldn't be. Worker logs and job lists go in [workDir]. */
    fun map(jobs: List<MapJob>, workDir: File) {
        val pending = jobs.filterNot { it.done }
        pending.forEach { it.error.delete() }
        logger.info { "Mapping ${pending.size} of ${jobs.size} chunks, ${jobs.size - pending.size} already mapped" }
        if (workers <= 1) mapInProcess(pending) else mapInWorkers(pending, workDir)

        val failed = jobs.filterNot { it.done }
        if (failed.isNotEmpty()) {
            val reasons = failed.take(10).joinToString("\n") { job ->
                "${job.chunk}: " + (job.error.takeIf { it.exists() }?.readLines()?.firstOrNull() ?: "no output; see the worker logs in $workDir")
            }
            throw BatchMappingException("${failed.size} of ${jobs.size} chunks could not be mapped, e.g.\n$reasons")
        }
    }

    private fun mapInProcess(jobs: List<MapJob>) {
        val mapper = ChunkMapper(Config(configPath))
        jobs.forEach { job ->
            try {
                mapper.map(job)
            } catch (e: Exception) {
                logger.error(e) { "Mapping ${job.chunk} failed" }
                job.error.parentFile.mkdirs()
                job.error.writeText(e.stackTraceToString())
            }
        }
    }

    private fun mapInWorkers(jobs: List<MapJob>, workDir: File) {
        workDir.mkdirs()
        // Largest chunks first, dealt out in turn, to share the work evenly
        val shares = jobs.sortedByDescending { it.chunk.length() }.withIndex()
            .groupBy({ it.index % workers }, { it.value }).values
        val java = ProcessHandle.current().info().command().orElse("java")
        val processes = mutableListOf<Process>()
        // Workers left running after this process ends would map the same chunks as those of the next run
        val stopWorkers = Thread { processes.forEach { it.destroy() } }
        Runtime.getRuntime().addShutdownHook(stopWorkers)
        try {
            shares.forEachIndexed { i, share ->
                val jobsFile = File(workDir, "worker-${i + 1}.jobs").apply { writeText(share.joinToString("") { it.toLine() + "\n" }) }
                processes += ProcessBuilder(listOf(java) + workerJvmArgs + listOf("-cp", System.getProperty("java.class.path"),
                    "eu.ehri_project.ehri_kg.batch.MapWorkerKt", configPath, jobsFile.path))
                    .redirectErrorStream(true)
                    .redirectOutput(File(workDir, "worker-${i + 1}.log"))
                    .start()
            }
            logger.info { "Started ${processes.size} mapping workers, logging to $workDir" }
            while (!processes.all { it.waitFor(30, TimeUnit.SECONDS) }) {
                logger.info { "Mapped ${jobs.count { it.done }} of ${jobs.size} chunks" }
            }
            processes.forEachIndexed { i, process ->
                if (process.exitValue() != 0) logger.error { "Mapping worker ${i + 1} exited with ${process.exitValue()}" }
            }
        } finally {
            processes.filter { it.isAlive }.forEach { it.destroy() }
            try {
                Runtime.getRuntime().removeShutdownHook(stopWorkers)
            } catch (_: IllegalStateException) {
                // Already shutting down, so the hook is running
            }
        }
    }

    companion object {
        /**
         * Writes the mapped data of all the jobs to [output] as N-Triples, and their violations to [violations],
         * returning the number of triples and violations of each source.
         */
        fun writeOutput(jobs: List<MapJob>, output: File, violations: File): Map<String, Pair<Long, Int>> {
            val counts = linkedMapOf<String, Pair<Long, Int>>()
            output.parentFile?.mkdirs()
            output.outputStream().buffered().use { out ->
                violations.bufferedWriter().use { violationsOut ->
                    jobs.forEach { job ->
                        job.output.inputStream().use { it.copyTo(out) }
                        val triples = job.output.useLines { lines -> lines.count { it.isNotBlank() } }.toLong()
                        val jobViolations = job.violations.readLines().filter { it.isNotEmpty() }
                        jobViolations.forEach { violationsOut.write("${job.source}/${job.chunk.name}: $it\n") }
                        val (t, v) = counts.getOrDefault(job.source, 0L to 0)
                        counts[job.source] = (t + triples) to (v + jobViolations.size)
                    }
                }
            }
            return counts
        }
    }
}
