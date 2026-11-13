package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.support.InMemorySparqlStore
import eu.ehri_project.ehri_kg.support.loadResource
import eu.ehri_project.ehri_kg.support.processEvents
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.jena.rdf.model.ResourceFactory.createProperty
import org.apache.jena.rdf.model.ResourceFactory.createResource
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Access points are owned by the unit: updating or deleting it updates or removes them, in both directions. */
class AccessPointsTest {

    @TempDir
    lateinit var dir: File

    val unitId = "be-000001-kd_00001"
    val unit = createResource("http://lod.ehri-project-test.eu/units/$unitId")
    val creator = createResource("http://lod.ehri-project-test.eu/vocabularies/ehri-cb/000001")
    val camp = createResource("http://lod.ehri-project-test.eu/vocabularies/ehri-camps/1")
    val term = createResource("http://lod.ehri-project-test.eu/vocabularies/ehri-terms/1")

    val hasCreator = createProperty("https://www.ica.org/standards/RiC/ontology#hasCreator")
    val isCreatorOf = createProperty("https://www.ica.org/standards/RiC/ontology#isCreatorOf")
    val hasOrHadSubject = createProperty("https://www.ica.org/standards/RiC/ontology#hasOrHadSubject")
    val isOrWasSubjectOf = createProperty("https://www.ica.org/standards/RiC/ontology#isOrWasSubjectOf")

    val original = loadResource("example/graphql/$unitId.json")

    /** The unit's GraphQL response, with some of the unit's fields replaced. */
    private fun withQuery(change: (JsonObject) -> Map<String, JsonElement>): String {
        val unit = Json.parseToJsonElement(original).jsonObject.getValue("data").jsonObject
            .getValue("documentaryUnits").jsonObject.getValue("items").jsonArray.single().jsonObject
        val items = JsonObject(mapOf("items" to JsonArray(listOf(JsonObject(unit + change(unit))))))
        return JsonObject(mapOf("data" to JsonObject(mapOf("documentaryUnits" to items)))).toString()
    }

    private fun JsonObject.subjects() = getValue("subjects").jsonArray

    /** The unit without its creator and with only its first subject. */
    val updated = withQuery { mapOf("creators" to JsonArray(emptyList()), "subjects" to JsonArray(it.subjects().take(1))) }

    /** The unit with a concept as its creator. */
    val conceptCreator = withQuery { query ->
        val concept = query.subjects().first {
            it.jsonObject.getValue("item").jsonObject.getValue("type").jsonPrimitive.content == "CvocConcept"
        }
        mapOf("creators" to JsonArray(listOf(concept)))
    }

    private fun accessPoint(id: String, type: String) =
        JsonObject(mapOf("item" to JsonObject(mapOf("id" to JsonPrimitive(id), "type" to JsonPrimitive(type)))))

    /** The unit with an extra subject that is not an agent or concept, and whose id has no '-'. */
    val countrySubject = withQuery { query ->
        mapOf("subjects" to JsonArray(query.subjects() + accessPoint("be", "Country")))
    }

    /** The unit with a creator and a subject outside EHRI's own authority sets and vocabularies. */
    val otherSets = withQuery { query ->
        mapOf(
            "creators" to JsonArray(listOf(accessPoint("terezin_victims-person_iti_995281", "HistoricalAgent"))),
            "subjects" to JsonArray(query.subjects() + accessPoint("terezin_terms-keyword_jmp_874", "CvocConcept")),
        )
    }

    private fun process(vararg events: Pair<String, String>): InMemorySparqlStore =
        processEvents(dir, unitId, "DocumentaryUnit", *events)

    @Test
    fun `a unit's access points are mapped in both directions`() {
        val kg = process("create-event" to original).dataset.defaultModel

        assertTrue(kg.contains(unit, hasCreator, creator))
        assertTrue(kg.contains(creator, isCreatorOf, unit))
        assertTrue(kg.contains(unit, hasOrHadSubject, camp))
        assertTrue(kg.contains(camp, isOrWasSubjectOf, unit))
    }

    @Test
    fun `access points removed from a unit are removed in both directions on update`() {
        val kg = process("create-event" to original, "update-event" to updated).dataset.defaultModel

        assertFalse(kg.contains(unit, hasCreator, creator))
        assertFalse(kg.contains(creator, isCreatorOf, unit))
        assertFalse(kg.contains(unit, hasOrHadSubject, camp))
        assertFalse(kg.contains(camp, isOrWasSubjectOf, unit))
        assertTrue(kg.contains(unit, hasOrHadSubject, term))
        assertTrue(kg.contains(term, isOrWasSubjectOf, unit))
    }

    @Test
    fun `a deleted unit's access points are removed, and nothing refers to it`() {
        val kg = process("create-event" to original, "delete-event" to original).dataset.defaultModel

        assertFalse(kg.contains(unit, hasCreator))
        assertFalse(kg.contains(unit, hasOrHadSubject))
        assertEquals(emptyList(), kg.listStatements(null, null, unit).toList().map { it.toString() })
    }

    @Test
    fun `only agents are mapped as creators`() {
        val kg = process("create-event" to conceptCreator).dataset.defaultModel

        assertFalse(kg.contains(unit, hasCreator))
        assertFalse(kg.listSubjectsWithProperty(isCreatorOf, unit).hasNext())
    }

    @Test
    fun `access points to other kinds of item are ignored`() {
        val kg = process("create-event" to countrySubject).dataset.defaultModel

        assertTrue(kg.contains(unit, hasOrHadSubject, camp))
        assertEquals(2, kg.listObjectsOfProperty(unit, hasOrHadSubject).toList().size)
    }

    @Test
    fun `access points to agents and concepts outside EHRI's own sets are ignored`() {
        val kg = process("create-event" to otherSets).dataset.defaultModel

        assertFalse(kg.contains(unit, hasCreator))
        assertTrue(kg.contains(unit, hasOrHadSubject, camp))
        assertEquals(2, kg.listObjectsOfProperty(unit, hasOrHadSubject).toList().size)
    }
}
