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
        initialiseSchema()
    }

    private fun connect(): Connection = DriverManager.getConnection("jdbc:sqlite:$databasePath")

    // We can run initialiseSchema on existing databases, if, e.g. they
    // don't have the index...
    private fun initialiseSchema() {
        if (!File(databasePath).isFile) {
            logger.info { "Creating database for registering the events history at $databasePath" }
        }
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(SourceHelper.readFile(creationScriptPath))
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

    /**
     * The id of the last event recorded, whether or not it was processed successfully, if any. Rows are compared in
     * the order they were inserted, which is the order the events were processed in, as the timestamps, without
     * trailing zeros, don't sort as text.
     */
    fun lastEventId(): String? {
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT event_id FROM events_history ORDER BY rowid DESC LIMIT 1;").use { rs ->
                    return if (rs.next()) rs.getString("event_id") else null
                }
            }
        }
    }

    fun checkIfSuccessfullyProcessed(event: EHRIEvent): Boolean {
        connect().use { connection ->
            connection.prepareStatement(
                """
                SELECT errors FROM events_history
                WHERE event_id = ? AND item_id = ? AND item_type = ? AND event_type = ?
                ORDER BY timestamp DESC;
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, event.eventId)
                statement.setString(2, event.id)
                statement.setString(3, event.type)
                statement.setString(4, event.eventType)
                statement.executeQuery().use { rs ->
                    val hasNext = rs.next()
                    val errors = rs.getString("errors")
                    return hasNext && errors.isNullOrEmpty()
                }
            }
        }
    }
}
