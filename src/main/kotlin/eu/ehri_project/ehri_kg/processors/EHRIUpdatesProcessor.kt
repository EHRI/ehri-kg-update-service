package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.model.EHRIUpdateReport
import eu.ehri_project.ehri_kg.sparql.SparqlDatasetQueryProcessor
import io.github.oshai.kotlinlogging.KotlinLogging
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import org.apache.jena.query.Dataset

class EHRIUpdatesProcessor(val config: Config) {

    val eventDetailsSparqlQuery = config.get("eventDetailsSparqlQuery")

    init {
        org.apache.jena.query.ARQ.init()
    }

    private val logger = KotlinLogging.logger {}

    fun process(observable: Single<Flowable<Dataset>>): Single<Flowable<EHRIUpdateReport>> {
        return observable.map {
            it.concatMap {
                Flowable.fromIterable(getEventTypeAndId(it)).map {
                   processEvent(it)
                }.defaultIfEmpty(EHRIUpdateReport(EHRIEvent("", "", "", "", ""), emptyList(), emptyList()))
            }
        }
    }

    fun processEvent(event: EHRIEvent): EHRIUpdateReport {
        try {
            with(UpdatesProcessorFactory(config).createUpdateProcessor(selectEntityTypeCase(event.type, event.id))) {
                val graphQLContent = downloadContents(event)
                val dataBefore = getDataStatus(event)
                val turtleResult = transformToRDF(graphQLContent)
                val executedQueries = update(event, turtleResult)
                val dataAfter = getDataStatus(event)
                val dataDiff = compareGraphs(dataBefore, dataAfter)
                return EHRIUpdateReport(event, executedQueries, dataDiff)
            }
        } catch (e: Exception) {
            return EHRIUpdateReport(event, emptyList(), emptyList(), e.stackTraceToString())
        }
    }

    private fun selectEntityTypeCase(type: String, id: String? = "null"): EHRITypes {
        return when(type) {
            "Country" -> EHRITypes.COUNTRY
            "Repository" -> EHRITypes.INSTITUTION
            "DocumentaryUnit" -> EHRITypes.ARCHIVAL_DESCRIPTION
            "CvocConcept" -> EHRITypes.VOCABULARY
            "Link" -> EHRITypes.LINK
            "HistoricalAgent" -> id?.let {
                if(it.startsWith("ehri_cb")) EHRITypes.CORPORATE_BODY
                else if(id.startsWith("ehri_pers")) EHRITypes.PERSON
                else null
            } ?: error("Unknown or unsupported Historical Agent type for id $id")
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

