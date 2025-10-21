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
    val timeStamp: String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())) {
}

@Serializable
data class EHRIEvent(val id: String, val eventType: String, val datetime: String)

enum class EHRITypes {
    COUNTRY, INSTITUTION, ARCHIVAL_DESCRIPTION
}