package software.rdd.apexperformance.model

import kotlinx.serialization.Serializable
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod

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
    val exercises: List<Exercise> = emptyList()
) {
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
        val isSupersetWithPrevious: Boolean = false
    )

    @Serializable
    data class ExerciseSet(
        val id: String,
        val order: Int = 0,
        val reps: String? = null,
        val weight: Double? = null
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
    val exercises: List<Exercise>
) {
    @Serializable
    data class Exercise(
        val workout: String,
        val note: String?,
        val sets: List<ExerciseSet>,
        val isSupersetWithPrevious: Boolean
    )

    @Serializable
    data class ExerciseSet(val reps: String?, val weight: Double?)
}
