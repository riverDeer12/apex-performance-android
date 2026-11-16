package software.rdd.apexperformance.model

import java.time.Instant

// Best weight a client lifted in one exercise, calculated on the device
// from the trainings the app loads (no extra API call).
data class WeightRecord(
    val workoutId: String,
    val workoutName: LocalizedText,
    val weight: Double,
    // Repetitions of the record set as written by the coach, e.g. "6".
    val reps: String?,
    // Training in which the weight was lifted first.
    val trainingId: String,
    val date: Instant
)

object TrainingHistory {

    // Trainings that were done: completed or started by the coach (planned ones don't count).
    fun isDone(training: Training): Boolean = training.isCompleted || training.startedAt != null

    // Heaviest set of an exercise that isn't a drop set (most repetitions
    // among equal weights, then the first one), or null without a weight.
    fun topSet(exercise: Training.Exercise): Training.ExerciseSet? =
        exercise.sets
            .sortedBy { it.order }
            .filter { !it.isDropSet && (it.weight ?: 0.0) > 0.0 }
            .fold(null as Training.ExerciseSet?) { best, set ->
                when {
                    best == null -> set
                    (set.weight ?: 0.0) > (best.weight ?: 0.0) -> set
                    set.weight == best.weight && repsNumber(set.reps) > repsNumber(best.reps) -> set
                    else -> best
                }
            }

    // Best weight per exercise over the given trainings of one client, the
    // most recent record first. A weight first lifted earlier and only
    // repeated later keeps the earlier date.
    fun bestWeights(trainings: List<Training>): List<WeightRecord> {
        val best = LinkedHashMap<String, WeightRecord>()
        trainings.filter(::isDone).sortedBy { it.date }.forEach { training ->
            training.exercises.forEach { exercise ->
                val top = topSet(exercise) ?: return@forEach
                val weight = top.weight ?: return@forEach
                val current = best[exercise.workoutId]
                if (current == null || weight > current.weight) {
                    best[exercise.workoutId] = WeightRecord(
                        workoutId = exercise.workoutId,
                        workoutName = exercise.workoutName,
                        weight = weight,
                        reps = top.reps?.trim()?.takeIf { it.isNotEmpty() },
                        trainingId = training.id,
                        date = training.date
                    )
                }
            }
        }
        return best.values.sortedByDescending { it.date }
    }

    // New records of a training: for each exercise whose heaviest set is
    // heavier than every earlier set of that exercise of the same client,
    // the exercise id with the id of that set. An exercise done for the
    // first time isn't a record (nothing to beat yet).
    fun newRecords(training: Training, history: List<Training>): Map<String, String> {
        if (!isDone(training)) return emptyMap()
        val earlier = history.filter {
            it.id != training.id && it.client.id == training.client.id && isDone(it) && it.date.isBefore(training.date)
        }
        val result = LinkedHashMap<String, String>()
        training.exercises.forEach { exercise ->
            val top = topSet(exercise) ?: return@forEach
            val weight = top.weight ?: return@forEach
            val previousBest = earlier
                .flatMap { previous -> previous.exercises.filter { it.workoutId == exercise.workoutId } }
                .flatMap { it.sets }
                .filter { !it.isDropSet }
                .mapNotNull { it.weight }
                .filter { it > 0.0 }
                .maxOrNull() ?: return@forEach
            if (weight > previousBest) result[exercise.id] = top.id
        }
        return result
    }

    // Training "Repeat last training" copies for a client: the latest one
    // with the name being typed when there is one, otherwise the latest one.
    // Planned trainings after now and trainings without exercises are skipped.
    fun repeatSource(
        history: List<Training>,
        clientId: String,
        typedName: String,
        excludingId: String? = null,
        now: Instant = Instant.now()
    ): Training? {
        val candidates = history
            .filter { it.client.id == clientId && it.id != excludingId && it.exercises.isNotEmpty() && !it.date.isAfter(now) }
            .sortedByDescending { it.date }
        val name = typedName.trim()
        if (name.isNotEmpty()) {
            candidates.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }?.let { return it }
        }
        return candidates.firstOrNull()
    }

    private fun repsNumber(reps: String?): Int = reps?.trim()?.toIntOrNull() ?: 0
}
