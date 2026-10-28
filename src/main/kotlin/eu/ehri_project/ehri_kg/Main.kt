package eu.ehri_project.ehri_kg

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import eu.ehri_project.ehri_kg.consumers.EHRISSEConsumer
import eu.ehri_project.ehri_kg.consumers.SseConnectionException
import eu.ehri_project.ehri_kg.consumers.SseEndpointCheck
import eu.ehri_project.ehri_kg.database.DatabaseManager
import eu.ehri_project.ehri_kg.graphql.GraphQLQueryProcessor
import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.DebugLogging
import eu.ehri_project.ehri_kg.helpers.KafkaEmitter
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.processors.EHRIUpdatesProcessor
import eu.ehri_project.ehri_kg.processors.UpdatesProcessorFactory
import eu.ehri_project.ehri_kg.sparql.LoggingSparqlStore
import eu.ehri_project.ehri_kg.sparql.RemoteSparqlStore
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EhriKgUpdateService().subcommands(BatchCommand()).main(args)
}

class EhriKgUpdateService : CliktCommand() {
    val logger = KotlinLogging.logger {}

    // Without a subcommand, the service listens to the Portal's events
    override val invokeWithoutSubcommand = true

    val mappingFile by option("-m", "--mapping",
            help="The mapping rules to be processed by shexml-streaming. Default: conf/ehri_sse_mapping.shexml")
        .default("conf/ehri_sse_mapping.shexml")
    val entitiesConfig by option("-c", "--conf",
            help="The properties file with the entities configurations. Default: conf/config.properties")
        .default("conf/config.properties")
    val outputToFile by option("-o", "--output",
            help="File path where to store the reports of this service. Example: output.jsonl")
    val sseEndpoint by option("-e", "--sseEndpoint",
            help="SSE endpoint to listen to, overriding the sseEndpoint property and the STREAM URL of the mapping rules. Example: https://portal.ehri-project.eu/admin/monitor/_events")
    val resumeFromEventId by option("--resumeFromEventId",
            help="Event to resume the SSE stream from, that event included, overriding the resumeFromEventId property. Example: 01a1d504-64aa-764a-ae6c-25adbe186009")
    val dryRun by option("-n", "--dryRun",
            help="Log the SPARQL update statements instead of executing them, without accessing the triple store. The events history is not updated.")
        .flag()
    val debug by option("--debug",
            help="Log what the service does in detail, with timings: the events received, the GraphQL requests, the mapping, validation and SPARQL updates.")
        .flag()
    val kafkaOptions by KafkaOptions().cooccurring()


    override fun run() {
        if (debug) DebugLogging.enable()
        if (currentContext.invokedSubcommand != null) return
        val kafkaEmitter = kafkaOptions?.let { KafkaEmitter(it.kafkaServer, it.kafkaTopic) }
        val config = Config(entitiesConfig)
        val consumer = EHRISSEConsumer(
            mappingFile,
            lastEventId = resumeFromEventId ?: config.getOptional("resumeFromEventId"),
            sseEndpoint = sseEndpoint ?: config.getOptional("sseEndpoint")
        )
        logger.debug { "Configuration $entitiesConfig: GraphQL endpoint ${config.get("graphQLEndpoint")}, " +
            "SPARQL endpoints ${config.get("querySparqlEndpoint")} and ${config.get("updateSparqlEndpoint")}, " +
            "resuming from event ${consumer.lastEventId ?: "none"}" }
        val database = DatabaseManager(config)
        val sparqlStore = if (dryRun) LoggingSparqlStore()
            else RemoteSparqlStore(config.get("querySparqlEndpoint"), config.get("updateSparqlEndpoint"))
        if (dryRun) logger.info { "Dry run: SPARQL update statements will be logged, not executed" }
        HttpClient().use { httpClient ->
            val sseUrl = consumer.streamUrl()
            logger.debug { "Listening to the SSE stream at $sseUrl" }
            try {
                runBlocking { SseEndpointCheck.verify(httpClient, sseUrl) }
            } catch (e: SseConnectionException) {
                logger.error(e.cause) { e.message }
                throw ProgramResult(1)
            }
            val graphQLClient = GraphQLQueryProcessor(config.get("graphQLEndpoint"), httpClient)
            val factory = UpdatesProcessorFactory(config, graphQLClient, sparqlStore = sparqlStore)
            EHRIUpdatesProcessor(config, database, factory::createUpdateProcessor, dryRun)
                .process(consumer.processEvents())
                .blockingGet()
                .blockingForEach { eventReport ->
                    val jsonReport = Json.encodeToString(eventReport)
                    logger.info { "Report for the processed event:\n${jsonReport}" }
                    if (eventReport.receivedEvent.eventId.isNotEmpty()) {
                        outputToFile?.let {
                            val filteredJsonReport = Json.encodeToString(listOf(eventReport))
                            SourceHelper.writeToFile(it, "${filteredJsonReport}\n")
                        }
                        if (!dryRun) database.insertReport(eventReport)
                    }
                    kafkaEmitter?.sendMessage(jsonReport)
                }
            logger.error { "The SSE stream at $sseUrl ended unexpectedly" }
            throw ProgramResult(1)
        }
    }
}

class KafkaOptions : OptionGroup() {
    val kafkaServer by option("--kafkaServer",
        help="Kafka topic where to push the reports of this service. Example: localhost:9092").required()
    val kafkaTopic by option("--kafkaTopic",
        help="Kafka topic where to push the reports of this service. Example: my-topic").required()
}