package software.rdd.apexperformance

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import software.rdd.apexperformance.core.network.ApiException
import software.rdd.apexperformance.core.network.OfflineTrainingSync
import software.rdd.apexperformance.core.network.PendingTrainingSave
import software.rdd.apexperformance.core.network.SaveFailure
import software.rdd.apexperformance.core.network.SendResult
import software.rdd.apexperformance.core.network.TrainingRemote
import software.rdd.apexperformance.core.network.UnauthorizedException
import software.rdd.apexperformance.model.ApiErrorResponse
import software.rdd.apexperformance.model.SaveTrainingRequest
import software.rdd.apexperformance.model.Training
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

class OfflineTrainingSyncTest {

    private val request = SaveTrainingRequest(
        client = "ana",
        name = "Legs",
        date = "2026-11-16T10:00:00Z",
        note = null,
        isCompleted = false,
        exercises = emptyList(),
        notifyClient = true
    )

    private fun entry(trainingId: String? = null, timesSaved: Boolean = trainingId != null) = PendingTrainingSave(
        key = "form",
        trainingName = "Legs",
        trainingId = trainingId,
        request = request,
        startedAt = if (trainingId == null) "2026-11-16T10:00:00Z" else null,
        timesSaved = timesSaved
    )

    // Server that fails the calls it is told to.
    private class FakeRemote(var failSave: Throwable? = null, var failTimes: Throwable? = null) : TrainingRemote {
        val saves = mutableListOf<Pair<SaveTrainingRequest, String?>>()
        var started: Instant? = null

        override suspend fun save(request: SaveTrainingRequest, id: String?): Training {
            failSave?.let { throw it }
            saves.add(request to id)
            return Training(id = id ?: "created", name = request.name, date = Instant.parse(request.date), client = Training.Person(request.client))
        }

        override suspend fun markStarted(training: Training, start: Instant): Training {
            failTimes?.let { throw it }
            started = start
            return training.copy(startedAt = start)
        }
    }

    @Test
    fun classifiesConnectionErrorsAndRejections() {
        assertEquals(SaveFailure.OFFLINE, OfflineTrainingSync.classify(UnknownHostException()))
        assertEquals(SaveFailure.OFFLINE, OfflineTrainingSync.classify(SocketTimeoutException()))
        assertEquals(SaveFailure.OFFLINE, OfflineTrainingSync.classify(IOException("Connection reset")))
        assertEquals(SaveFailure.REJECTED, OfflineTrainingSync.classify(ApiException.Validation(ApiErrorResponse(400, "1101"))))
        assertEquals(SaveFailure.REJECTED, OfflineTrainingSync.classify(ApiException.Server("", 404)))
        assertEquals(SaveFailure.RETRY_LATER, OfflineTrainingSync.classify(ApiException.Server("", 503)))
        assertEquals(SaveFailure.RETRY_LATER, OfflineTrainingSync.classify(UnauthorizedException()))
    }

    @Test
    fun keepsOfflineSavesAndWaitingOnesOnly() {
        assertTrue(OfflineTrainingSync.shouldKeep(SaveFailure.OFFLINE, wasQueued = false))
        // A rejected save from the open form shows the error there, as before.
        assertFalse(OfflineTrainingSync.shouldKeep(SaveFailure.REJECTED, wasQueued = false))
        assertFalse(OfflineTrainingSync.shouldKeep(SaveFailure.RETRY_LATER, wasQueued = false))
        // A waiting one stays until the coach retries or discards it.
        assertTrue(OfflineTrainingSync.shouldKeep(SaveFailure.REJECTED, wasQueued = true))
        assertTrue(OfflineTrainingSync.shouldKeep(SaveFailure.RETRY_LATER, wasQueued = true))
    }

    @Test
    fun trainingStartedOfflineIsCreatedOnceWhenTheConnectionReturns() = runBlocking {
        val remote = FakeRemote(failSave = UnknownHostException())

        // Offline: nothing is created.
        val offline = OfflineTrainingSync.send(entry(), remote) as SendResult.Failed
        assertEquals(SaveFailure.OFFLINE, offline.failure)
        assertNull(offline.entry.trainingId)

        // Online again: created once and marked as started.
        remote.failSave = null
        val synced = OfflineTrainingSync.send(offline.entry, remote) as SendResult.Synced
        assertEquals(listOf<String?>(null), remote.saves.map { it.second })
        assertEquals(Instant.parse("2026-11-16T10:00:00Z"), remote.started)
        assertEquals("created", synced.entry.trainingId)
        assertTrue(synced.entry.timesSaved)
    }

    @Test
    fun connectionLostAfterCreatingReusesTheServersId() = runBlocking {
        val remote = FakeRemote(failTimes = SocketTimeoutException())

        val failed = OfflineTrainingSync.send(entry(), remote) as SendResult.Failed
        assertEquals(SaveFailure.OFFLINE, failed.failure)
        // Created: the id is kept and the client isn't told again.
        assertEquals("created", failed.entry.trainingId)
        assertEquals(false, failed.entry.request.notifyClient)
        assertFalse(failed.entry.timesSaved)

        remote.failTimes = null
        OfflineTrainingSync.send(failed.entry, remote) as SendResult.Synced
        // The second try updates the training, it doesn't create another one.
        assertEquals(listOf(null, "created"), remote.saves.map { it.second })
        assertTrue(remote.started != null)
    }

    @Test
    fun newerSaveOfTheFormReplacesTheWaitingOneButKeepsTheId() {
        val waiting = entry(trainingId = "created", timesSaved = false).copy(startedAt = "2026-11-16T10:00:00Z", error = "1101")
        val newer = entry().copy(request = request.copy(name = "Legs and core"))

        val merged = OfflineTrainingSync.merge(waiting, newer)

        // Last write wins...
        assertEquals("Legs and core", merged.request.name)
        // ...on the training the server already has.
        assertEquals("created", merged.trainingId)
        assertEquals(false, merged.request.notifyClient)
        assertFalse(merged.timesSaved)
        assertNull(merged.error)

        // Never created: still a new training.
        assertNull(OfflineTrainingSync.merge(entry(), newer).trainingId)
        assertNull(OfflineTrainingSync.merge(null, newer).trainingId)
        // Created by an earlier save that already left the queue.
        val known = OfflineTrainingSync.merge(null, newer, knownTrainingId = "created")
        assertEquals("created", known.trainingId)
        assertTrue(known.timesSaved)
    }

    @Test
    fun rejectedSaveKeepsTheEntryForRetry() = runBlocking {
        val remote = FakeRemote(failSave = ApiException.Validation(ApiErrorResponse(400, "1101")))
        val failed = OfflineTrainingSync.send(entry(trainingId = "t1"), remote) as SendResult.Failed
        assertEquals(SaveFailure.REJECTED, failed.failure)
        assertEquals("t1", failed.entry.trainingId)
    }
}
