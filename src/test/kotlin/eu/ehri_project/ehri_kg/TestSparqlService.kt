package eu.ehri_project.ehri_kg

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.processors.UpdatesProcessor
import eu.ehri_project.ehri_kg.sparql.SparqlEndpointQueryProcessor
import org.apache.jena.rdf.model.Statement
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach

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

    fun assertStatementsNotExist(original: List<Statement>, modified: List<Statement>, predicatesToOmit: List<String>) {
        original.filter {
            predicatesToOmit.none { o -> o == it.predicate.uri }
        }.forEach { Assertions.assertFalse { modified.contains(it) } }
    }

    fun assertStatementsExist(original: List<Statement>, modified: List<Statement>, predicatesToInclude: List<String>) {
        original.filter {
            predicatesToInclude.contains(it.predicate.uri)
        }.forEach { Assertions.assertTrue { modified.contains(it) } }
    }

    fun assertStatementsExcluding(original: List<Statement>, modified: List<Statement>, predicatesToOmit: List<String>) {
        original.filter {
            predicatesToOmit.none { o -> o == it.predicate.uri }
        }.forEach { Assertions.assertTrue { modified.contains(it) } }
    }

    fun assertStartingWith(statements: List<Statement>, predicatesToTest: List<String>, prefix: String) {
        statements.toList().filter {
            predicatesToTest.contains(it.predicate.uri)
        }.forEach { Assertions.assertTrue(it.literal.string.startsWith(prefix)) }
    }

    fun assertNotStartingWith(statements: List<Statement>, predicatesToTest: List<String>, prefix: String) {
        statements.toList().filter {
            predicatesToTest.contains(it.predicate.uri)
        }.forEach { Assertions.assertFalse { (it.literal.string.startsWith(prefix)) } }
    }

    fun assertStatementsExist(original: List<Statement>, modified: List<Statement>) {
        original.forEach { Assertions.assertTrue { modified.contains(it) } }
    }

    fun assertExistenceOfOnlyOne(statements: List<Statement>, predicateToTest: String) {
        Assertions.assertTrue { statements.filter {
            it.predicate.uri == predicateToTest
        }.size == 1 }
    }

    fun assertNonExistence(statements: List<Statement>, predicateToTest: String) {
        Assertions.assertTrue { statements.none {
            it.predicate.uri == predicateToTest
        } }
    }
}

abstract class EntityTest : TestSparqlService {

    protected val config = Config("conf/config.properties")
    protected val queryEndpoint: String = "http://localhost:7878/query"
    protected val updateEndpoint: String = "http://localhost:7878/update"
    protected abstract val updatesProcessor: UpdatesProcessor
    protected abstract val getTriplesSparqlPath: String
    protected abstract val getAllIdsSparqlPath: String

    @BeforeEach
    fun createTestData() {
        Assertions.assertTrue { retrieveAllEntityIds().size == 0 }

        doCreateTestData()

        Assertions.assertTrue { retrieveAllEntityIds().size == 2 }
    }

    abstract fun doCreateTestData()

    @AfterEach
    fun cleanUp() {
        doCleanUp()

        //This works as long as we do not include other types that may create links with these countries as subjects.
        Assertions.assertTrue { retrieveAllEntityIds().size == 0 }
    }

    abstract fun doCleanUp()

    protected fun retrieveEntityTriples(entityCode: String): List<Statement> {
        return retrieveEntityTriples(
            queryEndpoint,
            getTriplesSparqlPath,
            entityCode
        )
    }

    protected fun retrieveAllEntityIds(): List<String> {
        return retrieveAllEntityIds(queryEndpoint, getAllIdsSparqlPath)
    }
}