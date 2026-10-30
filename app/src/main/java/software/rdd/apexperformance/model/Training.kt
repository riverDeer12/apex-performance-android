package software.rdd.apexperformance.model

import kotlinx.serialization.Serializable
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

// Training a coach prepared for a client (GET trainings). Clients get
// their own trainings, coaches the trainings of their clients.
@Serializable
data class Training(
    val id: String,
    val name: String = "",
    val date: IsoInstant,
    val note: String? = null,
    val isCompleted: Boolean = false,
    val completedAt: IsoInstant? = null,
    val client: Person,
    val exercises: List<Exercise> = emptyList(),
    // When the training really started and ended, marked by the coach.
    val startedAt: IsoInstant? = null,
    val endedAt: IsoInstant? = null,
    // Client's heart rate in between, from Apple Health on the client's iPhone.
    val heartRate: HeartRate? = null
) {
    // Beats per minute.
    @Serializable
    data class HeartRate(val min: Int, val max: Int, val average: Int)

    @Serializable
    data class TimesRequest(val startedAt: String?, val endedAt: String?)

    @Serializable
    data class TimesResponse(
        val startedAt: IsoInstant? = null,
        val endedAt: IsoInstant? = null,
        val heartRate: HeartRate? = null
    )

    @Serializable
    data class Person(
        val id: String,
        val firstName: String? = null,
        val lastName: String? = null
    ) {
        val fullName: String get() = "${firstName.orEmpty()} ${lastName.orEmpty()}".trim()
    }

    @Serializable
    data class Exercise(
        val id: String,
        val workoutId: String,
        // Persisted JSON with the workout name translations.
        val workoutName: LocalizedText = LocalizedText(),
        val order: Int = 0,
        val note: String? = null,
        val sets: List<ExerciseSet> = emptyList(),
        // Done right after the previous exercise without rest (superset).
        val isSupersetWithPrevious: Boolean = false,
        // Workout's PaceType value: its sets record a distance and show a pace.
        val paceType: String? = null
    )

    @Serializable
    data class ExerciseSet(
        val id: String,
        val order: Int = 0,
        val reps: String? = null,
        val weight: Double? = null,
        // Done right after the previous set with less weight (drop set).
        val isDropSet: Boolean = false,
        // How long the set lasted, for workouts done for a time (e.g. plank).
        val durationSeconds: Int? = null,
        // Distance of the set, for workouts with a pace type (e.g. rowing).
        val distanceMeters: Int? = null
    )

    // Marks the training as completed or planned (staff only).
    // Returns the training with the new state.
    suspend fun settingCompletion(isCompleted: Boolean): Training {
        val response: CompletionResponse =
            ApiClient.request("trainings/$id/completion", HttpMethod.PUT, CompletionRequest(isCompleted))
        return copy(isCompleted = response.isCompleted, completedAt = response.completedAt)
    }

    @Serializable
    private data class CompletionRequest(val isCompleted: Boolean)

    @Serializable
    private data class CompletionResponse(val isCompleted: Boolean, val completedAt: IsoInstant? = null)

    companion object {
        // All trainings the logged user can see, newest first.
        suspend fun loadAll(): List<Training> =
            ApiClient.get<List<Training>>("trainings").sortedByDescending { it.date }

        // Completed trainings the logged user can see, newest first.
        suspend fun loadCompleted(): List<Training> = loadAll().filter { it.isCompleted }

        // Creates a training, or updates it when an id is given (staff only).
        suspend fun save(request: SaveTrainingRequest, id: String? = null): Training =
            if (id == null) ApiClient.request("trainings", HttpMethod.POST, request)
            else ApiClient.request("trainings/$id", HttpMethod.PUT, request)

        // Deletes the training (soft delete on the API, staff only).
        suspend fun delete(id: String) {
            ApiClient.requestData("trainings/$id", HttpMethod.DELETE)
        }
    }
}

// Numbers of sets in order: 1, 2... for sets and null for their drop
// sets, which belong to the set before them.
val List<Training.ExerciseSet>.setNumbers: List<Int?>
    get() = setNumbers(map { it.isDropSet })

fun setNumbers(isDropSet: List<Boolean>): List<Int?> {
    var number = 0
    return isDropSet.map { isDrop ->
        if (isDrop && number > 0) {
            null
        } else {
            number += 1
            number
        }
    }
}

// Sets without their drop sets.
val List<Training.ExerciseSet>.mainSetCount: Int
    get() = setNumbers.count { it != null }

// Duration of a set as minutes and seconds, e.g. "1:30" or "1:02:30".
fun setDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

// Pace types of workouts done over a distance (Workout.paceType).
object PaceType {
    // Pace in min/km, e.g. walking or running.
    const val PER_KM = "PerKm"
    // Pace in min/500 m, e.g. row erg or ski erg.
    const val PER_500M = "Per500m"
    // Speed in km/h, e.g. bike or air bike.
    const val KMH = "Kmh"
}

// Croatian number format with up to the given decimals, like the web: "5,25".
private fun croatianNumber(value: Double, maxFractionDigits: Int): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("hr-HR")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = maxFractionDigits
        isGroupingUsed = false
        roundingMode = RoundingMode.HALF_UP
    }.format(value)

// Distance as metres below a kilometre, otherwise kilometres: "800 m", "2 km", "5,25 km".
fun formatDistance(meters: Int?): String {
    if (meters == null || meters == 0) return ""
    return if (meters < 1000) "$meters m" else "${croatianNumber(meters / 1000.0, 2)} km"
}

// Pace of a set from its distance and duration: "5:30 /km", "1:52 /500 m" or "24,5 km/h".
fun formatPace(paceType: String?, meters: Int?, seconds: Int?): String {
    if (meters == null || seconds == null || meters <= 0 || seconds <= 0) return ""
    return formatSpeed(paceType, meters * 60.0 / seconds)
}

// Pace from a speed in metres per minute, as the fastest pace record is kept.
fun formatSpeed(paceType: String?, metersPerMinute: Double): String {
    if (metersPerMinute <= 0 || metersPerMinute.isNaN() || metersPerMinute.isInfinite()) return ""

    if (paceType == PaceType.KMH) return "${croatianNumber(metersPerMinute * 60 / 1000, 1)} km/h"

    val per = if (paceType == PaceType.PER_500M) 500 else 1000
    val seconds = Math.round(per / metersPerMinute * 60).toInt()
    return "${setDuration(seconds)} /${if (paceType == PaceType.PER_500M) "500 m" else "km"}"
}

// Parts of a set over a distance without its weight, e.g.
// ["2 km", "7:30", "1:52 /500 m"]; empty when the set has no distance.
fun distanceSetParts(set: Training.ExerciseSet, paceType: String?): List<String> {
    val meters = set.distanceMeters?.takeIf { it > 0 } ?: return emptyList()
    return listOf(
        formatDistance(meters),
        set.durationSeconds?.takeIf { it > 0 }?.let { setDuration(it) }.orEmpty(),
        formatPace(paceType, meters, set.durationSeconds),
        set.reps?.trim().orEmpty()
    ).filter { it.isNotEmpty() }
}

object SupersetLabels {
    // Labels of exercises in order: "1.", "2." for single exercises and
    // "3a", "3b" for exercises done together in a superset, like the web.
    // `linked` tells for each exercise if it is in a superset with the previous one.
    fun labels(linked: List<Boolean>): List<String> {
        val isLinked = linked.indices.map { it > 0 && linked[it] }

        val numbers = mutableListOf<Int>()
        var number = 0
        for (index in linked.indices) {
            if (!isLinked[index]) number += 1
            numbers.add(number)
        }

        return linked.indices.map { index ->
            val startsSuperset = index + 1 < linked.size && isLinked[index + 1]
            if (!isLinked[index] && !startsSuperset) return@map "${numbers[index]}."

            // Letter by position in the superset: a, b, c...
            var first = index
            while (isLinked[first]) first -= 1
            val letter = 'a' + minOf(index - first, 25)
            "${numbers[index]}$letter"
        }
    }
}

// Body of POST trainings and PUT trainings/{id}.
@Serializable
data class SaveTrainingRequest(
    val client: String,
    val name: String,
    // ISO 8601, as the API expects a DateTimeOffset.
    val date: String,
    val note: String?,
    val isCompleted: Boolean,
    val exercises: List<Exercise>,
    // Push notification to the client. The API sends it for a new
    // training and not for an edited one when this is null.
    val notifyClient: Boolean? = null
) {
    @Serializable
    data class Exercise(
        val workout: String,
        val note: String?,
        val sets: List<ExerciseSet>,
        val isSupersetWithPrevious: Boolean
    )

    @Serializable
    data class ExerciseSet(
        val reps: String?,
        val weight: Double?,
        val isDropSet: Boolean = false,
        val durationSeconds: Int? = null,
        val distanceMeters: Int? = null
    )
}
