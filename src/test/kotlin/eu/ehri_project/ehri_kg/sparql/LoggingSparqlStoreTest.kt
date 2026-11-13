package eu.ehri_project.ehri_kg.sparql

import org.apache.jena.update.UpdateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoggingSparqlStoreTest {

    private val insert = """
        PREFIX ex: <http://example.com/>
        INSERT DATA { ex:s ex:p "o" . }
    """.trimIndent()
    private val delete = """
        PREFIX ex: <http://example.com/>
        DELETE { ?s ?p ?o } WHERE { ?s ?p ?o . FILTER(?s = ex:s) }
    """.trimIndent()

    @Test
    fun `updates are logged`() {
        val logged = mutableListOf<String>()

        LoggingSparqlStore(logged::add).update(insert)

        assertEquals(listOf(LoggingSparqlStore.formatStatement(insert)), logged)
    }

    @Test
    fun `constructs return an empty model`() {
        val result = LoggingSparqlStore {}.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }")

        assertTrue(result.isEmpty)
    }

    @Test
    fun `logged statements form a valid update request`() {
        val logged = mutableListOf<String>()
        val store = LoggingSparqlStore(logged::add)
        store.update(delete)
        store.update(insert)

        val request = UpdateFactory.create(logged.joinToString("\n"))

        assertEquals(2, request.operations.size)
    }
}
