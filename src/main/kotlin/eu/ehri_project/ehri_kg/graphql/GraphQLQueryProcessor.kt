package eu.ehri_project.ehri_kg.graphql

import eu.ehri_project.ehri_kg.model.EHRIEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class GraphQLQueryProcessor(val endpoint: String) {

    private val logger = KotlinLogging.logger {}

    suspend fun download(event: EHRIEvent, query: String): String {
        logger.info { "Downloading data from the GraphQL endpoint for the entity with id ${event.id}" }
        val client = HttpClient()
        return client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(query)
        }.body<String>()
    }
}