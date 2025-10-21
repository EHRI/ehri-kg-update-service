package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.consumers.EHRISSEConsumer
import eu.ehri_project.ehri_kg.processors.EHRIUpdatesProcessor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json

fun main() {

    val logger = KotlinLogging.logger {}

    val observable = EHRISSEConsumer().processEvents()
    EHRIUpdatesProcessor()
        .process(observable)
        .blockingGet()
        .blockingForEach {
            logger.info { "Report for the processed event:\n${Json.encodeToString(it)}" }
        }
}