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
import java.io.File

class EHRIUpdatesProcessor {

    val eventDetailsSparqlQuery = Config.get("eventDetailsSparqlQuery")

    private val logger = KotlinLogging.logger {}

    fun process(observable: Single<Observable<Dataset>>): Single<Observable<EHRIUpdateReport>> {
        return observable.map {
            it.map {
                getEventTypeAndId(it)?.let {
                    with(UpdatesProcessorFactory.createUpdateProcessor(EHRITypes.INSTITUTION)) {
                        try {
                            val graphQLContent = downloadContents(it)
                            val dataBefore = getDataStatus(it)
                            val turtleResult = transformToRDF(graphQLContent)
                            val executedQueries = update(it, turtleResult)
                            val dataAfter = getDataStatus(it)
                            val dataDiff = compareGraphs(dataBefore, dataAfter)
                            EHRIUpdateReport(it, executedQueries, dataDiff)
                        } catch (e: Exception) {
                            EHRIUpdateReport(it, emptyList(), emptyList(), e.toString())
                        }
                    }
                } ?: EHRIUpdateReport(EHRIEvent("", "", ""), emptyList(), emptyList())
            }
        }
    }

    private fun getEventTypeAndId(dataset: Dataset): EHRIEvent? {
        logger.info { "Extracting event information" }
        val sparqlQuery = SourceHelper.readFile(eventDetailsSparqlQuery)
        val resultSet = SparqlDatasetQueryProcessor(dataset).query(sparqlQuery)
        when(resultSet.hasNext()) {
            true -> {
                val result = resultSet.next()
                return EHRIEvent(
                    result.getLiteral("id").string,
                    result.getLiteral("eventType").string,
                    result.getLiteral("date").string
                ).also { logger.debug { "Event processed correctly" } }
            }
            false -> return null.also { logger.debug { "Empty event received or impossible to extract its information" } }
        }
    }



}

