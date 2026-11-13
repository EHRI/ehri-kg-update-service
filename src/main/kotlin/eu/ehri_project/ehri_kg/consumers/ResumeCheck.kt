package eu.ehri_project.ehri_kg.consumers

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Checks that a stream resumed from [resumedFrom] replays from it: the Portal replays from a `Last-Event-Id` on, that
 * event included, but if it doesn't have the event, e.g. one from another Portal instance, it silently sends only new
 * events, so the changes made since are missed.
 */
class ResumeCheck(private val resumedFrom: String?) {
    private val logger = KotlinLogging.logger {}

    @Volatile private var checked = resumedFrom == null

    /** Whether the stream replayed from the event, once known: null until the first event is received. */
    @Volatile var replayed: Boolean? = null
        private set

    /** Checks the first event received, ignoring keep-alives, which have no id. */
    fun received(eventId: String) {
        if (checked || eventId.isEmpty()) return
        checked = true
        replayed = eventId == resumedFrom
        if (replayed == true) logger.info { "The event stream replays from event $resumedFrom" }
        else logger.error { "The event stream was resumed from event $resumedFrom, but its first event is $eventId: " +
            "the Portal doesn't have the event, so the changes made since it were missed. Check that the events " +
            "history is from this Portal, and rebuild the KG with a batch conversion if need be." }
    }
}
