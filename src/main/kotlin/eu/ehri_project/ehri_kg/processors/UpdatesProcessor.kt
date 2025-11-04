package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.graphql.GraphQLQueryProcessor
import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.shexml.ShExMLMappingLauncherProxy
import eu.ehri_project.ehri_kg.sparql.SparqlEndpointQueryProcessor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
import java.io.ByteArrayOutputStream

class UpdatesProcessorFactory(val config: Config) {

    private val logger = KotlinLogging.logger {}

    fun createUpdateProcessor(type: EHRITypes): UpdatesProcessor {
        logger.info { "Detected entity type $type" }
        return when(type) {
            EHRITypes.COUNTRY ->
                InstitutionsUpdatesProcessor(
                    config.get("countriesGraphQLQuery"),
                    config.get("countriesShexmlMappingRules"),
                    config.get("countriesDeleteSparqlQuery"),
                    config.get("countriesConstructSparqlQuery"),
                    config
                )
            EHRITypes.INSTITUTION ->
                CountriesUpdatesProcessor(
                    config.get("institutionsGraphQLQuery"),
                    config.get("institutionsShexmlMappingRules"),
                    config.get("institutionsDeleteSparqlQuery"),
                    config.get("institutionsConstructSparqlQuery"),
                    config
                )
            EHRITypes.ARCHIVAL_DESCRIPTION ->
                ArchivalDescriptionsUpdatesProcessor(
                    config.get("archivalDescriptionsGraphQLQuery"),
                    config.get("archivalDescriptionsShexmlMappingRules"),
                    config.get("archivalDescriptionsDeleteSparqlQuery"),
                    config.get("archivalDescriptionsConstructSparqlQuery"),
                    config
                )
        }
    }

}

abstract class UpdatesProcessor(config: Config) {
    abstract val graphQLQuery: String
    abstract val shexmlMappingRules: String
    abstract val deleteSparqlQuery: String
    abstract val constructSparqlQuery: String

    val graphQLEndpoint = config.get("graphQLEndpoint")
    val querySparqlEndpoint = config.get("querySparqlEndpoint")
    val updateSparqlEndpoint = config.get("updateSparqlEndpoint")
    val insertSparqlQuery = config.get("insertSparqlQuery")

    private val logger = KotlinLogging.logger {}

    fun downloadContents(event: EHRIEvent): String {
        val query = SourceHelper.readFile(graphQLQuery)
        val finalQuery = query.replaceFirst("<id>", event.id).replace("\n", "\\n")
        return runBlocking {
            GraphQLQueryProcessor(graphQLEndpoint).download(event, finalQuery)
        }
    }

    fun transformToRDF(graphQLResponse: String): Dataset {
        val mappingRules = SourceHelper.readFile(shexmlMappingRules)
        return ShExMLMappingLauncherProxy().convert(mappingRules, graphQLResponse)
    }

    fun create(newContent: Dataset): List<String> {
        logger.info { "Launching INSERT query against the SPARQL endpoint" }
        val outputStream = ByteArrayOutputStream()
        RDFDataMgr.write(outputStream, newContent.defaultModel, RDFLanguages.nameToLang("N-Triples"))
        val nTriplesNewContent = outputStream.toString("UTF-8")
        outputStream.close()
        val insertQuery = SourceHelper.readFile(insertSparqlQuery)
            .replace("<\$ntriplesNewContent>", nTriplesNewContent)
        logger.debug { "Insert query: $insertQuery" }
        SparqlEndpointQueryProcessor(updateSparqlEndpoint).update(insertQuery)
        return listOf(insertQuery)
    }

    fun delete(event: EHRIEvent): List<String> {
        logger.info { "Launching DELETE query against the SPARQL endpoint" }
        val deleteQuery = SourceHelper.readFile(deleteSparqlQuery)
            .replace("<\$entityId>", event.id)
        logger.debug { "Delete query: $deleteQuery" }
        SparqlEndpointQueryProcessor(updateSparqlEndpoint).update(deleteQuery)
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
        val query = SourceHelper.readFile(constructSparqlQuery)
            .replace("<\$entityId>", event.id)
        return SparqlEndpointQueryProcessor(querySparqlEndpoint).construct(query)
    }
}

class InstitutionsUpdatesProcessor(
    override val graphQLQuery: String,
    override val shexmlMappingRules: String,
    override val deleteSparqlQuery: String,
    override val constructSparqlQuery: String,
    config: Config
) : UpdatesProcessor(config)

class CountriesUpdatesProcessor(
    override val graphQLQuery: String,
    override val shexmlMappingRules: String,
    override val deleteSparqlQuery: String,
    override val constructSparqlQuery: String,
    config: Config
) : UpdatesProcessor(config)

class ArchivalDescriptionsUpdatesProcessor(
    override val graphQLQuery: String,
    override val shexmlMappingRules: String,
    override val deleteSparqlQuery: String,
    override val constructSparqlQuery: String,
    config: Config
) : UpdatesProcessor(config)