package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.support.FakeGraphQLClient
import eu.ehri_project.ehri_kg.support.InMemorySparqlStore
import eu.ehri_project.ehri_kg.sparql.RemoteSparqlStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class UpdatesProcessorFactoryTest {

    private val config = Config("conf/config.properties")

    private data class Expected(val graphQL: String, val mapping: String, val delete: String, val construct: String)

    private fun expected(prefix: String, mappingKey: String = "${prefix}ShexmlMappingRules") = Expected(
        config.get("${prefix}GraphQLQuery"),
        config.get(mappingKey),
        config.get("${prefix}DeleteSparqlQuery"),
        config.get("${prefix}ConstructSparqlQuery"),
    )

    @Test
    fun `each entity type is wired to its own configuration files`() {
        val factory = UpdatesProcessorFactory(config, FakeGraphQLClient { "" }, sparqlStore = InMemorySparqlStore())
        val expectations = mapOf(
            EHRITypes.COUNTRY to expected("countries"),
            EHRITypes.INSTITUTION to expected("institutions"),
            EHRITypes.ARCHIVAL_DESCRIPTION to expected("archivalDescriptions"),
            EHRITypes.VOCABULARY to expected("vocabularies"),
            EHRITypes.CORPORATE_BODY to expected("historicalAgents", "corporateBodiesShexmlMappingRules"),
            EHRITypes.PERSON to expected("historicalAgents", "personsShexmlMappingRules"),
            EHRITypes.LINK to expected("links"),
        )
        assertEquals(EHRITypes.entries.toSet(), expectations.keys)

        expectations.forEach { (type, files) ->
            val processor = factory.createUpdateProcessor(type)
            assertEquals(files, Expected(
                processor.graphQLQuery,
                processor.shexmlMappingRules,
                processor.deleteSparqlQuery,
                processor.constructSparqlQuery
            ), "Wrong configuration for $type")
        }
    }

    @Test
    fun `processors share the injected store and GraphQL client`() {
        val store = InMemorySparqlStore()
        val client = FakeGraphQLClient { "" }
        val processor = UpdatesProcessorFactory(config, client, sparqlStore = store)
            .createUpdateProcessor(EHRITypes.LINK)

        assertSame(store, processor.sparqlStore)
        assertSame(client, processor.graphQLClient)
    }

    @Test
    fun `defaults to a remote store on the given endpoints`() {
        val processor = UpdatesProcessorFactory(config, FakeGraphQLClient { "" }, "http://q.example/query", "http://u.example/update")
            .createUpdateProcessor(EHRITypes.COUNTRY)

        val store = assertIs<RemoteSparqlStore>(processor.sparqlStore)
        assertEquals("http://q.example/query", store.queryEndpoint)
        assertEquals("http://u.example/update", store.updateEndpoint)
    }
}
