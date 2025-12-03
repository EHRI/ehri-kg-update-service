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

class ArchivalDescriptionsTest : EntityTest() {

    override val queryEndpoint = config.get("querySparqlEndpoint")
    override val updatesProcessor = UpdatesProcessorFactory(config).createUpdateProcessor(EHRITypes.ARCHIVAL_DESCRIPTION)
    override val getTriplesSparqlPath = "src/test/resources/archivalDescriptions/getAllArchivalDescriptionsTriples.rq"
    override val getAllIdsSparqlPath = "src/test/resources/archivalDescriptions/getAllArchivalDescriptionsIds.rq"
    val kdCollectionData = RDFDataMgr.loadDataset("src/test/resources/archivalDescriptions/kdCollection.ttl")
    val wienerLibraryCollectionData = RDFDataMgr.loadDataset("src/test/resources/archivalDescriptions/wienerLibraryCollection.ttl")
    val wienerLibraryCollectionDataUpdated = RDFDataMgr.loadDataset("src/test/resources/archivalDescriptions/wienerLibraryCollectionUpdated.ttl")
    val niodCollectionData = RDFDataMgr.loadDataset("src/test/resources/archivalDescriptions/niodCollection.ttl")
    val wienerLibraryCollectionJsonGraphQLData = SourceHelper.readFile("src/test/resources/archivalDescriptions/wienerLibraryCollectionJsonGraphQL.json")

    override fun doCreateTestData() {
        updatesProcessor.create(kdCollectionData)
        updatesProcessor.create(wienerLibraryCollectionData)
    }

    override fun doCleanUp() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "be-002157-kd_00017",
            "DocumentaryUnit"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "gb-003348-wl3000_9_1-1",
            "DocumentaryUnit"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "nl-002896-mf1014417",
            "DocumentaryUnit"
        ))
    }

    @Test
    @DisplayName("Deletion of one archival description is satisfactory")
    fun testDeletion() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "be-002157-kd_00017",
            "Country"
        ))

        Assertions.assertTrue { retrieveAllEntityIds().size == 1 }

        val kdCollectionPersistedData = retrieveEntityTriples("be-002157-kd_00017")

        //These should have been deleted
        kdCollectionData.defaultModel.listStatements().toList().filter {
            it.predicate.uri != "https://www.ica.org/standards/RiC/ontology#hasOrHadSubject" &&
            it.predicate.uri != "http://lod.ehri-project-test.eu/ontology#isCopyOf" &&
            it.predicate.uri != "http://lod.ehri-project-test.eu/ontology#hasCopy" &&
            it.predicate.uri != "https://www.ica.org/standards/RiC/ontology#hasCreator"
        }.forEach { Assertions.assertFalse { kdCollectionPersistedData.contains(it) } }

        //These should have been preserved
        kdCollectionData.defaultModel.listStatements().toList().filter {
            it.predicate.uri == "https://www.ica.org/standards/RiC/ontology#hasOrHadSubject" ||
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#isCopyOf" ||
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#hasCopy" ||
            it.predicate.uri == "https://www.ica.org/standards/RiC/ontology#hasCreator"
        }.forEach { Assertions.assertTrue { kdCollectionPersistedData.contains(it) } }
    }

    @Test
    @DisplayName("Update of one archival description is satisfactory")
    fun testUpdate() {
        doTestUpdate(wienerLibraryCollectionDataUpdated)
    }

    @Test
    @DisplayName("Update of one archival description with JSON data is satisfactory")
    fun testUpdateWithGraphQLData() {
        val updatedData = updatesProcessor.transformToRDF(wienerLibraryCollectionJsonGraphQLData)
        doTestUpdate(updatedData)
    }

    private fun doTestUpdate(data: Dataset) {
        retrieveEntityTriples("gb-003348-wl3000_9_1-1").toList().filter {
            it.predicate.uri == "https://www.ica.org/standards/RiC/ontology#scopeAndContent"
        }.forEach { Assertions.assertFalse { it.literal.string.startsWith("[Test update]") } }

        Assertions.assertTrue { retrieveEntityTriples("gb-003348-wl3000_9_1-1").toList().filter {
            it.predicate.uri == "https://www.ica.org/standards/RiC/ontology#history"
        }.size == 1 }

        Assertions.assertTrue { retrieveEntityTriples("gb-003348-wl3000_9_1-1").toList().none {
            it.predicate.uri == "https://www.ica.org/standards/RiC/ontology#accruals"
        } }

        updatesProcessor.update(EHRIEvent(
            "dummy",
            "update-event",
            DateTimeUtils.nowAsString(),
            "gb-003348-wl3000_9_1-1",
            "Repository"
        ), data)

        val persitedWienerLibraryUpdatedData = retrieveEntityTriples("gb-003348-wl3000_9_1-1")

        retrieveEntityTriples("gb-003348-wl3000_9_1-1").toList().filter {
            it.predicate.uri == "https://www.ica.org/standards/RiC/ontology#scopeAndContent"
        }.forEach { Assertions.assertTrue { it.literal.string.startsWith("[Test update]") } }

        retrieveEntityTriples("gb-003348-wl3000_9_1-1").toList().filter {
            it.predicate.uri == "https://www.ica.org/standards/RiC/ontology#accruals"
        }.forEach { Assertions.assertTrue { it.literal.string.equals("Test update") } }

        Assertions.assertTrue { retrieveEntityTriples("gb-003348-wl3000_9_1-1").none {
            it.predicate.uri == "https://www.ica.org/standards/RiC/ontology#history"
        } }

        // The rest of the properties should be identical
        wienerLibraryCollectionData.defaultModel.listStatements().toList().filter {
            it.predicate.uri != "https://www.ica.org/standards/RiC/ontology#scopeAndContent" &&
            it.predicate.uri != "https://www.ica.org/standards/RiC/ontology#accruals" &&
            it.predicate.uri != "https://www.ica.org/standards/RiC/ontology#history"
        }.forEach { Assertions.assertTrue { persitedWienerLibraryUpdatedData.contains(it) } }

        Assertions.assertTrue { retrieveAllEntityIds().size == 2 }
    }

    @Test
    @DisplayName("Creation of a new archival description is satisfactory")
    fun testCreation() {
        updatesProcessor.create(niodCollectionData)

        Assertions.assertTrue { retrieveAllEntityIds().size == 3 }

        val niodCollectionPersistedData = retrieveEntityTriples("nl-002896-mf1014417")

        //Everything should be identical
        niodCollectionData.defaultModel.listStatements().toList().forEach {
            Assertions.assertTrue { niodCollectionPersistedData.contains(it) }
        }
        niodCollectionData.defaultModel.listStatements().toList().size == niodCollectionPersistedData.size
    }

}