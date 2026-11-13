package eu.ehri_project.ehri_kg.consumers

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class SseConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)

object SseEndpointCheck {

    // shexml-streaming reports a failed connection as the normal end of the stream, without the cause,
    // so the endpoint is checked up front to be able to report why it cannot be used
    suspend fun verify(client: HttpClient, url: String) {
        try {
            client.prepareGet(url) { accept(ContentType.Text.EventStream) }.execute { response ->
                if (!response.status.isSuccess())
                    throw SseConnectionException("Cannot connect to the SSE stream at $url: HTTP ${response.status}")
                val contentType = response.contentType()
                if (contentType == null || !contentType.match(ContentType.Text.EventStream))
                    throw SseConnectionException(
                        "Cannot connect to the SSE stream at $url: expected ${ContentType.Text.EventStream} but got $contentType")
            }
        } catch (e: SseConnectionException) {
            throw e
        } catch (e: Exception) {
            throw SseConnectionException("Cannot connect to the SSE stream at $url: ${e.message ?: e::class.simpleName}", e)
        }
    }
}
