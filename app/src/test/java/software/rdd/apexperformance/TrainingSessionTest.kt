package software.rdd.apexperformance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import software.rdd.apexperformance.model.TrainingSession
import software.rdd.apexperformance.model.TrainingSession.SaveAllResult
import software.rdd.apexperformance.model.TrainingSession.SaveOutcome
import java.time.Instant

// Trainings of clients training at the same time: switching between them,
// the shared time and saving all of them.
class TrainingSessionTest {

    private val ana = TrainingSession.Participant("ana", "Ana", "Anić", id = "p-ana")
    private val marko = TrainingSession.Participant("marko", "Marko", "Marić", id = "p-marko")
    private val ivan = TrainingSession.Participant("ivan", " Ivan ", "Ivić", id = "p-ivan")

    private fun session(vararg participants: TrainingSession.Participant): TrainingSession =
        participants.drop(1).fold(TrainingSession(listOf(participants.first()))) { session, p -> session.adding(p)!! }

    @Test
    fun oneClientIsNotShared() {
        val session = session(ana)
        assertFalse(session.isShared)
        assertEquals("p-ana", session.active.id)
        assertFalse(session.canRemove("p-ana"))
        assertNull(session.removing("p-ana"))
    }

    @Test
    fun addedClientIsShown() {
        val session = session(ana, marko)
        assertTrue(session.isShared)
        assertEquals("p-marko", session.activeId)
        assertEquals("Ana · Marko", session.headerNames)
        assertEquals("p-ana", session.leader.id)
        assertEquals(listOf("p-ana"), session.others.map { it.id })
    }

    @Test
    fun sameClientIsNotAddedTwice() {
        val session = session(ana, marko)
        assertNull(session.adding(TrainingSession.Participant("ana", "Ana", "Anić")))
    }

    @Test
    fun switchingBetweenClients() {
        var session = session(ana, marko, ivan)
        session = session.selecting("p-ana")
        assertEquals("p-ana", session.active.id)
        assertEquals(listOf("p-marko", "p-ivan"), session.others.map { it.id })
        // Unknown ids change nothing.
        assertEquals("p-ana", session.selecting("nobody").activeId)
    }

    @Test
    fun segmentLabels() {
        val session = session(ana, ivan)
        assertEquals("Ana", session.segmentLabel(ana, " "))
        assertEquals("Ana · Gornji dio", session.segmentLabel(ana, " Gornji dio "))
        assertEquals("Ivan", session.segmentLabel(ivan, ""))
        assertEquals("Horvat", TrainingSession.Participant("x", null, "Horvat").shortName)
    }

    @Test
    fun removingAClientShowsTheOneInItsPlace() {
        var session = session(ana, marko, ivan).selecting("p-marko")
        session = session.removing("p-marko")!!
        assertEquals("p-ivan", session.activeId)
        session = session.removing("p-ivan")!!
        assertEquals("p-ana", session.activeId)
        // The last client stays.
        assertNull(session.removing("p-ana"))
    }

    @Test
    fun removalIsConfirmedWhenSomethingWouldBeLost() {
        assertFalse(TrainingSession.needsConfirmationToRemove(enteredSets = 0, isPersisted = false))
        assertTrue(TrainingSession.needsConfirmationToRemove(enteredSets = 2, isPersisted = false))
        assertTrue(TrainingSession.needsConfirmationToRemove(enteredSets = 0, isPersisted = true))
    }

    @Test
    fun firstNameStartsTheSharedTime() {
        val start = Instant.parse("2026-11-19T17:00:00Z")
        var session = session(ana).starting(start)
        // Later names and clients keep it.
        session = session.starting(start.plusSeconds(120)).adding(marko)!!
        assertEquals(start, session.startedAt)
    }

    @Test
    fun saveNeedsEveryTrainingComplete() {
        assertTrue(TrainingSession.canSave(listOf(true, true), isSaving = false))
        assertFalse(TrainingSession.canSave(listOf(true, false), isSaving = false))
        assertFalse(TrainingSession.canSave(listOf(true, true), isSaving = true))
        assertFalse(TrainingSession.canSave(emptyList(), isSaving = false))
    }

    @Test
    fun allSavedOrQueued() {
        val session = session(ana, marko, ivan)
        assertEquals(
            SaveAllResult.Saved(anyQueued = false),
            session.result(mapOf("p-ana" to SaveOutcome.Saved, "p-marko" to SaveOutcome.Saved, "p-ivan" to SaveOutcome.Saved))
        )
        assertEquals(
            SaveAllResult.Saved(anyQueued = true),
            session.result(mapOf("p-ana" to SaveOutcome.Saved, "p-marko" to SaveOutcome.Queued, "p-ivan" to SaveOutcome.Saved))
        )
    }

    @Test
    fun failedOnesInTheSessionsOrder() {
        val session = session(ana, marko, ivan)
        val result = session.result(
            mapOf(
                "p-ivan" to SaveOutcome.Failed("Server error"),
                "p-ana" to SaveOutcome.Saved,
                "p-marko" to SaveOutcome.Failed("No access")
            )
        )
        assertEquals(SaveAllResult.Failed(listOf("p-marko", "p-ivan")), result)
    }

    @Test
    fun lastSetSummary() {
        val now = Instant.now()
        assertEquals("Čučanj 3/4", TrainingSession.SetActivity(now, "Čučanj", 3, 4).summary)
        assertEquals("1/2", TrainingSession.SetActivity(now, "", 1, 2).summary)
        assertNotNull(now)
    }
    @Test
    fun onlyStaffEditSessionsOfMoreTrainings() {
        assertTrue(TrainingSession.opensAsSession(2, isStaff = true))
        assertFalse(TrainingSession.opensAsSession(2, isStaff = false))
        assertFalse(TrainingSession.opensAsSession(1, isStaff = true))
    }

    @Test
    fun sessionIsAskedForOnlyWhenThereCanBeOne() {
        assertFalse(TrainingSession.mayHaveSession(null, null))
        assertTrue(TrainingSession.mayHaveSession("s", null))
        assertTrue(TrainingSession.mayHaveSession(null, Instant.now()))
    }

    @Test
    fun editedSessionShowsTheTappedClient() {
        val start = Instant.parse("2026-11-19T17:00:00Z")
        val session = TrainingSession(listOf(ana, marko), activeId = "p-marko", startedAt = start)
        assertEquals("p-marko", session.active.id)
        assertEquals(start, session.startedAt)
        // An unknown active id falls back to the first client.
        assertEquals("p-ana", TrainingSession(listOf(ana, marko), activeId = "x").active.id)
    }
}
