package eu.ehri_project.ehri_kg.graphql

import eu.ehri_project.ehri_kg.model.EHRIEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

fun interface GraphQLClient {
    suspend fun download(event: EHRIEvent, query: String): String
}

class GraphQLQueryProcessor(val endpoint: String, private val client: HttpClient) : GraphQLClient {

    private val logger = KotlinLogging.logger {}

    override suspend fun download(event: EHRIEvent, query: String): String {
        logger.info { "Downloading data from the GraphQL endpoint for the entity with id ${event.id}" }
        logger.debug { "POST $endpoint: $query" }
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(query)
        }
        logger.debug { "GraphQL response: ${response.status}" }
        return response.body<String>()
    }
}
