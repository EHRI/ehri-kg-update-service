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

class CountryTest : EntityTest() {

    override val queryEndpoint = config.get("querySparqlEndpoint")
    override val updatesProcessor = UpdatesProcessorFactory(config).createUpdateProcessor(EHRITypes.COUNTRY)
    override val getTriplesSparqlPath = "src/test/resources/countries/getAllCountriesTriples.rq"
    override val getAllIdsSparqlPath = "src/test/resources/countries/getAllCountriesIds.rq"
    val beData = RDFDataMgr.loadDataset("src/test/resources/countries/be.ttl")
    val ukData = RDFDataMgr.loadDataset("src/test/resources/countries/uk.ttl")
    val ukDataUpdated = RDFDataMgr.loadDataset("src/test/resources/countries/ukUpdated.ttl")
    val nlData = RDFDataMgr.loadDataset("src/test/resources/countries/nl.ttl")
    val ukJsonGraphQLData = SourceHelper.readFile("src/test/resources/countries/ukJsonGraphQL.json")

    override fun doCreateTestData() {
        updatesProcessor.create(beData)
        updatesProcessor.create(ukData)
    }

    override fun doCleanUp() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "be",
            "Country"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "gb",
            "Country"
        ))
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "nl",
            "Country"
        ))
    }

    @Test
    @DisplayName("Deletion of one country is satisfactory")
    fun testDeletion() {
        updatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "be",
            "Country"
        ))

        Assertions.assertTrue { retrieveAllEntityIds().size == 1 }

        val bePersistedData = retrieveEntityTriples("be")

        //These should have been deleted
        beData.defaultModel.listStatements().toList().filter {
            it.predicate.uri != "https://www.ica.org/standards/RiC/ontology#containsOrContained" &&
            it.predicate.uri != "https://www.ica.org/standards/RiC/ontology#isOrWasLocationOfAgent"
        }.forEach { Assertions.assertFalse { bePersistedData.contains(it) } }

        //These should have been preserved
        beData.defaultModel.listStatements().toList().filter {
            it.predicate.uri == "https://www.ica.org/standards/RiC/ontology#containsOrContained" ||
            it.predicate.uri == "https://www.ica.org/standards/RiC/ontology#isOrWasLocationOfAgent"
        }.forEach { Assertions.assertTrue { bePersistedData.contains(it) } }
    }

    @Test
    @DisplayName("Update of one country is satisfactory")
    fun testUpdate() {
        doTestUpdate(ukDataUpdated)
    }

    @Test
    @DisplayName("Update of one country with JSON data is satisfactory")
    fun testUpdateWithGraphQLData() {
        val updatedData = updatesProcessor.transformToRDF(ukJsonGraphQLData)
        doTestUpdate(updatedData)
    }

    private fun doTestUpdate(data: Dataset) {
        Assertions.assertFalse { retrieveEntityTriples("gb").toList().find {
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#researchSummary"
        }!!.literal.string.startsWith("[Test update]") }

        updatesProcessor.update(EHRIEvent(
            "dummy",
            "update-event",
            DateTimeUtils.nowAsString(),
            "gb",
            "Country"
        ), data)

        val persitedUkUpdatedData = retrieveEntityTriples("gb")

        ukData.defaultModel.listStatements().toList().filter {
            it.predicate.uri != "http://lod.ehri-project-test.eu/ontology#researchSummary"
        }.forEach { Assertions.assertTrue { persitedUkUpdatedData.contains(it) } }

        Assertions.assertTrue { persitedUkUpdatedData.toList().find {
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#researchSummary"
        }!!.literal.string.startsWith("[Test update]") }

        Assertions.assertTrue { retrieveAllEntityIds().size == 2 }
    }

    @Test
    @DisplayName("Creation of a new country is satisfactory")
    fun testCreation() {
        updatesProcessor.create(nlData)

        Assertions.assertTrue { retrieveAllEntityIds().size == 3 }

        val nlPersistedData = retrieveEntityTriples("nl")

        //Everything should be identical
        nlData.defaultModel.listStatements().toList().forEach {
            Assertions.assertTrue { nlPersistedData.contains(it) }
        }
        nlData.defaultModel.listStatements().toList().size == nlPersistedData.size
    }

}