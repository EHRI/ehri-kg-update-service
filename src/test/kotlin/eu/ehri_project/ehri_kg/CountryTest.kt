package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.processors.UpdatesProcessorFactory
import eu.ehri_project.ehri_kg.sparql.SparqlEndpointQueryProcessor
import org.apache.jena.atlas.lib.DateTimeUtils
import org.apache.jena.rdf.model.Statement
import org.apache.jena.riot.RDFDataMgr
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import kotlin.test.Test

class CountryTest {

    val config = Config("conf/config.properties")
    val queryEndpoint = config.get("querySparqlEndpoint")
    val beData = RDFDataMgr.loadDataset("src/test/resources/countries/be.ttl")
    val ukData = RDFDataMgr.loadDataset("src/test/resources/countries/uk.ttl")
    val ukDataUpdated = RDFDataMgr.loadDataset("src/test/resources/countries/ukUpdated.ttl")
    val nlData = RDFDataMgr.loadDataset("src/test/resources/countries/nl.ttl")
    val countryUpdatesProcessor =
        UpdatesProcessorFactory(config)
            .createUpdateProcessor(EHRITypes.COUNTRY)

    @BeforeEach
    fun createCountryTestData() {
        Assertions.assertTrue { retrieveAllCountriesIds().size == 0 }

        countryUpdatesProcessor.create(beData)
        countryUpdatesProcessor.create(ukData)

        Assertions.assertTrue { retrieveAllCountriesIds().size == 2 }
    }

    @AfterEach
    fun cleanUp() {
        countryUpdatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "be",
            "Country"
        ))
        countryUpdatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "gb",
            "Country"
        ))
        countryUpdatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "nl",
            "Country"
        ))

        //This works as long as we do not include other types that may create links with these countries as subjects.
        Assertions.assertTrue { retrieveAllCountriesIds().size == 0 }
    }

    @Test
    @DisplayName("Deletion of one country is satisfactory")
    fun testDeletion() {
        countryUpdatesProcessor.delete(EHRIEvent(
            "dummy",
            "delete-event",
            DateTimeUtils.nowAsString(),
            "be",
            "Country"
        ))

        Assertions.assertTrue { retrieveAllCountriesIds().size == 1 }

        val bePersistedData = retrieveCountryTriples("be")

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
        Assertions.assertFalse { retrieveCountryTriples("gb").toList().find {
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#researchSummary"
        }!!.literal.string.startsWith("[Test update]") }

        countryUpdatesProcessor.update(EHRIEvent(
            "dummy",
            "update-event",
            DateTimeUtils.nowAsString(),
            "gb",
            "Country"
        ), ukDataUpdated)

        val persitedUkUpdatedData = retrieveCountryTriples("gb")

        ukData.defaultModel.listStatements().toList().filter {
            it.predicate.uri != "http://lod.ehri-project-test.eu/ontology#researchSummary"
        }.forEach { Assertions.assertTrue { persitedUkUpdatedData.contains(it) } }

        Assertions.assertTrue { persitedUkUpdatedData.toList().find {
            it.predicate.uri == "http://lod.ehri-project-test.eu/ontology#researchSummary"
        }!!.literal.string.startsWith("[Test update]") }

        Assertions.assertTrue { retrieveAllCountriesIds().size == 2 }
    }

    @Test
    @DisplayName("Creation of a new country is satisfactory")
    fun testCreation() {
        countryUpdatesProcessor.create(nlData)

        Assertions.assertTrue { retrieveAllCountriesIds().size == 3 }

        val nlPersistedData = retrieveCountryTriples("nl")

        //Everything should be identical
        nlData.defaultModel.listStatements().toList().forEach {
            Assertions.assertTrue { nlPersistedData.contains(it) }
        }
    }

    fun retrieveCountryTriples(countryCode: String): List<Statement> {
        val query = SourceHelper
            .readFile("src/test/resources/countries/getAllCountriesTriples.sparql")
            .replaceFirst("<\$entityId>", countryCode)
        val resultSet = SparqlEndpointQueryProcessor(queryEndpoint).construct(query)
        return resultSet.listStatements().toList()
    }

    fun retrieveAllCountriesIds(): List<String> {
        val query = SourceHelper.readFile("src/test/resources/countries/getAllCountriesIds.sparql")
        val resultSet = SparqlEndpointQueryProcessor(queryEndpoint).query(query)
        return resultSet.asSequence().map { it.get("s").toString() }.toList()
    }

}