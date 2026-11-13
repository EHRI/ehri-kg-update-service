package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.model.EHRITypes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The authority sets and vocabularies whose historical agents and concepts are in the KG, by the mapping rules they
 * use, from the personSets, corporateBodySets and vocabularySets properties. The agents and concepts of other sets,
 * e.g. terezin_terms, are left out: the update stream ignores them, the batch conversion doesn't convert them, and
 * units' access points to them aren't mapped.
 */
class KgSets(val personSets: List<String>, val corporateBodySets: List<String>, val vocabularySets: List<String>) {

    constructor(config: Config) : this(config.getList("personSets"), config.getList("corporateBodySets"),
        config.getList("vocabularySets"))

    /** The type a historical agent is mapped as, by its set, or null if its set isn't in the KG. */
    fun agentType(id: String): EHRITypes? = when (setIdOf(id)) {
        in personSets -> EHRITypes.PERSON
        in corporateBodySets -> EHRITypes.CORPORATE_BODY
        else -> null
    }

    fun hasConcept(id: String): Boolean = setIdOf(id) in vocabularySets

    /** Whether an item, by its Portal type and id, is a historical agent or concept in the KG. */
    fun contains(type: String, id: String): Boolean = when (type) {
        "HistoricalAgent" -> agentType(id) != null
        "CvocConcept" -> hasConcept(id)
        else -> false
    }

    /**
     * A GraphQL response for units, `{"data": {<field>: {"items": […]}}}`, without the units' creators and subjects
     * that aren't agents or concepts in the KG, so that the mapping rules don't link to them.
     */
    fun withAccessPointsInKg(response: String): String {
        val json = Json.parseToJsonElement(response).jsonObject
        val data = json["data"] as? JsonObject ?: return response
        return JsonObject(json + ("data" to JsonObject(data.mapValues { (_, connection) ->
            val items = (connection as? JsonObject)?.get("items") as? JsonArray ?: return@mapValues connection
            JsonObject(connection + ("items" to JsonArray(items.map(::withAccessPointsInKg))))
        }))).toString()
    }

    private fun withAccessPointsInKg(unit: JsonElement): JsonElement {
        if (unit !is JsonObject) return unit
        return JsonObject(unit.mapValues { (key, value) ->
            if (key !in ACCESS_POINT_FIELDS || value !is JsonArray) value
            else JsonArray(value.filter { accessPoint ->
                val item = (accessPoint as? JsonObject)?.get("item") as? JsonObject
                val type = item?.get("type")?.jsonPrimitive?.content
                val id = item?.get("id")?.jsonPrimitive?.content
                type != null && id != null && contains(type, id)
            })
        })
    }

    companion object {
        /** The fields of a unit's access points to other items, which the mapping rules link to. */
        private val ACCESS_POINT_FIELDS = setOf("creators", "subjects")
    }
}
