package eu.ehri_project.ehri_kg.validation

import eu.ehri_project.ehri_kg.shexml.ShExMLMappingLauncherProxy
import eu.ehri_project.ehri_kg.support.loadResource
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ResourceFactory.createProperty
import org.apache.jena.rdf.model.ResourceFactory.createResource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KGValidatorTest {

    val validator = KGValidator("conf/shapes/holdings")
    val unit = createResource("http://lod.ehri-project-test.eu/units/be-000001-kd_00001")
    val title = createProperty("https://www.ica.org/standards/RiC/ontology#title")

    private fun mappedUnit(): Model = ShExMLMappingLauncherProxy().convert(
        File("conf/archivalDescriptions/archivalDescriptionsMappingRules.shexml").readText(),
        loadResource("example/graphql/be-000001-kd_00001.json")
    ).defaultModel

    @Test
    fun `a unit's mapped data conforms to the holdings shapes`() {
        assertEquals(emptyList(), validator.validate(mappedUnit().graph))
    }

    @Test
    fun `a unit without a title violates both the ShEx and the SHACL shapes`() {
        val untitled = mappedUnit().apply { removeAll(unit, title, null) }
        val violations = validator.validate(untitled.graph)

        assertEquals(setOf("holdings.shex", "holdings.shacl.ttl"), violations.map { it.schema }.toSet())
        assertTrue(violations.all { it.focusNode == unit.uri })
    }

    @Test
    fun `shapes that do not exist are an error`() {
        assertFailsWith<IllegalArgumentException> { KGValidator("conf/shapes/missing") }
    }
}
