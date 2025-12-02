package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.sparql.SparqlEndpointQueryProcessor
import org.apache.jena.rdf.model.Statement

interface TestSparqlService {

    fun retrieveEntityTriples(queryEndpoint: String, pathToQuery: String, entityId: String): List<Statement> {
        val query = SourceHelper
            .readFile(pathToQuery)
            .replace("<\$entityId>", entityId)
        val resultSet = SparqlEndpointQueryProcessor(queryEndpoint).construct(query)
        return resultSet.listStatements().toList()
    }

    fun retrieveAllEntityIds(queryEndpoint: String, pathToQuery: String): List<String> {
        val query = SourceHelper.readFile(pathToQuery)
        val resultSet = SparqlEndpointQueryProcessor(queryEndpoint).query(query)
        return resultSet.asSequence().map { it.get("s").toString() }.toList()
    }
}