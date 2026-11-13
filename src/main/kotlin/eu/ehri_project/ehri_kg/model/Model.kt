package eu.ehri_project.ehri_kg.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter

@Serializable
data class EHRIUpdateReport(
    val receivedEvent: EHRIEvent,
    val executedQueries: List<String>,
    val rdfDiff: List<String>,
    val errors: String = "",
    val timeStamp: String = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
    val dryRun: Boolean = false,
    val violations: List<String> = emptyList())

@Serializable
data class EHRIEvent(
    val eventId: String,
    val eventType: String,
    val datetime: String,
    val id: String,
    val type: String)

open class UnsupportedEntityTypeException(message: String) : Exception(message)

/** An event for a historical agent or concept whose set isn't configured to be in the KG. */
class NotInKgException(message: String) : UnsupportedEntityTypeException(message)

enum class EHRITypes {
    COUNTRY, INSTITUTION, ARCHIVAL_DESCRIPTION, VOCABULARY, CORPORATE_BODY, PERSON, LINK
}