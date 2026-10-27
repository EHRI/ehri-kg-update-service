package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.sparql.SparqlDatasetQueryProcessor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.jena.query.Dataset

/** Extracts the events from an SSE message mapped by the SSE mapping rules, with the event details query. */
class EventExtractor(private val eventDetailsSparqlQuery: String) {

    private val logger = KotlinLogging.logger {}

    init {
        org.apache.jena.query.ARQ.init()
    }

    fun events(dataset: Dataset): List<EHRIEvent> {
        logger.info { "Extracting event information" }
        val sparqlQuery = SourceHelper.readFile(eventDetailsSparqlQuery)
        val resultSet = SparqlDatasetQueryProcessor(dataset).query(sparqlQuery)
        val events = resultSet.asSequence().toList().map {
            EHRIEvent(
                it.getLiteral("eventId").string,
                it.getLiteral("eventType").string,
                it.getLiteral("date").string,
                it.getLiteral("ids").string,
                it.getLiteral("types").string,
            )
        }
        if (events.isEmpty()) {
            if (dataset.defaultModel.isEmpty) logger.debug { "Empty event received" }
            else logger.warn {
                "Event data received but no event could be extracted from it; " +
                    "check that the SSE mapping rules match $eventDetailsSparqlQuery"
            }
        }
        return events
    }
}
