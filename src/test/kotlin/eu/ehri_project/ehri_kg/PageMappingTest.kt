package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.shexml.ShExMLMappingLauncherProxy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A batch conversion maps a page of entities at once, while the update stream maps one entity at a time, and later
 * deletes and re-inserts its data. Mapping a page must give the same data as mapping its entities one by one.
 */
class PageMappingTest {

    private val graphQLDir = File("src/test/resources/example/graphql")

    /** Each mapping, with the response field it iterates and which of the field's entities it maps. */
    private data class Mapping(val rules: String, val field: String, val maps: (id: String) -> Boolean = { true })

    private val mappings = listOf(
        Mapping("countries/countriesMappingRules.shexml", "countries"),
        Mapping("institutions/institutionsMappingRules.shexml", "repositories"),
        Mapping("archivalDescriptions/archivalDescriptionsMappingRules.shexml", "documentaryUnits"),
        Mapping("vocabularies/vocabulariesMappingRules.shexml", "concepts"),
        Mapping("historicalAgents/personsMappingRules.shexml", "historicalAgents") { it.startsWith("ehri_pers") },
        Mapping("historicalAgents/corporateBodiesMappingRules.shexml", "historicalAgents") {
            it.startsWith("ehri_cb") || it.startsWith("ehri_fm")
        },
        Mapping("links/linksMappingRules.shexml", "links"),
    )

    /** The example entities of each response field, from their GraphQL responses. */
    private val entities: Map<String, List<JsonObject>> = graphQLDir.listFiles { f -> f.extension == "json" }!!
        .sortedBy { it.name }
        .map { Json.parseToJsonElement(it.readText()).jsonObject.getValue("data").jsonObject.entries.single() }
        .groupBy({ it.key }, { it.value.jsonObject.getValue("items").jsonArray.single().jsonObject })

    private fun response(field: String, items: List<JsonElement>) =
        JsonObject(mapOf("data" to JsonObject(mapOf(field to JsonObject(mapOf("items" to JsonArray(items))))))).toString()

    private fun map(mapping: Mapping, items: List<JsonElement>): Model = ShExMLMappingLauncherProxy()
        .convert(File("conf/${mapping.rules}").readText(), response(mapping.field, items)).defaultModel

    private fun Model.diff(other: Model) = difference(other).listStatements().toList().map { it.toString() }.sorted()

    @Test
    fun `mapping a page of entities gives the same data as mapping them one by one`() {
        val differences = mappings.associate { mapping ->
            val items = entities.getValue(mapping.field).filter { mapping.maps(it.getValue("id").jsonPrimitive.content) }
            val page = map(mapping, items)
            val oneByOne = items.fold(ModelFactory.createDefaultModel()) { acc, item -> acc.add(map(mapping, listOf(item))) }
            mapping.rules to mapOf("only in page" to page.diff(oneByOne), "only one by one" to oneByOne.diff(page))
                .filterValues { it.isNotEmpty() }
        }
        assertEquals(mappings.associate { it.rules to emptyMap() }, differences)
    }
}
