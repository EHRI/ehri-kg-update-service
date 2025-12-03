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

    override val queryEndpoint = config.get("querySparqlEndpoint")
    override val updatesProcessor = UpdatesProcessorFactory(config).createUpdateProcessor(EHRITypes.INSTITUTION)
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

        //These should have been deleted
        kdData.defaultModel.listStatements().toList().filter {
            it.predicate.uri != "https://www.ica.org/standards/RiC/ontology#isOrWasHolderOf" &&
            it.predicate.uri != "http://lod.ehri-project-test.eu/ontology#isCopyOf" &&
            it.predicate.uri != "http://lod.ehri-project-test.eu/ontology#hasCopy"
        }.forEach { Assertions.assertFalse { kdPersistedData.contains(it) } }

        //These should have been preserved
        kdData.defaultModel.listStatements().toList().filter {
            it.predicate.uri == "https://www.ica.org/standards/RiC/ontology#isOrWasHolderOf" ||
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#isCopyOf" ||
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#hasCopy"
        }.forEach { Assertions.assertTrue { kdPersistedData.contains(it) } }
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
        // In particular these mapping rules generate the rft:type property again which collides with the test under CountryTest.
        UpdatesProcessorFactory(config).createUpdateProcessor(EHRITypes.COUNTRY)
            .delete(EHRIEvent(
                "dummy",
                "delete-event",
                DateTimeUtils.nowAsString(),
                "gb",
                "Country"
            ))
    }

    private fun doTestUpdate(data: Dataset) {
        retrieveEntityTriples("gb-003348").toList().filter {
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#conditionsOfAccess" ||
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#accessibility"
        }.forEach { Assertions.assertFalse { it.literal.string.startsWith("[Test update]") } }

        updatesProcessor.update(EHRIEvent(
            "dummy",
            "update-event",
            DateTimeUtils.nowAsString(),
            "gb-003348",
            "Repository"
        ), data)

        val persitedWienerLibraryUpdatedData = retrieveEntityTriples("gb-003348")

        wienerLibraryData.defaultModel.listStatements().toList().filter {
            it.predicate.uri != "http://lod.ehri-project-test.eu/ontology#conditionsOfAccess" &&
            it.predicate.uri != "http://lod.ehri-project-test.eu/ontology#accessibility"
        }.forEach { Assertions.assertTrue { persitedWienerLibraryUpdatedData.contains(it) } }

        persitedWienerLibraryUpdatedData.toList().filter {
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#conditionsOfAccess" ||
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#accessibility"
        }.forEach { Assertions.assertTrue { it.literal.string.startsWith("[Test update]") } }

        Assertions.assertTrue { retrieveAllEntityIds().size == 2 }
    }

    @Test
    @DisplayName("Creation of a new institution is satisfactory")
    fun testCreation() {
        updatesProcessor.create(niodData)

        Assertions.assertTrue { retrieveAllEntityIds().size == 3 }

        val niodPersistedData = retrieveEntityTriples("nl-002896")

        //Everything should be identical
        niodData.defaultModel.listStatements().toList().forEach {
            Assertions.assertTrue { niodPersistedData.contains(it) }
        }
        niodData.defaultModel.listStatements().toList().size == niodPersistedData.size
    }

}