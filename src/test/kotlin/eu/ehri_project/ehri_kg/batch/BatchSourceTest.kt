package eu.ehri_project.ehri_kg.batch

import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.shexml.ShExMLMappingLauncherProxy
import eu.ehri_project.ehri_kg.support.loadResource
import eu.ehri_project.ehri_kg.support.testConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatchSourceTest {

    private val config = testConfig(createTempDirectory().toFile())
    private val sources = BatchSource.fromConfig(config)

    private fun BatchSource.query() = Json.parseToJsonElement(body).jsonObject.getValue("query").jsonPrimitive.content

    /** The item selection of a query: everything from its `items` field on. */
    private fun itemSelection(query: String) = query.substring(query.indexOf("items {")).trimEnd('}', ' ', '\n')

    @Test
    fun `a source for each list field and configured set`() {
        assertEquals(
            listOf("countries", "repositories", "documentaryUnits", "ehri_terms", "ehri_camps", "ehri_ghettos",
                "ehri_cb", "ehri_fm", "ehri_pers", "links"),
            sources.map { it.name })
        assertEquals(listOf("ehri_pers"), sources.filter { it.type == EHRITypes.PERSON }.map { it.name })
    }

    @Test
    fun `list field queries fetch all items, with the per-entity query's fields`() {
        val units = sources.single { it.name == "documentaryUnits" }
        assertEquals(listOf("data", "documentaryUnits", "items"), units.itemsPath)
        assertTrue(units.query().trimStart().startsWith("{\n  documentaryUnits {"), units.query())
        val perEntity = File(config.get("archivalDescriptionsGraphQLQuery")).readText()
            .let { Json.parseToJsonElement(it.replace("\n", "\\n")).jsonObject.getValue("query").jsonPrimitive.content }
        assertEquals(itemSelection(perEntity), itemSelection(units.query()))
    }

    @Test
    fun `set queries fetch the set's items, as the field the mapping iterates`() {
        val persons = sources.single { it.name == "ehri_pers" }
        assertEquals("historicalAgents", persons.field)
        assertEquals(listOf("data", "AuthoritativeSet", "authorities", "items"), persons.itemsPath)
        assertTrue(persons.query().contains("""AuthoritativeSet(id: "ehri_pers") { authorities {"""), persons.query())
        val camps = sources.single { it.name == "ehri_camps" }
        assertEquals("concepts", camps.field)
        assertTrue(camps.query().contains("""CvocVocabulary(id: "ehri_camps") { concepts {"""), camps.query())
    }

    @Test
    fun `every query is valid JSON with balanced braces and no ids argument`() {
        sources.forEach { source ->
            val query = source.query()
            assertEquals(query.count { it == '{' }, query.count { it == '}' }, "${source.name}: $query")
            assertFalse(query.contains("ids:"), "${source.name}: $query")
        }
    }

    @Test
    fun `links that are left out map to no data`() {
        val links = sources.single { it.name == "links" }
        val rules = File(config.get("linksShexmlMappingRules")).readText()
        val examples = listOf("00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002").map {
            Json.parseToJsonElement(loadResource("example/graphql/$it.json")).jsonObject.getValue("data").jsonObject
                .getValue("links").jsonObject.getValue("items").jsonArray.single().jsonObject
        }
        val (kept, leftOut) = examples.partition(links.keep)
        assertEquals(1, kept.size)
        leftOut.forEach {
            assertTrue(ShExMLMappingLauncherProxy().convert(rules, links.response(listOf(it))).defaultModel.isEmpty, "$it")
        }
    }
}
