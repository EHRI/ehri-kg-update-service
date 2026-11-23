package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.graphql.GraphQLQueryProcessor
import eu.ehri_project.ehri_kg.graphql.GraphQLResponseException
import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRIEventTypes
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.processors.UpdatesProcessorFactory
import org.junit.jupiter.api.DisplayName
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GraphQLResponseTest {

    private val event = EHRIEvent("dummy", EHRIEventTypes.UPDATE, "2026-01-01T00:00:00Z", "gb-003348-wl3000_9_1-1", "DocumentaryUnit")

    @Test
    @DisplayName("Responses with data for the entity are accepted")
    fun testValidResponses() {
        listOf(
            "src/test/resources/archivalDescriptions/wienerLibraryCollectionJsonGraphQL.json",
            "src/test/resources/countries/ukJsonGraphQL.json",
            "src/test/resources/institutions/wienerLibraryJsonGraphQL.json",
            "src/test/resources/historicalAgents/persons/person001774JsonGraphQL.json",
            "src/test/resources/vocabularies/terms1062JsonGraphQL.json",
            "src/test/resources/links/link2JsonGraphQL.json",
        ).forEach { GraphQLQueryProcessor.checkResponse(event, SourceHelper.readFile(it)) }
    }

    @Test
    @DisplayName("Responses with errors or without data for the entity are rejected")
    fun testInvalidResponses() {
        listOf(
            """{"data": {"query": null}}""",
            """{"data": null, "errors": [{"message": "Something went wrong"}]}""",
            """{"data": {"query": {"id": "x"}}, "errors": [{"message": "Partly wrong"}]}""",
            """{"data": {}}""",
            """{}""",
            "<html><body>Bad gateway</body></html>",
            "",
        ).forEach { body ->
            assertFailsWith<GraphQLResponseException>(body) { GraphQLQueryProcessor.checkResponse(event, body) }
        }
    }

    @Test
    @DisplayName("A bad formatted query raises the expected error")
    fun testMissingInvalidQuery() {
        val processor = UpdatesProcessorFactory(Config("conf/config.properties"), "", "")
            .createUpdateProcessor(EHRITypes.ARCHIVAL_DESCRIPTION)
        assertFailsWith<GraphQLResponseException> {
            processor.downloadContents(event.copy(id = "no-such\"-unit"))
        }
    }
}
