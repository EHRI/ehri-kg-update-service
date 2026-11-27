package eu.ehri_project.ehri_kg.graphql

import eu.ehri_project.ehri_kg.model.EHRIEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class GraphQLResponseException(message: String) : Exception(message)

class GraphQLQueryProcessor(val endpoint: String, private val client: HttpClient) {

    private val logger = KotlinLogging.logger {}

    suspend fun download(event: EHRIEvent, query: String): String {
        logger.info { "Downloading data from the GraphQL endpoint for the entity with id ${event.id}" }
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(query)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw GraphQLResponseException("GraphQL request for ${event.id} failed with status ${response.status}: $body")
        }
        checkResponse(event, body)
        return body
    }

    companion object {
        /** Throws if a response has errors or no data for the entity, rather than letting it be mapped to nothing. */
        fun checkResponse(event: EHRIEvent, body: String) {
            val response = try {
                Json.parseToJsonElement(body).jsonObject
            } catch (e: Exception) {
                throw GraphQLResponseException("GraphQL response for ${event.id} isn't a JSON object: $body")
            }
            val errors = response["errors"]
            if (errors != null && errors != JsonNull) {
                throw GraphQLResponseException("GraphQL response for ${event.id} has errors: $errors")
            }
            val entity = (response["data"] as? JsonObject)?.entries
            if (entity.isNullOrEmpty() || entity.all { it.value is JsonNull}) {
                throw GraphQLResponseException("GraphQL response for ${event.id} has no data for it: $body")
            }
        }
    }
}
