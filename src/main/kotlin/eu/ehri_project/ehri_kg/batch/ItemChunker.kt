package eu.ehri_project.ehri_kg.batch

import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/** A source's streamed response read in an error, e.g. a GraphQL error the backend wrote after the data. */
class BatchResponseException(message: String) : Exception(message)

/**
 * Splits a [BatchSource]'s streamed response into chunks of items, reading it as a stream so that a response of
 * gigabytes is never held in memory. Mapping a chunk gives the same data as mapping its items one by one
 * (see PageMappingTest), and mapping slows with larger chunks, so chunks of about 100 are best.
 */
object ItemChunker {

    private val logger = KotlinLogging.logger {}

    /** The source's items in [response], leaving out null items and those it doesn't [BatchSource.keep]. */
    fun items(response: File, source: BatchSource): Sequence<JsonObject> = sequence {
        JsonReader(response.bufferedReader()).use { reader ->
            var found = false
            suspend fun SequenceScope<JsonObject>.readObject(path: List<String>) {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    when {
                        name == "errors" -> throw BatchResponseException(
                            "GraphQL errors in the response for ${source.name}: ${JsonParser.parseReader(reader)}")
                        path.isNotEmpty() && name == path.first() && reader.peek() != JsonToken.NULL ->
                            if (path.size == 1) {
                                found = true
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    if (reader.peek() == JsonToken.NULL) reader.nextNull()
                                    else {
                                        val item = Json.parseToJsonElement(JsonParser.parseReader(reader).toString()).jsonObject
                                        if (source.keep(item)) yield(item)
                                    }
                                }
                                reader.endArray()
                            } else readObject(path.drop(1))
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            readObject(source.itemsPath)
            if (!found) throw BatchResponseException("No ${source.itemsPath.joinToString(".")} in the response for ${source.name}")
        }
    }

    /**
     * Writes the source's items in [response] to [dir] as numbered files of up to [size] items, each a response
     * that the type's mapping rules map. Returns the number of chunks and of items written.
     */
    fun writeChunks(response: File, source: BatchSource, size: Int, dir: File): Pair<Int, Int> {
        dir.deleteRecursively()
        dir.mkdirs()
        var items = 0
        val chunks = items(response, source).chunked(size).onEach { items += it.size }
            .mapIndexed { i, chunk -> File(dir, "%06d.json".format(i + 1)).writeText(source.response(chunk)) }
            .count()
        logger.info { "Split ${source.name} into $chunks chunks of $items items in $dir" }
        return chunks to items
    }
}
