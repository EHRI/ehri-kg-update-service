package eu.ehri_project.ehri_kg.batch

import eu.ehri_project.ehri_kg.model.EHRITypes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ItemChunkerTest {

    private val dir = createTempDirectory().toFile()

    private val units = BatchSource("documentaryUnits", EHRITypes.ARCHIVAL_DESCRIPTION, "documentaryUnits", "{}",
        listOf("data", "documentaryUnits", "items"))

    private val persons = BatchSource("ehri_pers", EHRITypes.PERSON, "historicalAgents", "{}",
        listOf("data", "AuthoritativeSet", "authorities", "items"))

    private fun response(json: String) = File(dir, "response.json").apply { writeText(json) }

    private fun ids(items: Sequence<kotlinx.serialization.json.JsonObject>) =
        items.map { it.getValue("id").jsonPrimitive.content }.toList()

    @Test
    fun `items of a list field, without nulls`() {
        val json = """{"data": {"documentaryUnits": {"items": [{"id": "a"}, null, {"id": "b", "items": [1]}, {"id": "c"}]}}}"""
        assertEquals(listOf("a", "b", "c"), ids(ItemChunker.items(response(json), units)))
    }

    @Test
    fun `items of a set, skipping the set's other fields`() {
        val json = """{"data": {"AuthoritativeSet": {"id": "ehri_pers", "items": [{"id": "no"}],
            "authorities": {"items": [{"id": "ehri_pers-1"}, {"id": "ehri_pers-2"}]}}}}"""
        assertEquals(listOf("ehri_pers-1", "ehri_pers-2"), ids(ItemChunker.items(response(json), persons)))
    }

    @Test
    fun `items a source doesn't keep are left out`() {
        val links = units.copy(keep = { it.getValue("id").jsonPrimitive.content != "b" })
        val json = """{"data": {"documentaryUnits": {"items": [{"id": "a"}, {"id": "b"}, {"id": "c"}]}}}"""
        assertEquals(listOf("a", "c"), ids(ItemChunker.items(response(json), links)))
    }

    @Test
    fun `GraphQL errors fail, even after the data`() {
        val json = """{"data": {"documentaryUnits": {"items": [{"id": "a"}]}}, "errors": [{"message": "Timed out"}]}"""
        val e = assertFailsWith<BatchResponseException> { ItemChunker.items(response(json), units).toList() }
        assertTrue(e.message!!.contains("Timed out"), e.message)
    }

    @Test
    fun `a response without the items fails`() {
        assertFailsWith<BatchResponseException> {
            ItemChunker.items(response("""{"data": {"documentaryUnits": null}}"""), units).toList()
        }
    }

    @Test
    fun `chunks are written as responses the mapping rules map`() {
        val json = """{"data": {"documentaryUnits": {"items": [{"id": "a"}, {"id": "b"}, null, {"id": "c"}]}}}"""
        val chunksDir = File(dir, "chunks")
        assertEquals(2 to 3, ItemChunker.writeChunks(response(json), units, 2, chunksDir))
        val chunks = chunksDir.listFiles()!!.sortedBy { it.name }
        assertEquals(listOf("000001.json", "000002.json"), chunks.map { it.name })
        assertEquals(listOf(listOf("a", "b"), listOf("c")), chunks.map { chunk ->
            Json.parseToJsonElement(chunk.readText()).jsonObject.getValue("data").jsonObject.getValue("documentaryUnits")
                .jsonObject.getValue("items").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        })
    }
}
