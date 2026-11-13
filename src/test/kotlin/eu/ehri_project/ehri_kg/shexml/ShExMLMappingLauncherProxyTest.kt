package eu.ehri_project.ehri_kg.shexml

import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.support.loadResource
import org.apache.jena.rdf.model.ResourceFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ShExMLMappingLauncherProxyTest {

    @Test
    fun `maps GraphQL JSON to RDF with the configured rules`() {
        val result = ShExMLMappingLauncherProxy().convert(
            SourceHelper.readFile("conf/countries/countriesMappingRules.shexml"),
            loadResource("countries/ukJsonGraphQL.json")
        ).defaultModel

        val gb = ResourceFactory.createResource("http://lod.ehri-project-test.eu/countries/gb")
        assertFalse(result.isEmpty)
        assertTrue(result.containsResource(gb))
    }

    @Test
    fun `consecutive conversions do not leak input into each other`() {
        val proxy = ShExMLMappingLauncherProxy()
        val rules = SourceHelper.readFile("conf/countries/countriesMappingRules.shexml")

        val first = proxy.convert(rules, loadResource("countries/ukJsonGraphQL.json")).defaultModel
        val second = proxy.convert(rules, loadResource("countries/ukJsonGraphQL.json")).defaultModel

        assertTrue(first.isIsomorphicWith(second))
    }

    @Test
    fun `stdin is restored after a conversion`() {
        val stdin = System.`in`
        ShExMLMappingLauncherProxy().convert(
            SourceHelper.readFile("conf/countries/countriesMappingRules.shexml"),
            loadResource("countries/ukJsonGraphQL.json"))
        assertSame(stdin, System.`in`)
    }
}
