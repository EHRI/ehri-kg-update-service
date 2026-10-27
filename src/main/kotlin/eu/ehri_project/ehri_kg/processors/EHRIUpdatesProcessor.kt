package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.database.DatabaseManager
import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.model.EHRIUpdateReport
import eu.ehri_project.ehri_kg.model.NotInKgException
import eu.ehri_project.ehri_kg.model.UnsupportedEntityTypeException
import eu.ehri_project.ehri_kg.validation.TypeValidators
import io.github.oshai.kotlinlogging.KotlinLogging
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import org.apache.jena.query.Dataset

class EHRIUpdatesProcessor(
    val config: Config,
    val database: DatabaseManager,
    private val createUpdateProcessor: (EHRITypes) -> UpdatesProcessor,
    private val dryRun: Boolean = false
) {

    private val eventExtractor = EventExtractor(config.get("eventDetailsSparqlQuery"))
    private val kgSets = KgSets(config)
    val emptyEventReport = EHRIUpdateReport(EHRIEvent("", "", "", "", ""), emptyList(), emptyList())
    var previousEventErroredOrNotProcessed = false

    private val logger = KotlinLogging.logger {}

    fun process(observable: Single<Flowable<Dataset>>): Single<Flowable<EHRIUpdateReport>> {
        return observable.map {
            it.concatMap {
                Flowable.fromIterable(eventExtractor.events(it)).map {
                   processEvent(it)
                }
            }
        }
    }

    fun processEvent(event: EHRIEvent): EHRIUpdateReport {
        try {
            val type = selectEntityTypeCase(event.type, event.id)
            with(createUpdateProcessor(type)) {
                if(!previousEventErroredOrNotProcessed && database.checkIfSuccessfullyProcessed(event)) {
                    logger.info { "Skipping the event as it was already successfully processed in a previous run: $event" }
                    return emptyEventReport
                } else {
                    previousEventErroredOrNotProcessed = true
                    val graphQLContent = fetchGraphQLData(event)
                    val dataBefore = getDataStatus(event)
                    val turtleResult = transformToRDF(graphQLContent)
                    val violations = validate(type, turtleResult)
                    val executedQueries = update(event, turtleResult)
                    val dataAfter = getDataStatus(event)
                    val dataDiff = compareGraphs(dataBefore, dataAfter)
                    return EHRIUpdateReport(event, executedQueries, dataDiff, violations = violations, dryRun = dryRun)
                }
            }
        } catch (e: NotInKgException) {
            logger.info { "Ignoring event ${event.eventId}: ${e.message}" }
            return emptyEventReport
        } catch (e: UnsupportedEntityTypeException) {
            logger.debug { "Ignoring event ${event.eventId}: ${e.message}" }
            return emptyEventReport
        } catch (e: Exception) {
            return EHRIUpdateReport(event, emptyList(), emptyList(), e.stackTraceToString(), dryRun = dryRun)
        }
    }

    private val validators = TypeValidators(config)

    private fun validate(type: EHRITypes, mapped: Dataset): List<String> {
        val violations = validators.validate(type, mapped.defaultModel.graph).map { it.toString() }
        violations.forEach { logger.warn { "Mapped data does not conform to its shapes: $it" } }
        return violations
    }

    private fun selectEntityTypeCase(type: String, id: String? = "null"): EHRITypes {
        return when(type) {
            "Country" -> EHRITypes.COUNTRY
            "Repository" -> EHRITypes.INSTITUTION
            "DocumentaryUnit" -> EHRITypes.ARCHIVAL_DESCRIPTION
            "CvocConcept" -> if (kgSets.hasConcept(id.orEmpty())) EHRITypes.VOCABULARY
                else throw NotInKgException("Concept $id is not in a vocabulary in the KG (see vocabularySets)")
            "Link" -> EHRITypes.LINK
            "HistoricalAgent" -> kgSets.agentType(id.orEmpty())
                ?: throw NotInKgException("Historical Agent $id is not in an authority set in the KG (see personSets and corporateBodySets)")
            else -> throw UnsupportedEntityTypeException("Unknown or unsupported type $type")
        }
    }
}

