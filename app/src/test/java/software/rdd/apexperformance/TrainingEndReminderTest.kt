package software.rdd.apexperformance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import software.rdd.apexperformance.core.network.TrainingEndReminder
import java.time.Instant

class TrainingEndReminderTest {

    private val data = mapOf(
        "type" to "training_end_reminder",
        "trainingId" to "5b0c3f0e-8a7d-4c1b-9e2f-1a2b3c4d5e6f",
        "startedAt" to "2026-11-11T10:00:00.250Z",
        "title" to "Trening još traje",
        "body" to "Legs · Ana Anić traje već sat vremena. Želiš li ga završiti?"
    )

    @Test
    fun parsesTheBackendPush() {
        val content = TrainingEndReminder.parse(data)!!

        assertEquals("5b0c3f0e-8a7d-4c1b-9e2f-1a2b3c4d5e6f", content.trainingId)
        assertEquals(Instant.parse("2026-11-11T10:00:00.250Z"), content.startedAt)
        assertEquals("Trening još traje", content.title)
        // Sent back unchanged with the end time.
        assertEquals("2026-11-11T10:00:00.250Z", content.startedAt.toString())
    }

    @Test
    fun startWithoutMilliseconds() {
        val content = TrainingEndReminder.parse(data + ("startedAt" to "2026-11-11T10:00:00Z"))!!
        assertEquals(Instant.parse("2026-11-11T10:00:00Z"), content.startedAt)
    }

    @Test
    fun otherPushesAndIncompleteOnesAreNotReminders() {
        assertNull(TrainingEndReminder.parse(data + ("type" to "training")))
        assertNull(TrainingEndReminder.parse(data - "trainingId"))
        assertNull(TrainingEndReminder.parse(data - "startedAt"))
        assertNull(TrainingEndReminder.parse(data + ("startedAt" to "yesterday")))
    }

    @Test
    fun oneNotificationPerTraining() {
        val first = TrainingEndReminder.parse(data)!!
        val again = TrainingEndReminder.parse(data + ("startedAt" to "2026-11-11T11:00:00Z"))!!
        val other = TrainingEndReminder.parse(data + ("trainingId" to "00000000-0000-0000-0000-000000000001"))!!

        assertEquals(first.notificationId, again.notificationId)
        assertNotEquals(first.notificationId, other.notificationId)
    }
}
