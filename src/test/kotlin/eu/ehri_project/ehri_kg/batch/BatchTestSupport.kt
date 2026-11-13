package eu.ehri_project.ehri_kg.batch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.apache.jena.riot.RDFDataMgr
import java.io.File
import kotlin.test.assertTrue

/**
 * The example entities, from their GraphQL responses in example/graphql, as each source's streamed response would
 * return them, with a null item as for a deleted entity.
 */
fun exampleResponses(sources: List<BatchSource>): Map<BatchSource, String> {
    val entities = File("src/test/resources/example/graphql").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        .map { Json.parseToJsonElement(it.readText()).jsonObject.getValue("data").jsonObject.entries.single() }
        .map { (field, value) -> field to value.jsonObject.getValue("items").jsonArray.single().jsonObject }
    return sources.associateWith { source ->
        val items = entities.filter { (field, entity) ->
            field == source.field && (source.itemsPath.size == 3 || entity.getValue("id").toString().contains(source.name))
        }.map { it.second }
        // Wrap the items as the source's query returns them, e.g. in AuthoritativeSet.authorities
        source.itemsPath.dropLast(1).foldRight<String, JsonElement>(JsonObject(mapOf("items" to JsonArray(items + JsonNull)))) {
            key, inner -> JsonObject(mapOf(key to inner))
        }.toString()
    }
}

/** Asserts that N-Triples [output] is the example KG, which processing the example entities' events builds. */
fun assertMatchesExampleKG(output: File) {
    val example = RDFDataMgr.loadModel("src/test/resources/example/example.ttl")
    val generated = RDFDataMgr.loadModel(output.path)
    assertTrue(example.isIsomorphicWith(generated),
        "Only generated: ${generated.difference(example).listStatements().toList()}\n" +
            "Only in example: ${example.difference(generated).listStatements().toList()}")
}
