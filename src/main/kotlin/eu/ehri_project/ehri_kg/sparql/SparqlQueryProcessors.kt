package eu.ehri_project.ehri_kg.sparql

import org.apache.jena.query.*
import org.apache.jena.rdf.model.Model
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory

class SparqlDatasetQueryProcessor(val dataset: Dataset) {

    fun query(query: String): ResultSet {
        val compiledQuery = QueryFactory.create(query)
        return QueryExecution
            .create()
            .dataset(dataset.asDatasetGraph())
            .query(compiledQuery)
            .build().execSelect()
    }
}

class SparqlEndpointQueryProcessor(val endpoint: String) {

    fun construct(query: String): Model {
        val compiledQuery = QueryFactory.create(query)
        return QueryExecutionFactory
            .sparqlService(endpoint, compiledQuery)
            .execConstruct()
    }

    fun update(query: String) {
        val compiledQuery = UpdateFactory.create(query)
        UpdateExecutionFactory
            .createRemote(compiledQuery, endpoint)
            .execute()
    }
}