package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.graphql.GraphQLClient
import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.shexml.ShExMLMappingLauncherProxy
import eu.ehri_project.ehri_kg.sparql.RemoteSparqlStore
import eu.ehri_project.ehri_kg.sparql.SparqlStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
import java.io.ByteArrayOutputStream
import kotlin.text.replace

class UpdatesProcessorFactory(val config: Config,
                              val graphQLClient: GraphQLClient,
                              val querySparqlEndpoint: String = config.get("querySparqlEndpoint"),
                              val updateSparqlEndpoint: String = config.get("updateSparqlEndpoint"),
                              val sparqlStore: SparqlStore = RemoteSparqlStore(querySparqlEndpoint, updateSparqlEndpoint)) {

    private val logger = KotlinLogging.logger {}

    fun createUpdateProcessor(type: EHRITypes): UpdatesProcessor {
        logger.info { "Detected entity type $type" }
        return when(type) {
            EHRITypes.COUNTRY ->
                CountriesUpdatesProcessor(
                    config.get("countriesGraphQLQuery"),
                    config.get("countriesShexmlMappingRules"),
                    config.get("countriesDeleteSparqlQuery"),
                    config.get("countriesConstructSparqlQuery"),
                    config,
                    sparqlStore,
                    graphQLClient
                )
            EHRITypes.INSTITUTION ->
                InstitutionsUpdatesProcessor(
                    config.get("institutionsGraphQLQuery"),
                    config.get("institutionsShexmlMappingRules"),
                    config.get("institutionsDeleteSparqlQuery"),
                    config.get("institutionsConstructSparqlQuery"),
                    config,
                    sparqlStore,
                    graphQLClient
                )
            EHRITypes.ARCHIVAL_DESCRIPTION ->
                ArchivalDescriptionsUpdatesProcessor(
                    config.get("archivalDescriptionsGraphQLQuery"),
                    config.get("archivalDescriptionsShexmlMappingRules"),
                    config.get("archivalDescriptionsDeleteSparqlQuery"),
                    config.get("archivalDescriptionsConstructSparqlQuery"),
                    config,
                    sparqlStore,
                    graphQLClient
                )
            EHRITypes.VOCABULARY ->
                VocabulariesUpdatesProcessor(
                    config.get("vocabulariesGraphQLQuery"),
                    config.get("vocabulariesShexmlMappingRules"),
                    config.get("vocabulariesDeleteSparqlQuery"),
                    config.get("vocabulariesConstructSparqlQuery"),
                    config,
                    sparqlStore,
                    graphQLClient
                )
            EHRITypes.CORPORATE_BODY ->
                HistoricalAgentsUpdatesProcessor(
                    config.get("historicalAgentsGraphQLQuery"),
                    config.get("corporateBodiesShexmlMappingRules"),
                    config.get("historicalAgentsDeleteSparqlQuery"),
                    config.get("historicalAgentsConstructSparqlQuery"),
                    config,
                    sparqlStore,
                    graphQLClient
                )
            EHRITypes.PERSON ->
                HistoricalAgentsUpdatesProcessor(
                    config.get("historicalAgentsGraphQLQuery"),
                    config.get("personsShexmlMappingRules"),
                    config.get("historicalAgentsDeleteSparqlQuery"),
                    config.get("historicalAgentsConstructSparqlQuery"),
                    config,
                    sparqlStore,
                    graphQLClient
                )
            EHRITypes.LINK ->
                LinksUpdatesProcessor(
                    config.get("linksGraphQLQuery"),
                    config.get("linksShexmlMappingRules"),
                    config.get("linksDeleteSparqlQuery"),
                    config.get("linksConstructSparqlQuery"),
                    config,
                    sparqlStore,
                    graphQLClient
                )
        }
    }
}

abstract class UpdatesProcessor(config: Config) {
    abstract val graphQLQuery: String
    abstract val shexmlMappingRules: String
    abstract val deleteSparqlQuery: String
    abstract val constructSparqlQuery: String
    abstract val sparqlStore: SparqlStore
    abstract val graphQLClient: GraphQLClient

    private val insertSparqlQuery = config.get("insertSparqlQuery")

    private val logger = KotlinLogging.logger {}

    fun fetchGraphQLData(event: EHRIEvent): String {
        val query = SourceHelper.readFile(graphQLQuery)
        val finalQuery = query.replaceFirst("<id>", event.id).replace("\n", "\\n")
        return runBlocking {
            graphQLClient.download(event, finalQuery)
        }
    }

    fun transformToRDF(graphQLResponse: String): Dataset {
        val mappingRules = SourceHelper.readFile(shexmlMappingRules)
        return ShExMLMappingLauncherProxy().convert(mappingRules, beforeMapping(graphQLResponse))
    }

    /** The response as the type's mapping rules map it, e.g. without the parts that aren't in the KG. */
    open fun beforeMapping(graphQLResponse: String): String = graphQLResponse

    fun create(newContent: Dataset): List<String> {
        logger.info { "Launching INSERT query against the SPARQL endpoint" }
        val outputStream = ByteArrayOutputStream()
        RDFDataMgr.write(outputStream, newContent.defaultModel, RDFLanguages.nameToLang("N-Triples"))
        val nTriplesNewContent = outputStream.toString("UTF-8")
        outputStream.close()
        val insertQuery = SourceHelper.readFile(insertSparqlQuery)
            .replace("<\$ntriplesNewContent>", nTriplesNewContent)
        logger.debug { "Insert query: $insertQuery" }
        sparqlStore.update(insertQuery)
        return listOf(insertQuery)
    }

    fun delete(event: EHRIEvent): List<String> {
        logger.info { "Launching DELETE query against the SPARQL endpoint" }
        val deleteQuery = replaceEntityId(event, SourceHelper.readFile(deleteSparqlQuery))
        logger.debug { "Delete query: $deleteQuery" }
        sparqlStore.update(deleteQuery)
        return listOf(deleteQuery)
    }

    fun update(event: EHRIEvent, newContent: Dataset): List<String> {
        return when(event.eventType) {
            "create-event" -> create(newContent)
            "delete-event" -> delete(event)
            "update-event" -> delete(event) + create(newContent)
            else -> error("Event ${event.eventType} not supported")
        }
    }

    fun compareGraphs(before: Model, after: Model): List<String> {
        logger.info { "Comparing graphs to generate the report" }
        val beforeList = before.listStatements().toList()
        val afterList = after.listStatements().toList()
        val difference = before.difference(after)
            .union(after.difference(before)).listStatements().toList()
            .map { Pair(it.subject, it.predicate) }.toSet()
        return difference.map { (s, p) ->
            when {
                !before.contains(s, p) && after.contains(s, p) ->
                    "Added: ${afterList.filter { it.subject == s && it.predicate == p }}"
                before.contains(s, p) && !after.contains(s, p) ->
                    "Removed: ${beforeList.filter { it.subject == s && it.predicate == p }}"
                before.contains(s, p) && after.contains(s, p) ->
                    "Modified: ${beforeList.filter { it.subject == s && it.predicate == p }} -> ${afterList.filter { it.subject == s && it.predicate == p }}"
                else -> ""
            }
        }
    }

    fun getDataStatus(event: EHRIEvent): Model {
        val query = replaceEntityId(event, SourceHelper.readFile(constructSparqlQuery))
        logger.debug { "Construct query: $query" }
        return sparqlStore.construct(query)
    }

    open fun replaceEntityId(event: EHRIEvent, fileContent: String): String {
        return fileContent.replace("<\$entityId>", event.id)
    }
}

class InstitutionsUpdatesProcessor(
    override val graphQLQuery: String,
    override val shexmlMappingRules: String,
    override val deleteSparqlQuery: String,
    override val constructSparqlQuery: String,
    config: Config,
    override val sparqlStore: SparqlStore,
    override val graphQLClient: GraphQLClient
) : UpdatesProcessor(config)

class CountriesUpdatesProcessor(
    override val graphQLQuery: String,
    override val shexmlMappingRules: String,
    override val deleteSparqlQuery: String,
    override val constructSparqlQuery: String,
    config: Config,
    override val sparqlStore: SparqlStore,
    override val graphQLClient: GraphQLClient
) : UpdatesProcessor(config)

class ArchivalDescriptionsUpdatesProcessor(
    override val graphQLQuery: String,
    override val shexmlMappingRules: String,
    override val deleteSparqlQuery: String,
    override val constructSparqlQuery: String,
    config: Config,
    override val sparqlStore: SparqlStore,
    override val graphQLClient: GraphQLClient
) : UpdatesProcessor(config) {
    private val kgSets = KgSets(config)

    override fun beforeMapping(graphQLResponse: String): String = kgSets.withAccessPointsInKg(graphQLResponse)
}

class LinksUpdatesProcessor(
    override val graphQLQuery: String,
    override val shexmlMappingRules: String,
    override val deleteSparqlQuery: String,
    override val constructSparqlQuery: String,
    config: Config,
    override val sparqlStore: SparqlStore,
    override val graphQLClient: GraphQLClient
) : UpdatesProcessor(config)

class VocabulariesUpdatesProcessor(
    override val graphQLQuery: String,
    override val shexmlMappingRules: String,
    override val deleteSparqlQuery: String,
    override val constructSparqlQuery: String,
    config: Config,
    override val sparqlStore: SparqlStore,
    override val graphQLClient: GraphQLClient
) : UpdatesProcessor(config) {
    override fun replaceEntityId(event: EHRIEvent, fileContent: String): String =
        fileContent.replace("<\$entityId>", vocabularyLocalName(event.id))
}

class HistoricalAgentsUpdatesProcessor(
    override val graphQLQuery: String,
    override val shexmlMappingRules: String,
    override val deleteSparqlQuery: String,
    override val constructSparqlQuery: String,
    config: Config,
    override val sparqlStore: SparqlStore,
    override val graphQLClient: GraphQLClient
) : UpdatesProcessor(config) {
    override fun replaceEntityId(event: EHRIEvent, fileContent: String): String =
        fileContent.replace("<\$entityId>", vocabularyLocalName(event.id))
}