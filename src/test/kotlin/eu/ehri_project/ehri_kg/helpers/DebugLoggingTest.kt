package eu.ehri_project.ehri_kg.helpers

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugLoggingTest {

    private val logger = LoggerFactory.getLogger("eu.ehri_project.ehri_kg") as Logger

    @AfterTest
    fun reset() {
        System.clearProperty(DebugLogging.PROPERTY)
        logger.level = Level.INFO
    }

    @Test
    fun `the service logs at info by default`() {
        assertFalse(DebugLogging.enabled)
        assertEquals(emptyList(), DebugLogging.jvmArgs())
    }

    @Test
    fun `enabling debugs the service's own loggers, not the libraries'`() {
        DebugLogging.enable()
        assertTrue(DebugLogging.enabled)
        assertTrue(LoggerFactory.getLogger("eu.ehri_project.ehri_kg.batch.BatchRun").isDebugEnabled)
        assertFalse(LoggerFactory.getLogger("org.apache.jena").isDebugEnabled)
    }

    @Test
    fun `processes started with its JVM arguments debug too`() {
        DebugLogging.enable()
        val java = ProcessHandle.current().info().command().orElse("java")
        val process = ProcessBuilder(listOf(java) + DebugLogging.jvmArgs() +
            listOf("-cp", System.getProperty("java.class.path"), DebugProbe::class.java.name))
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(60, TimeUnit.SECONDS)
        assertEquals("true", output.lines().last { it.isNotBlank() }, output)
    }
}

/** Prints whether the service's loggers debug, in a process of its own. */
object DebugProbe {
    @JvmStatic
    fun main(args: Array<String>) = println(LoggerFactory.getLogger("eu.ehri_project.ehri_kg.batch.MapWorker").isDebugEnabled)
}
