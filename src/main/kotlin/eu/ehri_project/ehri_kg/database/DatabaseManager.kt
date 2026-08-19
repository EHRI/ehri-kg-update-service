package eu.ehri_project.ehri_kg.database

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import eu.ehri_project.ehri_kg.model.EHRIEvent
import eu.ehri_project.ehri_kg.model.EHRIUpdateReport
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseManager(config: Config) {
    private val logger = KotlinLogging.logger {}
    private val databasePath = config.get("databasePath")
    private val creationScriptPath = config.get("databaseCreationScript")

    init {
        createDatabaseIfNotExists()
    }

    private fun connect(): Connection = DriverManager.getConnection("jdbc:sqlite:$databasePath")

    private fun createDatabaseIfNotExists() {
        if (!File(databasePath).isFile) {
            logger.info { "Creating database for registering the events history at $databasePath" }
            connect().use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(SourceHelper.readFile(creationScriptPath))
                }
            }
        }
    }

    fun insertReport(report: EHRIUpdateReport) {
        connect().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO events_history (event_id, event_type, timestamp, item_id, item_type, executed_queries, rdf_diff, errors)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, report.receivedEvent.eventId)
                statement.setString(2, report.receivedEvent.eventType)
                statement.setString(3, report.timeStamp)
                statement.setString(4, report.receivedEvent.id)
                statement.setString(5, report.receivedEvent.type)
                statement.setString(6, report.executedQueries.joinToString("\n\n"))
                statement.setString(7, report.rdfDiff.joinToString("\n\n"))
                statement.setString(8, report.errors.ifEmpty { null })
                try {
                    statement.executeUpdate()
                } catch (e: SQLException) {
                    logger.error(e) { "Error while inserting the event report into the database" }
                }
            }
        }
    }

    fun checkIfSuccessfullyProcessed(event: EHRIEvent): Boolean {
        connect().use { connection ->
            connection.prepareStatement(
                """
                SELECT 1 FROM events_history
                WHERE event_id = ? AND item_id = ? AND item_type = ? AND event_type = ? AND (errors IS NULL OR errors = '')
                ORDER BY timestamp DESC;
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, event.eventId)
                statement.setString(2, event.id)
                statement.setString(3, event.type)
                statement.setString(4, event.eventType)
                statement.executeQuery().use { rs -> return rs.next() }
            }
        }
    }
}
