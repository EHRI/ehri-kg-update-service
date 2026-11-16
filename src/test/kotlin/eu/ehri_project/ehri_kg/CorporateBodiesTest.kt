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

class CorporateBodiesTest : EntityTest() {

    override val updatesProcessor =
        UpdatesProcessorFactory(config, queryEndpoint, updateEndpoint)
            .createUpdateProcessor(EHRITypes.CORPORATE_BODY)
    override val getTriplesSparqlPath = "src/test/resources/historicalAgents/getAllHistoricalAgentsTriples.rq"
    override val getAllIdsSparqlPath = "src/test/resources/historicalAgents/getAllHistoricalAgentsIds.rq"
    val cb004763Data = RDFDataMgr.loadDataset("src/test/resources/historicalAgents/corporateBodies/cb004763.ttl")
    val cb004284Data = RDFDataMgr.loadDataset("src/test/resources/historicalAgents/corporateBodies/cb004284.ttl")
    val cb004284DataUpdated = RDFDataMgr.loadDataset("src/test/resources/historicalAgents/corporateBodies/cb004284Updated.ttl")
    val cb005433Data = RDFDataMgr.loadDataset("src/test/resources/historicalAgents/corporateBodies/cb005433.ttl")
    val cb004284JsonGraphQLData = SourceHelper.readFile("src/test/resources/historicalAgents/corporateBodies/cb004284JsonGraphQL.json")

    override fun doCreateTestData() {
        updatesProcessor.create(cb004763Data)
        updatesProcessor.create(cb004284Data)
    }

    override fun doCleanUp() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_cb-004763",
            "HistoricalAgent"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_cb-004284",
            "HistoricalAgent"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_cb-005433",
            "HistoricalAgent"
        ))
    }

    @Test
    @DisplayName("Deletion of one corporate body is satisfactory")
    fun testDeletion() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_cb-004763",
            "HistoricalAgent"
        ))

        Assertions.assertTrue { retrieveAllEntityIds().size == 1 }

        val cb004763PersistedData = retrieveEntityTriples("ehri-cb\\/004763")
        val cb004763DataStatements = cb004763Data.defaultModel.listStatements().toList()

        //These should have been deleted
        assertStatementsNotExist(cb004763DataStatements, cb004763PersistedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#isOrWasSubjectOf",
            "https://www.ica.org/standards/RiC/ontology#isCreatorOf",
            "https://www.ica.org/standards/RiC/ontology#hasOrHadSubject",
            "https://www.ica.org/standards/RiC/ontology#hasCreator"
        ))

        //These should have been preserved
        assertStatementsExist(cb004763DataStatements, cb004763PersistedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#isOrWasSubjectOf",
            "https://www.ica.org/standards/RiC/ontology#isCreatorOf",
            "https://www.ica.org/standards/RiC/ontology#hasOrHadSubject",
            "https://www.ica.org/standards/RiC/ontology#hasCreator"
        ))
    }

    @Test
    @DisplayName("Update of one corporate body is satisfactory")
    fun testUpdate() {
        doTestUpdate(cb004284DataUpdated)
    }

    @Test
    @DisplayName("Update of one corporate body with JSON data is satisfactory")
    fun testUpdateWithGraphQLData() {
        val updatedData = updatesProcessor.transformToRDF(cb004284JsonGraphQLData)
        doTestUpdate(updatedData)
    }

    private fun doTestUpdate(data: Dataset) {
        assertNotStartingWith(retrieveEntityTriples("ehri-cb\\/004284"), listOf(
            "https://www.ica.org/standards/RiC/ontology#history"
        ), "[Test Update]")

        updatesProcessor.update(EHRIEvent(
            "dummy",
            "update-event",
            DateTimeUtils.nowAsString(),
            "ehri_cb-004284",
            "HistoricalAgent"
        ), data)

        val cb004284PersistedUpdatedData = retrieveEntityTriples("ehri-cb\\/004284")
        val cb004284DataStatements = cb004284DataUpdated.defaultModel.listStatements().toList()

        assertStatementsExcluding(cb004284DataStatements, cb004284PersistedUpdatedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#history"
        ))

        assertStartingWith(cb004284PersistedUpdatedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#history"
        ), "[Test Update]")

        Assertions.assertTrue { retrieveAllEntityIds().size == 2 }
    }

    @Test
    @DisplayName("Creation of a new corporate body is satisfactory")
    fun testCreation() {
        updatesProcessor.create(cb005433Data)

        Assertions.assertTrue { retrieveAllEntityIds().size == 3 }

        val cb005433PersistedData = retrieveEntityTriples("ehri-cb\\/005433")
        val cb005433DataStatements = cb005433Data.defaultModel.listStatements().toList()

        //Everything should be identical
        assertStatementsExist(cb005433DataStatements, cb005433PersistedData)

        Assertions.assertTrue { cb005433DataStatements.size == cb005433PersistedData.size }
    }

}
