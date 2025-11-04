package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.model.EHRIUpdateReport
import eu.ehri_project.ehri_kg.sparql.SparqlDatasetQueryProcessor
import io.github.oshai.kotlinlogging.KotlinLogging
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.apache.jena.query.Dataset

class EHRIUpdatesProcessor(val config: Config) {

    val eventDetailsSparqlQuery = config.get("eventDetailsSparqlQuery")

    init {
        org.apache.jena.query.ARQ.init()
    }

    private val logger = KotlinLogging.logger {}

    fun process(observable: Single<Observable<Dataset>>): Single<Observable<List<EHRIUpdateReport>>> {
        return observable.map {
            it.map {
                getEventTypeAndId(it).map {
                    try {
                        with(UpdatesProcessorFactory(config).createUpdateProcessor(selectEntityTypeCase(it.type))) {
                            val graphQLContent = downloadContents(it)
                            val dataBefore = getDataStatus(it)
                            val turtleResult = transformToRDF(graphQLContent)
                            val executedQueries = update(it, turtleResult)
                            val dataAfter = getDataStatus(it)
                            val dataDiff = compareGraphs(dataBefore, dataAfter)
                            EHRIUpdateReport(it, executedQueries, dataDiff)
                        }
                    } catch (e: Exception) {
                        EHRIUpdateReport(it, emptyList(), emptyList(), e.stackTraceToString())
                    }
                }.ifEmpty { listOf(EHRIUpdateReport(EHRIEvent("", "", "", "", ""), emptyList(), emptyList())) }
            }
        }
    }

    private fun selectEntityTypeCase(type: String): EHRITypes {
        return when(type) {
            "Country" -> EHRITypes.COUNTRY
            "Repository" -> EHRITypes.INSTITUTION
            "DocumentaryUnit" -> EHRITypes.ARCHIVAL_DESCRIPTION
            else -> error("Unknown or unsupported type $type")
        }
    }

    private fun getEventTypeAndId(dataset: Dataset): List<EHRIEvent> {
        logger.info { "Extracting event information" }
        val sparqlQuery = SourceHelper.readFile(eventDetailsSparqlQuery)
        val resultSet = SparqlDatasetQueryProcessor(dataset).query(sparqlQuery)
        val events = resultSet.asSequence().toList().map {
            EHRIEvent(
                it.getResource("eventId").localName,
                it.getLiteral("eventType").string,
                it.getLiteral("date").string,
                it.getLiteral("ids").string,
                it.getLiteral("types").string,
            )
        }
        return events
            .ifEmpty { emptyList<EHRIEvent>().also { logger.debug { "Empty event received or impossible to extract its information" } } }
    }
}

