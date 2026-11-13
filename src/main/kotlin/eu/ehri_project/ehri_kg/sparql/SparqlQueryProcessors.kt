package eu.ehri_project.ehri_kg.sparql

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.jena.query.*
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.update.UpdateExecution
import org.apache.jena.update.UpdateFactory

class SparqlDatasetQueryProcessor(val dataset: Dataset) {

    fun query(query: String): ResultSet {
        val compiledQuery = QueryFactory.create(query)
        return QueryExecution
            .create()
            .dataset(dataset)
            .query(compiledQuery)
            .build().execSelect()
    }
}

interface SparqlStore {
    fun construct(query: String): Model
    fun update(query: String)
}

class RemoteSparqlStore(val queryEndpoint: String, val updateEndpoint: String) : SparqlStore {
    override fun construct(query: String): Model = SparqlEndpointQueryProcessor(queryEndpoint).construct(query)
    override fun update(query: String) = SparqlEndpointQueryProcessor(updateEndpoint).update(query)
}

class LoggingSparqlStore(
    private val log: (String) -> Unit = { statement -> sparqlLogger.info { statement } }
) : SparqlStore {
    override fun construct(query: String): Model = ModelFactory.createDefaultModel()
    override fun update(query: String) = log(formatStatement(query))

    companion object {
        private val sparqlLogger = KotlinLogging.logger("sparql")

        // Terminated with ';' so that the logged statements together form a valid SPARQL Update request
        fun formatStatement(query: String) = "${query.trimEnd()}\n;"
    }
}

class SparqlEndpointQueryProcessor(val endpoint: String) {

    fun query(query: String): ResultSet {
        val compiledQuery = QueryFactory.create(query)
        return QueryExecution.service(endpoint).query(compiledQuery).build().execSelect()
    }

    fun construct(query: String): Model {
        val compiledQuery = QueryFactory.create(query)
        return QueryExecution.service(endpoint).query(compiledQuery).build().use { it.execConstruct() }
    }

    fun update(query: String) {
        val compiledQuery = UpdateFactory.create(query)
        UpdateExecution.service(endpoint).update(compiledQuery).execute()
    }
}