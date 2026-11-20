package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRIEvent
import org.apache.jena.query.DatasetFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import kotlin.test.Test
import kotlin.test.assertEquals

class EventExtractionTest {

    private val query = SourceHelper.readFile("conf/sparqlGetEventDetails.rq")

    private fun mappedEvent(eventId: String) = DatasetFactory.create().also {
        RDFParser.fromString("""
            @prefix : <http://example.com/> .
            <http://example.com/$eventId> a :Event ;
                :eventId "$eventId" ;
                :eventType "update-event" ;
                :date "2026-01-01T00:00:00Z" ;
                :hasTypedIds :1 .
            :1 a :TypedId ;
                :id "gb" ;
                :type "Country" .
        """.trimIndent()).lang(Lang.TURTLE).parse(it)
    }

    @Test
    fun `event ids are kept intact`() {
        listOf("01a0f817-c642-7fa7-a13b-4857724cbeeb", "01999999-1234-7abc-8def-0123456789ab", "evt-1").forEach { id ->
            assertEquals(
                listOf(EHRIEvent(id, "update-event", "2026-01-01T00:00:00Z", "gb", "Country")),
                EHRIUpdatesProcessor.extractEvents(mappedEvent(id), query)
            )
        }
    }
}
