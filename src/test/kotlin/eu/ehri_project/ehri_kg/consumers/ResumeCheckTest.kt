package eu.ehri_project.ehri_kg.consumers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResumeCheckTest {

    @Test
    fun `a stream that replays from the event it resumed from passes`() {
        val check = ResumeCheck("e1")
        check.received("")
        assertNull(check.replayed)
        check.received("e1")
        check.received("e2")
        assertEquals(true, check.replayed)
    }

    @Test
    fun `a stream whose first event is another one fails`() {
        val check = ResumeCheck("e1")
        check.received("e7")
        check.received("e1")
        assertEquals(false, check.replayed)
    }

    @Test
    fun `a stream that didn't resume isn't checked`() {
        val check = ResumeCheck(null)
        check.received("e7")
        assertNull(check.replayed)
    }
}
