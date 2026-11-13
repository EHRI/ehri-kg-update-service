package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.shexml.ShExMLMappingLauncherProxy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.apache.jena.rdf.model.Model
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Other and parallel names are part of their nodes' IRIs. Some names in the Portal have control characters, e.g. the
 * MARC non-sorting markers U+0098 and U+009C around "von", which IRIs can't have, so they are left out of the IRIs.
 */
class NameIrisTest {

    private val marked = "Schirach, Baldur \u0098von\u009C 1907-1974"
    private val plain = "Schirach, Baldur von 1907-1974"

    /** Each mapping that puts names in IRIs, with an example response it maps. */
    private val mappings = mapOf(
        "historicalAgents/personsMappingRules.shexml" to "ehri_pers-000001.json",
        "historicalAgents/corporateBodiesMappingRules.shexml" to "ehri_cb-000001.json",
        "institutions/institutionsMappingRules.shexml" to "nl-000002.json",
        "archivalDescriptions/archivalDescriptionsMappingRules.shexml" to "be-000001-kd_00001.json",
    )

    /** The response with [name] as every other and parallel name. */
    private fun withNames(element: JsonElement, name: String): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (key == "otherFormsOfName" || key == "parallelFormsOfName") JsonArray(listOf(JsonPrimitive(name)))
            else withNames(value, name)
        })
        is JsonArray -> JsonArray(element.map { withNames(it, name) })
        else -> element
    }

    private fun map(rules: String, response: String, name: String): Model {
        val json = Json.parseToJsonElement(File("src/test/resources/example/graphql/$response").readText())
        return ShExMLMappingLauncherProxy().convert(File("conf/$rules").readText(), withNames(json, name).toString())
            .defaultModel
    }

    private fun Model.nameIris() = listSubjects().toList().map { it.uri.orEmpty() }
        .filter { it.contains("/otherNames/") || it.contains("/parallelNames/") }.sorted()

    @Test
    fun `control characters in names are left out of their IRIs`() {
        mappings.forEach { (rules, response) ->
            val withMarkers = map(rules, response, marked)
            val iris = withMarkers.nameIris()
            assertTrue(iris.isNotEmpty(), "$rules maps no names")
            assertEquals(map(rules, response, plain).nameIris(), iris, rules)
            assertTrue(iris.none { iri -> iri.any { it.isISOControl() } }, "$rules: $iris")
            // The names themselves are kept as they are
            assertTrue(withMarkers.listObjects().toList().any { it.isLiteral && it.asLiteral().string == marked }, rules)
        }
    }
}
