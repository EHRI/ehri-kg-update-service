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

class PersonsTest : EntityTest() {

    override val updatesProcessor =
        UpdatesProcessorFactory(config, queryEndpoint, updateEndpoint)
            .createUpdateProcessor(EHRITypes.PERSON)
    override val getTriplesSparqlPath = "src/test/resources/historicalAgents/getAllHistoricalAgentsTriples.rq"
    override val getAllIdsSparqlPath = "src/test/resources/historicalAgents/getAllHistoricalAgentsIds.rq"
    val person001047Data = RDFDataMgr.loadDataset("src/test/resources/historicalAgents/persons/person001047.ttl")
    val person001774Data = RDFDataMgr.loadDataset("src/test/resources/historicalAgents/persons/person001774.ttl")
    val person001774DataUpdated = RDFDataMgr.loadDataset("src/test/resources/historicalAgents/persons/person001774Updated.ttl")
    val person27069547Data = RDFDataMgr.loadDataset("src/test/resources/historicalAgents/persons/person27069547.ttl")
    val person001774JsonGraphQLData = SourceHelper.readFile("src/test/resources/historicalAgents/persons/person001774JsonGraphQL.json")

    override fun doCreateTestData() {
        updatesProcessor.create(person001047Data)
        updatesProcessor.create(person001774Data)
    }

    override fun doCleanUp() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_pers-001047",
            "HistoricalAgent"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_pers-001774",
            "HistoricalAgent"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_pers-27069547",
            "HistoricalAgent"
        ))
    }

    @Test
    @DisplayName("Deletion of one person is satisfactory")
    fun testDeletion() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_pers-001774",
            "HistoricalAgent"
        ))

        Assertions.assertTrue { retrieveAllEntityIds().size == 1 }

        val person001774PersistedData = retrieveEntityTriples("ehri-pers\\/001774")
        val person001774DataStatements = person001774Data.defaultModel.listStatements().toList()

        //These should have been deleted
        assertStatementsNotExist(person001774DataStatements, person001774PersistedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#isOrWasSubjectOf",
            "https://www.ica.org/standards/RiC/ontology#isCreatorOf",
            "https://www.ica.org/standards/RiC/ontology#hasOrHadSubject",
            "https://www.ica.org/standards/RiC/ontology#hasCreator"
        ))

        //These should have been preserved
        assertStatementsExist(person001774DataStatements, person001774PersistedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#isOrWasSubjectOf",
            "https://www.ica.org/standards/RiC/ontology#isCreatorOf",
            "https://www.ica.org/standards/RiC/ontology#hasOrHadSubject",
            "https://www.ica.org/standards/RiC/ontology#hasCreator"
        ))
    }

    @Test
    @DisplayName("Update of one person is satisfactory")
    fun testUpdate() {
        doTestUpdate(person001774DataUpdated)
    }

    @Test
    @DisplayName("Update of one person with JSON data is satisfactory")
    fun testUpdateWithGraphQLData() {
        val updatedData = updatesProcessor.transformToRDF(person001774JsonGraphQLData)
        doTestUpdate(updatedData)
    }

    private fun doTestUpdate(data: Dataset) {
        assertNotStartingWith(retrieveEntityTriples("ehri-pers\\/001774"), listOf(
            "https://www.ica.org/standards/RiC/ontology#history"
        ), "[Test Update]")

        updatesProcessor.update(EHRIEvent(
            "dummy",
            "update-event",
            DateTimeUtils.nowAsString(),
            "ehri_pers-001774",
            "HistoricalAgent"
        ), data)

        val person001774PersistedUpdatedData = retrieveEntityTriples("ehri-pers\\/001774")
        val person001774DataStatements = person001774Data.defaultModel.listStatements().toList()

        assertStatementsExcluding(person001774DataStatements, person001774PersistedUpdatedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#history"
        ))

        assertStartingWith(person001774PersistedUpdatedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#history"
        ), "[Test Update]")

        Assertions.assertTrue { retrieveAllEntityIds().size == 2 }
    }

    @Test
    @DisplayName("Creation of a new person is satisfactory")
    fun testCreation() {
        updatesProcessor.create(person27069547Data)

        Assertions.assertTrue { retrieveAllEntityIds().size == 3 }

        val person2706954PersistedData = retrieveEntityTriples("ehri-pers\\/27069547")
        val person2706954DataStatements = person27069547Data.defaultModel.listStatements().toList()

        //Everything should be identical
        assertStatementsExist(person2706954DataStatements, person2706954PersistedData)

        Assertions.assertTrue { person2706954DataStatements.size == person2706954PersistedData.size }
    }

}
