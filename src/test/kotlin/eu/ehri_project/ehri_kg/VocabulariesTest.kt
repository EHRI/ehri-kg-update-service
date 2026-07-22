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

class VocabulariesTest : EntityTest() {

    override val updatesProcessor =
        UpdatesProcessorFactory(config, queryEndpoint, updateEndpoint)
            .createUpdateProcessor(EHRITypes.VOCABULARY)
    override val getTriplesSparqlPath = "src/test/resources/vocabularies/getAllVocabulariesTriples.rq"
    override val getAllIdsSparqlPath = "src/test/resources/vocabularies/getAllVocabulariesIds.rq"
    val termData = RDFDataMgr.loadDataset("src/test/resources/vocabularies/terms1062.ttl")
    val campData = RDFDataMgr.loadDataset("src/test/resources/vocabularies/camps2233.ttl")
    val termDataUpdated = RDFDataMgr.loadDataset("src/test/resources/vocabularies/terms1062Updated.ttl")
    val ghettoData = RDFDataMgr.loadDataset("src/test/resources/vocabularies/ghettos600.ttl")
    val termJsonGraphQLData = SourceHelper.readFile("src/test/resources/vocabularies/terms1062JsonGraphQL.json")

    override fun doCreateTestData() {
        updatesProcessor.create(termData)
        updatesProcessor.create(campData)
    }

    override fun doCleanUp() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_terms-1062",
            "CvocConcept"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_camps-2233",
            "CvocConcept"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_ghettos-600",
            "CvocConcept"
        ))
    }

    @Test
    @DisplayName("Deletion of one vocabulary concept is satisfactory")
    fun testDeletion() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "ehri_camps-2233",
            "CvocConcept"
        ))

        Assertions.assertTrue { retrieveAllEntityIds().size == 1 }

        val campPersistedData = retrieveEntityTriples("ehri-camps\\/2233")
        val campDataStatements = termData.defaultModel.listStatements().toList()

        // For now, we test that everything was deleted
        Assertions.assertTrue { campPersistedData.isEmpty() }

        //These should have been deleted
        //assertStatementsNotExist(campDataStatements, campPersistedData, listOf())

        //These should have been preserved
        //assertStatementsExist(campDataStatements, campPersistedData, listOf())
    }

    @Test
    @DisplayName("Update of one vocabulary concept is satisfactory")
    fun testUpdate() {
        doTestUpdate(termDataUpdated)
    }

    @Test
    @DisplayName("Update of one vocabulary concept with JSON data is satisfactory")
    fun testUpdateWithGraphQLData() {
        val updatedData = updatesProcessor.transformToRDF(termJsonGraphQLData)
        doTestUpdate(updatedData)
    }

    private fun doTestUpdate(data: Dataset) {
        updatesProcessor.update(EHRIEvent(
            "dummy",
            "update-event",
            DateTimeUtils.nowAsString(),
            "ehri_terms-1062",
            "CvocConcept"
        ), data)

        val persistedTermUpdatedData = retrieveEntityTriples("ehri-terms\\/1062")
        val termDataStatements = termData.defaultModel.listStatements().toList()

        assertStatementsExcluding(termDataStatements, persistedTermUpdatedData, listOf(
            "http://www.w3.org/2004/02/skos/core#prefLabel"
        ))

        assertStatementsExist(listOf(
            termData.defaultModel.createStatement(
                termData.defaultModel.createResource("http://lod.ehri-project-test.eu/vocabularies/ehri-terms/1062"),
                termData.defaultModel.createProperty("http://www.w3.org/2004/02/skos/core#prefLabel"),
                termData.defaultModel.createLiteral("Hola, soy una prueba", "es"),
            )
        ), persistedTermUpdatedData)

        Assertions.assertTrue { retrieveAllEntityIds().size == 2 }
    }

    @Test
    @DisplayName("Creation of a new vocabulary concept is satisfactory")
    fun testCreation() {
        updatesProcessor.create(ghettoData)

        Assertions.assertTrue { retrieveAllEntityIds().size == 3 }

        val ghettoPersistedData = retrieveEntityTriples("ehri-ghettos\\/600")
        val ghettoDataStatements = ghettoData.defaultModel.listStatements().toList()

        //Everything should be identical
        assertStatementsExist(ghettoDataStatements, ghettoPersistedData)

        Assertions.assertTrue { ghettoDataStatements.size == ghettoPersistedData.size }
    }

}
