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

class InstitutionsTest : EntityTest() {

    override val updatesProcessor =
        UpdatesProcessorFactory(config, queryEndpoint, updateEndpoint)
            .createUpdateProcessor(EHRITypes.INSTITUTION)
    override val getTriplesSparqlPath = "src/test/resources/institutions/getAllInstitutionsTriples.rq"
    override val getAllIdsSparqlPath = "src/test/resources/institutions/getAllInstitutionsIds.rq"
    val kdData = RDFDataMgr.loadDataset("src/test/resources/institutions/kd.ttl")
    val wienerLibraryData = RDFDataMgr.loadDataset("src/test/resources/institutions/wienerLibrary.ttl")
    val wienerLibraryDataUpdated = RDFDataMgr.loadDataset("src/test/resources/institutions/wienerLibraryUpdated.ttl")
    val niodData = RDFDataMgr.loadDataset("src/test/resources/institutions/niod.ttl")
    val wienerLibraryJsonGraphQLData = SourceHelper.readFile("src/test/resources/institutions/wienerLibraryJsonGraphQL.json")

    override fun doCreateTestData() {
        updatesProcessor.create(kdData)
        updatesProcessor.create(wienerLibraryData)
    }

    override fun doCleanUp() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "be-002157",
            "Repository"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "gb-003348",
            "Repository"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "nl-002896",
            "Repository"
        ))
    }

    @Test
    @DisplayName("Deletion of one institution is satisfactory")
    fun testDeletion() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "be-002157",
            "Country"
        ))

        Assertions.assertTrue { retrieveAllEntityIds().size == 1 }

        val kdPersistedData = retrieveEntityTriples("be-002157")
        val kdDataStatements = kdData.defaultModel.listStatements().toList()

        //These should have been deleted
        assertStatementsNotExist(kdDataStatements, kdPersistedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#isOrWasHolderOf",
            "http://lod.ehri-project-test.eu/ontology#isCopyOf",
            "http://lod.ehri-project-test.eu/ontology#hasCopy"
        ))

        //These should have been preserved
        assertStatementsExist(kdDataStatements, kdPersistedData, listOf(
            "https://www.ica.org/standards/RiC/ontology#isOrWasHolderOf",
            "http://lod.ehri-project-test.eu/ontology#isCopyOf",
            "http://lod.ehri-project-test.eu/ontology#hasCopy"
        ))
    }

    @Test
    @DisplayName("Update of one institution is satisfactory")
    fun testUpdate() {
        doTestUpdate(wienerLibraryDataUpdated)
    }

    @Test
    @DisplayName("Update of one institution with JSON data is satisfactory")
    fun testUpdateWithGraphQLData() {
        val updatedData = updatesProcessor.transformToRDF(wienerLibraryJsonGraphQLData)
        doTestUpdate(updatedData)
        // This forces the deletion of UK's data to avoid collisions with countries tests.
        // In particular, these mapping rules generate the rdf:type property again which collides with the test under CountryTest.
        UpdatesProcessorFactory(config, queryEndpoint, updateEndpoint)
            .createUpdateProcessor(EHRITypes.COUNTRY)
                .delete(EHRIEvent(
                    "dummy",
                    "delete-event",
                    DateTimeUtils.nowAsString(),
                    "gb",
                    "Country"
                ))
    }

    private fun doTestUpdate(data: Dataset) {
        assertNotStartingWith(retrieveEntityTriples("gb-003348"), listOf(
            "http://lod.ehri-project-test.eu/ontology#conditionsOfAccess",
            "http://lod.ehri-project-test.eu/ontology#accessibility"
        ), "[Test update]")

        updatesProcessor.update(EHRIEvent(
            "dummy",
            "update-event",
            DateTimeUtils.nowAsString(),
            "gb-003348",
            "Repository"
        ), data)

        val persitedWienerLibraryUpdatedData = retrieveEntityTriples("gb-003348")
        val wienerLibraryDataStatements = wienerLibraryData.defaultModel.listStatements().toList()

        assertStatementsExcluding(wienerLibraryDataStatements, persitedWienerLibraryUpdatedData, listOf(
            "http://lod.ehri-project-test.eu/ontology#conditionsOfAccess",
            "http://lod.ehri-project-test.eu/ontology#accessibility"
        ))

        assertStartingWith(persitedWienerLibraryUpdatedData, listOf(
            "http://lod.ehri-project-test.eu/ontology#conditionsOfAccess",
            "http://lod.ehri-project-test.eu/ontology#accessibility"
        ), "[Test update]")

        Assertions.assertTrue { retrieveAllEntityIds().size == 2 }
    }

    @Test
    @DisplayName("Creation of a new institution is satisfactory")
    fun testCreation() {
        updatesProcessor.create(niodData)

        Assertions.assertTrue { retrieveAllEntityIds().size == 3 }

        val niodPersistedData = retrieveEntityTriples("nl-002896")
        val niodDataStatements = niodData.defaultModel.listStatements().toList()

        //Everything should be identical
        assertStatementsExist(niodDataStatements, niodPersistedData)

        niodDataStatements.size == niodPersistedData.size
    }

}