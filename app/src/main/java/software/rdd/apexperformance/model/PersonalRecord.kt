package software.rdd.apexperformance.model

import kotlinx.serialization.Serializable
import software.rdd.apexperformance.core.network.ApiClient
import java.time.Instant
import java.time.temporal.ChronoUnit

// Record of a client in an exercise, calculated by the API from
// completed trainings (GET personal-records).
@Serializable
data class PersonalRecord(
    val id: String,
    val clientId: String,
    val workoutId: String,
    // Persisted JSON with the workout name translations.
    val workoutName: LocalizedText = LocalizedText(),
    val trainingId: String,
    // "MaxWeight", "EstimatedOneRepMax" or "LongestDuration" (value in
    // seconds, for workouts done for a time).
    val type: String,
    val value: Double = 0.0,
    val weight: Double = 0.0,
    val reps: Double? = null,
    val achievedAt: IsoInstant
) {
    val isMaxWeight: Boolean get() = type == "MaxWeight"
    val isOneRepMax: Boolean get() = type == "EstimatedOneRepMax"
    val isLongestDuration: Boolean get() = type == "LongestDuration"

    companion object {
        // Record history of a client, newest first. Staff pass the client,
        // clients get their own records (null).
        suspend fun load(clientId: String?): List<PersonalRecord> =
            ApiClient.get<List<PersonalRecord>>(
                if (clientId != null) "personal-records?clientId=$clientId" else "personal-records"
            ).sortedByDescending { it.achievedAt }
    }
}

// Current records of one exercise with its whole record history.
data class ExerciseRecords(
    val workoutId: String,
    val name: String,
    val maxWeight: PersonalRecord?,
    val oneRepMax: PersonalRecord?,
    // Longest set of a workout done for a time (e.g. plank).
    val longestDuration: PersonalRecord?,
    // Newest first.
    val history: List<PersonalRecord>
) {
    val lastAchievedAt: Instant get() = history.firstOrNull()?.achievedAt ?: Instant.EPOCH

    val isRecent: Boolean
        get() = !lastAchievedAt.isBefore(Instant.now().minus(RECENT_DAYS, ChronoUnit.DAYS))

    companion object {
        // Records set in this many days are marked as new, like the web.
        const val RECENT_DAYS = 14L

        // Records grouped by exercise, the latest records first.
        fun group(records: List<PersonalRecord>): List<ExerciseRecords> =
            records.groupBy { it.workoutId }
                .map { (workoutId, grouped) ->
                    val history = grouped.sortedByDescending { it.achievedAt }
                    ExerciseRecords(
                        workoutId = workoutId,
                        name = history.first().workoutName.localized,
                        // History is newest first, so the first record of a type is the current one.
                        maxWeight = history.firstOrNull { it.isMaxWeight },
                        oneRepMax = history.firstOrNull { it.isOneRepMax },
                        longestDuration = history.firstOrNull { it.isLongestDuration },
                        history = history
                    )
                }
                .sortedByDescending { it.lastAchievedAt }
    }
}
