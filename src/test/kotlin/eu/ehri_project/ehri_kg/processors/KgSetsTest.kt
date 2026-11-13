package eu.ehri_project.ehri_kg.processors

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.model.EHRITypes
import eu.ehri_project.ehri_kg.shexml.ShExMLMappingLauncherProxy
import eu.ehri_project.ehri_kg.support.loadResource
import org.apache.jena.rdf.model.ResourceFactory
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KgSetsTest {

    private val sets = KgSets(Config("conf/config.properties"))

    @Test
    fun `the sets are configured by the mapping rules they use`() {
        assertEquals(listOf("ehri_pers"), sets.personSets)
        assertEquals(listOf("ehri_cb", "ehri_fm"), sets.corporateBodySets)
        assertEquals(listOf("ehri_terms", "ehri_camps", "ehri_ghettos"), sets.vocabularySets)
    }

    @Test
    fun `a configuration without the sets fails`() {
        val e = assertFailsWith<IllegalArgumentException> {
            KgSets(Config(Properties().apply { setProperty("personSets", "ehri_pers") }))
        }
        assertTrue(e.message!!.contains("corporateBodySets"), e.message)
    }

    @Test
    fun `agents and concepts are in the KG by their kind and set`() {
        assertEquals(EHRITypes.PERSON, sets.agentType("ehri_pers-000001"))
        assertEquals(EHRITypes.CORPORATE_BODY, sets.agentType("ehri_fm-000002"))
        assertNull(sets.agentType("terezin_persons-1"))
        assertNull(sets.agentType("ehri_camps-1"))
        assertTrue(sets.contains("CvocConcept", "ehri_camps-1"))
        assertFalse(sets.contains("CvocConcept", "terezin_terms-1"))
        assertFalse(sets.contains("CvocConcept", "ehri_pers-000001"))
        assertFalse(sets.contains("HistoricalAgent", "ehri_terms-1"))
        assertFalse(sets.contains("DocumentaryUnit", "ehri_pers-000001"))
    }

    @Test
    fun `units only link to agents and concepts in the KG`() {
        val rules = File("conf/archivalDescriptions/archivalDescriptionsMappingRules.shexml").readText()
        val unit = ResourceFactory.createResource("http://lod.ehri-project-test.eu/units/be-000001-kd_00001")
        fun subjects(sets: KgSets) = ShExMLMappingLauncherProxy()
            .convert(rules, sets.withAccessPointsInKg(loadResource("example/graphql/be-000001-kd_00001.json")))
            .defaultModel.listObjectsOfProperty(unit,
                ResourceFactory.createProperty("https://www.ica.org/standards/RiC/ontology#hasOrHadSubject"))
            .toList().map { it.toString().substringAfterLast("vocabularies/") }.sorted()

        assertEquals(listOf("ehri-camps/1", "ehri-terms/1"), subjects(sets))
        assertEquals(listOf("ehri-terms/1"), subjects(KgSets(sets.personSets, sets.corporateBodySets, listOf("ehri_terms"))))
    }
}
