package eu.ehri_project.ehri_kg

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import eu.ehri_project.ehri_kg.consumers.EHRISSEConsumer
import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.KafkaEmitter
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.processors.EHRIUpdatesProcessor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EhriKgUpdateService().main(args)
}

class EhriKgUpdateService : CliktCommand() {
    val logger = KotlinLogging.logger {}

    val mappingFile by option("-m", "--mapping",
            help="The mapping rules to be processed by shexml-streaming. Default: conf/ehri_sse_mapping.shexml (embedded in the library)")
        .default("conf/ehri_sse_mapping.shexml")
    val entitiesConfig by option("-c", "--conf",
            help="The properties file with the entities configurations. Default: conf/config.properties (embedded in the library)")
        .default("conf/config.properties")
    val outputToFile by option("-o", "--output",
            help="File path where to store the reports of this service. Example: output.jsonl")
    val kafkaOptions by KafkaOptions().cooccurring()


    override fun run() {
        val kafkaEmitter = kafkaOptions?.let { KafkaEmitter(it.kafkaServer, it.kafkaTopic) }
        val observable = EHRISSEConsumer(mappingFile).processEvents()
        EHRIUpdatesProcessor(Config(entitiesConfig))
            .process(observable)
            .blockingGet()
            .blockingForEach { eventReport ->
                val jsonReport = Json.encodeToString(eventReport)
                logger.info { "Report for the processed event:\n${jsonReport}" }
                outputToFile?.let {
                    SourceHelper.writeToFile(it, "${jsonReport}\n")
                }
                kafkaEmitter?.sendMessage(jsonReport)
            }
    }
}

class KafkaOptions : OptionGroup() {
    val kafkaServer by option("--kafkaServer",
        help="Kafka topic where to push the reports of this service. Example: localhost:9092").required()
    val kafkaTopic by option("--kafkaTopic",
        help="Kafka topic where to push the reports of this service. Example: my-topic").required()
}