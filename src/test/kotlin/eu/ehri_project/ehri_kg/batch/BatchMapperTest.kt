package eu.ehri_project.ehri_kg.batch

import eu.ehri_project.ehri_kg.support.testConfig
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Maps the example GraphQL responses in batch, as streamed responses of each source, chunked and mapped. */
class BatchMapperTest {

    private val dir = createTempDirectory().toFile()
    private val sources = BatchSource.fromConfig(testConfig(dir))

    private fun jobs(chunkSize: Int): List<MapJob> {
        exampleResponses(sources).forEach { (source, response) ->
            val file = File(dir, "responses/${source.name}.json").apply { parentFile.mkdirs(); writeText(response) }
            ItemChunker.writeChunks(file, source, chunkSize, File(dir, "chunks/${source.name}"))
        }
        return MapJob.forChunks(sources, File(dir, "chunks"), File(dir, "mapped"))
    }

    @Test
    fun `mapping in this process gives the example KG`() {
        val jobs = jobs(chunkSize = 2)
        BatchMapper("conf/config.properties", workers = 1).map(jobs, File(dir, "work"))
        val counts = BatchMapper.writeOutput(jobs, File(dir, "kg.nt"), File(dir, "violations.txt"))
        assertMatchesExampleKG(File(dir, "kg.nt"))
        assertEquals(emptyList(), File(dir, "violations.txt").readLines())
        assertEquals(sources.map { it.name }.filter { counts.containsKey(it) }, counts.keys.toList())
    }

    @Test
    fun `mapping in worker processes gives the example KG`() {
        val jobs = jobs(chunkSize = 1)
        BatchMapper("conf/config.properties", workers = 2, listOf("-Xmx512m")).map(jobs, File(dir, "work"))
        BatchMapper.writeOutput(jobs, File(dir, "kg.nt"), File(dir, "violations.txt"))
        assertMatchesExampleKG(File(dir, "kg.nt"))
        assertEquals(2, File(dir, "work").listFiles { f -> f.extension == "log" }!!.size)
    }

    @Test
    fun `chunks already mapped are skipped, and failed chunks reported`() {
        val jobs = jobs(chunkSize = 2)
        val mapper = BatchMapper("conf/config.properties", workers = 1)
        mapper.map(jobs.take(1), File(dir, "work"))
        val mapped = jobs.first().output.readText()
        jobs.first().output.writeText("") // not mapped again, so left as it is
        val broken = jobs.last().copy(chunk = File(dir, "missing.json"))
        val e = assertFailsWith<BatchMappingException> { mapper.map(jobs.dropLast(1) + broken, File(dir, "work")) }
        assertTrue(e.message!!.startsWith("1 of ${jobs.size} chunks could not be mapped"), e.message)
        assertEquals("", jobs.first().output.readText())
        assertTrue(mapped.isNotEmpty())
        assertTrue(broken.error.exists())
    }
}
