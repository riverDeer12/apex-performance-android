package software.rdd.apexperformance.model

import java.time.Instant
import java.util.UUID

// Trainings of clients who train at the same time with the coach ("Separate
// trainings, one time"): every client has their own training (name,
// exercises, sets, notes), all of them share one time. Each training is saved
// as a normal training of its client, so history, progress, records and
// packages work as before. The state of the session form
// (TrainingSessionScreen) without Compose, so it is unit tested.
data class TrainingSession(
    val participants: List<Participant>,
    val activeId: String = participants.first().id,
    // The shared start: when the first training's name was written.
    val startedAt: Instant? = null
) {

    // One client of the session.
    data class Participant(
        val clientId: String,
        private val first: String?,
        private val last: String?,
        val pictureUrl: String? = null,
        // The form's own id, not the client's.
        val id: String = UUID.randomUUID().toString()
    ) {
        val firstName: String get() = first?.trim().orEmpty()
        val lastName: String get() = last?.trim().orEmpty()
        val fullName: String get() = listOf(firstName, lastName).filter { it.isNotEmpty() }.joinToString(" ")
        // "Ana"; the last name when there is no first name.
        val shortName: String get() = firstName.ifEmpty { fullName }
    }

    // The set entered last in a training, for the line about the other client:
    // "Marko: last set 2 min ago (Squat 3/4)".
    data class SetActivity(
        val at: Instant,
        val exerciseName: String,
        // Sets of the exercise with something entered, and all its sets.
        val enteredSets: Int,
        val setCount: Int
    ) {
        // "Squat 3/4", "3/4" without the exercise's name.
        val summary: String
            get() = if (exerciseName.isBlank()) "$enteredSets/$setCount" else "$exerciseName $enteredSets/$setCount"
    }

    sealed interface SaveOutcome {
        data object Saved : SaveOutcome
        // No connection: saved later from the queue.
        data object Queued : SaveOutcome
        data class Failed(val message: String) : SaveOutcome
    }

    // What one Save of all trainings ended with.
    sealed interface SaveAllResult {
        // Every training is saved or kept on the device.
        data class Saved(val anyQueued: Boolean) : SaveAllResult
        // These trainings were not saved (in the session's order); the others were.
        data class Failed(val ids: List<String>) : SaveAllResult
    }

    val active: Participant get() = participants.firstOrNull { it.id == activeId } ?: participants.first()

    // More than one client: the form shows the shared time and the switcher.
    val isShared: Boolean get() = participants.size > 1

    // Its training has the coach's notes on the clients: the first one.
    val leader: Participant get() = participants.first()

    // Clients other than the shown one, for the lines under its exercises.
    val others: List<Participant> get() = participants.filter { it.id != activeId }

    fun contains(clientId: String): Boolean = participants.any { it.clientId == clientId }

    fun participant(id: String): Participant? = participants.firstOrNull { it.id == id }

    // MARK: - Clients

    // Adds a client's training and shows it; null when the client is in already.
    fun adding(participant: Participant): TrainingSession? {
        if (contains(participant.clientId)) return null
        return copy(participants = participants + participant, activeId = participant.id)
    }

    // Only with another client left.
    fun canRemove(id: String): Boolean = isShared && participant(id) != null

    // Removes a client's training; the one next to it is shown if it was.
    fun removing(id: String): TrainingSession? {
        val index = participants.indexOfFirst { it.id == id }
        if (!canRemove(id) || index < 0) return null
        val left = participants.filter { it.id != id }
        val active = if (activeId == id) left[minOf(index, left.lastIndex)].id else activeId
        return copy(participants = left, activeId = active)
    }

    fun selecting(id: String): TrainingSession =
        if (participant(id) == null) this else copy(activeId = id)

    // MARK: - Shared time

    // The first name written starts the time of all trainings; later ones
    // (and clients added later) keep it.
    fun starting(at: Instant): TrainingSession = if (startedAt != null) this else copy(startedAt = at)

    // MARK: - Texts

    // Segment of the switcher: "Ana · Upper body", "Ana" before the name is written.
    fun segmentLabel(participant: Participant, trainingName: String): String {
        val name = trainingName.trim()
        return if (name.isEmpty()) participant.shortName else "${participant.shortName} · $name"
    }

    // "Ana · Marko" in the header.
    val headerNames: String get() = participants.joinToString(" · ") { it.shortName }

    // MARK: - Save all

    // Every training was answered: all saved (or kept on the device), or
    // the ones that weren't, in the session's order.
    fun result(outcomes: Map<String, SaveOutcome>): SaveAllResult {
        val failed = participants.map { it.id }.filter { outcomes[it] is SaveOutcome.Failed }
        if (failed.isNotEmpty()) return SaveAllResult.Failed(failed)
        return SaveAllResult.Saved(anyQueued = outcomes.values.any { it == SaveOutcome.Queued })
    }

    companion object {
        // Whether a saved training's edit opens as a session: only staff, and
        // only when the API returned more than the training itself.
        fun opensAsSession(sessionSize: Int, isStaff: Boolean): Boolean = isStaff && sessionSize > 1

        // Worth asking the API for the training's session: it has a session id,
        // or it was started (older sessions are grouped by their start).
        fun mayHaveSession(sessionId: String?, startedAt: Instant?): Boolean = sessionId != null || startedAt != null

        // Asked first when something of the training would be lost.
        fun needsConfirmationToRemove(enteredSets: Int, isPersisted: Boolean): Boolean =
            enteredSets > 0 || isPersisted

        // Every training complete (name, exercises): the session can be saved.
        fun canSave(valid: List<Boolean>, isSaving: Boolean): Boolean = !isSaving && valid.isNotEmpty() && valid.all { it }
    }
}
