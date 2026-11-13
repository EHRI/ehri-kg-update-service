package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.shexml.ShExMLMappingLauncherProxy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Portal answers a list query for an id that no longer exists, e.g. on a delete event, with a null item. */
class MissingEntityResponsesTest {

    private val mappings = mapOf(
        "countries/countriesMappingRules.shexml" to "countries",
        "institutions/institutionsMappingRules.shexml" to "repositories",
        "archivalDescriptions/archivalDescriptionsMappingRules.shexml" to "documentaryUnits",
        "vocabularies/vocabulariesMappingRules.shexml" to "concepts",
        "historicalAgents/corporateBodiesMappingRules.shexml" to "historicalAgents",
        "historicalAgents/personsMappingRules.shexml" to "historicalAgents",
        "links/linksMappingRules.shexml" to "links",
    )

    @Test
    fun `a missing entity is mapped to no data`() {
        val subjects = mappings.mapValues { (rules, field) ->
            ShExMLMappingLauncherProxy()
                .convert(File("conf/$rules").readText(), """{"data": {"$field": {"items": [null]}}}""")
                .defaultModel.listSubjects().toList().map { it.toString() }
        }
        assertEquals(mappings.mapValues { emptyList<String>() }, subjects)
    }
}
