package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.processors.UpdatesProcessorFactory
import org.apache.jena.atlas.lib.DateTimeUtils
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Statement
import org.apache.jena.riot.RDFDataMgr
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import kotlin.test.Test

class InstitutionsTest : TestSparqlService {

    val config = Config("conf/config.properties")
    val queryEndpoint = config.get("querySparqlEndpoint")
    val kdData = RDFDataMgr.loadDataset("src/test/resources/institutions/kd.ttl")
    val wienerLibraryData = RDFDataMgr.loadDataset("src/test/resources/institutions/wienerLibrary.ttl")
    val wienerLibraryDataUpdated = RDFDataMgr.loadDataset("src/test/resources/institutions/wienerLibraryUpdated.ttl")
    val niodData = RDFDataMgr.loadDataset("src/test/resources/institutions/niod.ttl")
    val wienerLibraryJsonGraphQLData = SourceHelper.readFile("src/test/resources/institutions/wienerLibraryJsonGraphQL.json")
    val institutionsUpdatesProcessor =
        UpdatesProcessorFactory(config)
            .createUpdateProcessor(EHRITypes.INSTITUTION)

    @BeforeEach
    fun createInstitutionTestData() {
        Assertions.assertTrue { retrieveAllInstitutionsIds().size == 0 }

        institutionsUpdatesProcessor.create(kdData)
        institutionsUpdatesProcessor.create(wienerLibraryData)

        Assertions.assertTrue { retrieveAllInstitutionsIds().size == 2 }
    }

    @AfterEach
    fun cleanUp() {
        institutionsUpdatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "be-002157",
            "Repository"
        ))
        institutionsUpdatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "gb-003348",
            "Repository"
        ))
        institutionsUpdatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "nl-002896",
            "Repository"
        ))

        //This works as long as we do not include other types that may create links with these institutions as subjects.
        Assertions.assertTrue { retrieveAllInstitutionsIds().size == 0 }
    }

    @Test
    @DisplayName("Deletion of one institution is satisfactory")
    fun testDeletion() {
        institutionsUpdatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "be-002157",
            "Country"
        ))

        Assertions.assertTrue { retrieveAllInstitutionsIds().size == 1 }

        val kdPersistedData = retrieveInstitutionTriples("be-002157")

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
        val updatedData = institutionsUpdatesProcessor.transformToRDF(wienerLibraryJsonGraphQLData)
        doTestUpdate(updatedData)
    }

    private fun doTestUpdate(data: Dataset) {
        retrieveInstitutionTriples("gb-003348").toList().filter {
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#conditionsOfAccess" ||
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#accessibility"
        }.forEach { Assertions.assertFalse { it.literal.string.startsWith("[Test update]") } }

        institutionsUpdatesProcessor.update(EHRIEvent(
            "dummy",
            "update-event",
            DateTimeUtils.nowAsString(),
            "gb-003348",
            "Repository"
        ), data)

        val persitedWienerLibraryUpdatedData = retrieveInstitutionTriples("gb-003348")

        wienerLibraryData.defaultModel.listStatements().toList().filter {
            it.predicate.uri != "http://lod.ehri-project-test.eu/ontology#conditionsOfAccess" &&
            it.predicate.uri != "http://lod.ehri-project-test.eu/ontology#accessibility"
        }.forEach { Assertions.assertTrue { persitedWienerLibraryUpdatedData.contains(it) } }

        persitedWienerLibraryUpdatedData.toList().filter {
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#conditionsOfAccess" ||
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#accessibility"
        }.forEach { Assertions.assertTrue { it.literal.string.startsWith("[Test update]") } }

        Assertions.assertTrue { retrieveAllInstitutionsIds().size == 2 }
    }

    @Test
    @DisplayName("Creation of a new institution is satisfactory")
    fun testCreation() {
        institutionsUpdatesProcessor.create(niodData)

        Assertions.assertTrue { retrieveAllInstitutionsIds().size == 3 }

        val niodPersistedData = retrieveInstitutionTriples("nl-002896")

        //Everything should be identical
        niodData.defaultModel.listStatements().toList().forEach {
            Assertions.assertTrue { niodPersistedData.contains(it) }
        }
        niodData.defaultModel.listStatements().toList().size == niodPersistedData.size
    }

    fun retrieveInstitutionTriples(institutionCode: String): List<Statement> {
        return retrieveEntityTriples(
            queryEndpoint,
            "src/test/resources/institutions/getAllInstitutionsTriples.rq",
            institutionCode
        )
    }

    fun retrieveAllInstitutionsIds(): List<String> {
        return retrieveAllEntityIds(queryEndpoint, "src/test/resources/institutions/getAllInstitutionsIds.rq")
    }

}