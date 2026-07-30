package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.processors.UpdatesProcessorFactory
import org.apache.jena.atlas.lib.DateTimeUtils
import org.apache.jena.query.Dataset
import org.apache.jena.riot.RDFDataMgr
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import kotlin.test.Test

class LinksTest : EntityTest() {

    override val updatesProcessor =
        UpdatesProcessorFactory(config, queryEndpoint, updateEndpoint)
            .createUpdateProcessor(EHRITypes.LINK)
    override val getTriplesSparqlPath = "src/test/resources/links/getAllLinksTriples.rq"
    override val getAllIdsSparqlPath = "src/test/resources/links/getAllLinksIds.rq"
    val link1Data = RDFDataMgr.loadDataset("src/test/resources/links/link1.ttl")
    val link2Data = RDFDataMgr.loadDataset("src/test/resources/links/link2.ttl")
    val link2DataUpdated = RDFDataMgr.loadDataset("src/test/resources/links/link2Updated.ttl")
    val link3Data = RDFDataMgr.loadDataset("src/test/resources/links/link3.ttl")
    val link2JsonGraphQLData = SourceHelper.readFile("src/test/resources/links/link2JsonGraphQL.json")

    override fun doCreateTestData() {
        updatesProcessor.create(link1Data)
        updatesProcessor.create(link2Data)
    }

    override fun doCleanUp() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "971c8661-f345-11e5-82d8-e94fedf8eea5",
            "Link"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "9f57326c-4bbb-11e9-aff1-03b6fe2bb563",
            "Link"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "2f400ce1-4b33-11e5-bf02-a756dc08e138",
            "Link"
        ))
    }

    @Test
    @DisplayName("Deletion of one link is satisfactory")
    fun testDeletion() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "971c8661-f345-11e5-82d8-e94fedf8eea5",
            "Link"
        ))

        Assertions.assertTrue { retrieveAllEntityIds().size == 1 }

        val link1PersistedData = retrieveEntityTriples("971c8661-f345-11e5-82d8-e94fedf8eea5")

        // Nothing should have been preserved in this case
        Assertions.assertTrue { link1PersistedData.isEmpty() }
    }

    @Test
    @DisplayName("Update of one link is satisfactory")
    fun testUpdate() {
        doTestUpdate(link2DataUpdated)
    }

    @Test
    @DisplayName("Update of one link with JSON data is satisfactory")
    fun testUpdateWithGraphQLData() {
        val updatedData = updatesProcessor.transformToRDF(link2JsonGraphQLData)
        doTestUpdate(updatedData)
    }

    private fun doTestUpdate(data: Dataset) {
        updatesProcessor.update(EHRIEvent(
            "dummy",
            "update-event",
            DateTimeUtils.nowAsString(),
            "9f57326c-4bbb-11e9-aff1-03b6fe2bb563",
            "Link"
        ), data)

        val link2PersistedUpdatedData = retrieveEntityTriples("9f57326c-4bbb-11e9-aff1-03b6fe2bb563")
        val link2DataStatements = link2Data.defaultModel.listStatements().toList()

        assertStatementsExcluding(link2DataStatements, link2PersistedUpdatedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#generalDescription"
        ))

        assertStartingWith(link2PersistedUpdatedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#generalDescription"
        ), "[Updated data]")

        Assertions.assertTrue { retrieveAllEntityIds().size == 2 }
    }

    @Test
    @DisplayName("Creation of a new link is satisfactory")
    fun testCreation() {
        Assertions.assertTrue { retrieveAllEntityIds().size == 2 }

        updatesProcessor.create(link3Data)

        Assertions.assertTrue { retrieveAllEntityIds().size == 3 }

        val link3PersistedData = retrieveEntityTriples("2f400ce1-4b33-11e5-bf02-a756dc08e138")
        val link3DataStatements = link3Data.defaultModel.listStatements().toList()

        //Everything should be identical
        assertStatementsExist(link3DataStatements, link3PersistedData)

        Assertions.assertTrue { link3DataStatements.size == link3PersistedData.size }
    }

}
