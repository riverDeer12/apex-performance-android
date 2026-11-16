package software.rdd.apexperformance.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import software.rdd.apexperformance.model.SaveTrainingRequest
import software.rdd.apexperformance.model.Training
import java.io.IOException
import java.time.Instant

// A save of the coach's training form that is waiting for the connection
// (or that the server rejected and the coach hasn't decided on yet).
// Kept in a file so it survives the app being closed.
@Serializable
data class PendingTrainingSave(
    // One open training form; its later saves replace this one (last write wins).
    val key: String,
    // Saves of another account aren't sent.
    val userId: String? = null,
    // Shown in the banner.
    val trainingName: String = "",
    // Null until the training exists on the server; then it is updated, never created again.
    val trainingId: String? = null,
    val request: SaveTrainingRequest,
    // Start of a training started in the form, marked once it is created.
    val startedAt: String? = null,
    val timesSaved: Boolean = true,
    // Message of the server's rejection: kept until the coach retries or discards it.
    val error: String? = null
)

// Why a save didn't go through.
enum class SaveFailure {
    // No connection (or it dropped / timed out): kept and sent when it is back.
    OFFLINE,
    // The server said no (4xx): kept with the error until the coach decides.
    REJECTED,
    // Server error or expired session: tried again later.
    RETRY_LATER
}

// Server calls of a save, replaced in the unit tests.
interface TrainingRemote {
    suspend fun save(request: SaveTrainingRequest, id: String?): Training
    suspend fun markStarted(training: Training, start: Instant): Training
}

object ApiTrainingRemote : TrainingRemote {
    override suspend fun save(request: SaveTrainingRequest, id: String?): Training = Training.save(request, id)
    override suspend fun markStarted(training: Training, start: Instant): Training = training.settingTimes(start, null)
}

sealed interface SendResult {
    // The entry as it ended up (with the server's id).
    val entry: PendingTrainingSave

    data class Synced(val training: Training, override val entry: PendingTrainingSave) : SendResult
    data class Failed(override val entry: PendingTrainingSave, val failure: SaveFailure, val error: Throwable) : SendResult
}

object OfflineTrainingSync {

    fun classify(error: Throwable): SaveFailure = when (error) {
        // UnknownHost, ConnectException, SocketTimeout, connection lost...
        is IOException -> SaveFailure.OFFLINE
        is UnauthorizedException -> SaveFailure.RETRY_LATER
        is ApiException.Server -> if (error.code in 400..499) SaveFailure.REJECTED else SaveFailure.RETRY_LATER
        is ApiException.Validation -> if (error.response.statusCode >= 500) SaveFailure.RETRY_LATER else SaveFailure.REJECTED
        // E.g. an unreadable response: sending it again could create the training twice.
        else -> SaveFailure.REJECTED
    }

    // Whether a failed save is kept in the queue. A save that was waiting
    // already stays (with the error when rejected); a new one is only kept
    // when there is no connection, otherwise the form shows the error as before.
    fun shouldKeep(failure: SaveFailure, wasQueued: Boolean): Boolean =
        failure == SaveFailure.OFFLINE || wasQueued

    // A newer save of the same form replaces the waiting one, but keeps what
    // the server already has: the training's id (so it isn't created twice),
    // whether its start was marked, and the client's push sent with the creation.
    fun merge(existing: PendingTrainingSave?, newer: PendingTrainingSave, knownTrainingId: String? = null): PendingTrainingSave {
        val createdId = newer.trainingId ?: existing?.trainingId ?: knownTrainingId
        if (newer.trainingId != null || createdId == null) return newer.copy(error = null)
        return newer.copy(
            trainingId = createdId,
            request = newer.request.copy(notifyClient = false),
            // Marked already unless the waiting save still had to do it.
            timesSaved = existing?.takeIf { it.trainingId != null }?.timesSaved ?: true,
            startedAt = existing?.startedAt ?: newer.startedAt,
            error = null
        )
    }

    // Sends one save: creates or updates the training and marks its start.
    // The id is remembered as soon as the training is created, so a failure
    // after that never creates it again.
    suspend fun send(entry: PendingTrainingSave, remote: TrainingRemote): SendResult {
        var current = entry
        return try {
            var saved = remote.save(current.request, current.trainingId)
            if (current.trainingId == null) {
                current = current.copy(trainingId = saved.id, request = current.request.copy(notifyClient = false))
            }
            val start = current.startedAt
            if (!current.timesSaved && start != null) {
                saved = remote.markStarted(saved, Instant.parse(start))
                current = current.copy(timesSaved = true)
            }
            SendResult.Synced(saved, current.copy(error = null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SendResult.Failed(current, classify(e), e)
        }
    }
}
