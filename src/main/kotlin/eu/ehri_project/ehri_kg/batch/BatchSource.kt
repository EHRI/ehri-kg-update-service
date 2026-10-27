package eu.ehri_project.ehri_kg.batch

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRITypes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A collection of entities of one type, fetched with a single streamed GraphQL request: a top-level list field such
 * as `documentaryUnits`, or one EHRI authority set or vocabulary.
 *
 * @property name the name of the source, used for its files
 * @property field the response field the type's mapping rules iterate, e.g. `$.data.documentaryUnits.items[*]`
 * @property body the request body, with the same item fields as the type's per-entity query
 * @property itemsPath the keys leading to the items array in the response
 * @property keep whether an item can produce data, so that others needn't be mapped
 */
data class BatchSource(
    val name: String,
    val type: EHRITypes,
    val field: String,
    val body: String,
    val itemsPath: List<String>,
    val keep: (JsonObject) -> Boolean = { true },
) {
    /** Items as a response to the type's per-entity query, which its mapping rules map. */
    fun response(items: List<JsonElement>): String =
        JsonObject(mapOf("data" to JsonObject(mapOf(field to JsonObject(mapOf("items" to JsonArray(items))))))).toString()

    companion object {
        /** The list field and its `ids` argument in a per-entity query file, e.g. `countries(ids: [\"<id>\"])`. */
        private val idsArgument = Regex("""(\w+)\(ids: \[\\"<id>\\"]\)""")

        /** The links field values the links mapping rules map. */
        private val mappedLinkFields = setOf("locationOfOriginals", "locationOfCopies")

        /** The sources of all entities of the given types, from the per-entity queries in [config]. */
        fun fromConfig(config: Config, types: Collection<EHRITypes> = EHRITypes.entries): List<BatchSource> =
            types.flatMap { type ->
                when (type) {
                    EHRITypes.COUNTRY -> listOf(all(type, config.get("countriesGraphQLQuery")))
                    EHRITypes.INSTITUTION -> listOf(all(type, config.get("institutionsGraphQLQuery")))
                    EHRITypes.ARCHIVAL_DESCRIPTION -> listOf(all(type, config.get("archivalDescriptionsGraphQLQuery")))
                    EHRITypes.LINK -> listOf(all(type, config.get("linksGraphQLQuery")) {
                        it["field"]?.jsonPrimitive?.content in mappedLinkFields
                    })
                    EHRITypes.PERSON -> sets(config, "batchPersonSets").map {
                        inSet(type, config.get("historicalAgentsGraphQLQuery"), "AuthoritativeSet", it, "authorities")
                    }
                    EHRITypes.CORPORATE_BODY -> sets(config, "batchCorporateBodySets").map {
                        inSet(type, config.get("historicalAgentsGraphQLQuery"), "AuthoritativeSet", it, "authorities")
                    }
                    EHRITypes.VOCABULARY -> sets(config, "batchVocabularySets").map {
                        inSet(type, config.get("vocabulariesGraphQLQuery"), "CvocVocabulary", it, "concepts")
                    }
                }
            }

        private fun sets(config: Config, property: String): List<String> =
            config.getOptional(property)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

        /** All the entities of a top-level list field, e.g. `{ countries { items { … } } }`. */
        fun all(type: EHRITypes, queryFile: String, keep: (JsonObject) -> Boolean = { true }): BatchSource {
            val query = SourceHelper.readFile(queryFile)
            val field = listField(query, queryFile)
            return BatchSource(field, type, field, requestBody(query.replace(idsArgument, "$1")),
                listOf("data", field, "items"), keep)
        }

        /** The entities of one set, e.g. `{ AuthoritativeSet(id: "ehri_pers") { authorities { items { … } } } }`. */
        fun inSet(type: EHRITypes, queryFile: String, setField: String, setId: String, connection: String): BatchSource {
            val query = SourceHelper.readFile(queryFile)
            val field = listField(query, queryFile)
            val inSet = query.replace(idsArgument, """$setField(id: \\"$setId\\") { $connection""")
            // Close the set's selection, before the end of the query string
            val end = inSet.lastIndexOf('"')
            return BatchSource(setId, type, field, requestBody(inSet.substring(0, end) + " }" + inSet.substring(end)),
                listOf("data", setField, connection, "items"))
        }

        private fun listField(query: String, queryFile: String): String =
            requireNotNull(idsArgument.find(query)) { "No list field with an ids argument in $queryFile" }.groupValues[1]

        /**
         * The query file as a JSON request body, as [eu.ehri_project.ehri_kg.processors.UpdatesProcessor] sends it,
         * without the escaped newline that a newline at the end of the file would leave after the JSON.
         */
        private fun requestBody(query: String): String = query.trim().replace("\n", "\\n")
    }
}
