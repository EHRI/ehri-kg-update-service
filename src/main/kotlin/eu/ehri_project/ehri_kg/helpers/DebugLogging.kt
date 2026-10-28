package eu.ehri_project.ehri_kg.helpers

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory

/**
 * Debug logging of this service's own steps: what it receives, fetches, maps and writes, and how long each takes.
 * Third-party libraries keep their levels. Processes started with [jvmArgs] log at the same level, as logback.xml
 * reads the [PROPERTY] system property.
 */
object DebugLogging {
    const val PROPERTY = "ehriKg.logLevel"
    private const val PACKAGE = "eu.ehri_project.ehri_kg"

    val enabled: Boolean get() = LoggerFactory.getLogger(PACKAGE).isDebugEnabled

    fun enable() {
        System.setProperty(PROPERTY, "debug")
        (LoggerFactory.getLogger(PACKAGE) as Logger).level = Level.DEBUG
    }

    /** The JVM arguments that give a child process this process's level. */
    fun jvmArgs(): List<String> = if (enabled) listOf("-D$PROPERTY=debug") else emptyList()
}

/** Runs [block], returning its result and the milliseconds it took. */
inline fun <T> timed(block: () -> T): Pair<T, Long> {
    val start = System.nanoTime()
    val result = block()
    return result to (System.nanoTime() - start) / 1_000_000
}
