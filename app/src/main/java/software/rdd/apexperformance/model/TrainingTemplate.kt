package software.rdd.apexperformance.model

import kotlinx.serialization.Serializable
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import java.text.Collator
import java.time.Instant
import java.time.temporal.ChronoUnit

// Training prepared once and reused (staff only). Coaches see and change
// their own templates, administrators the templates of all coaches.
@Serializable
data class TrainingTemplate(
    val id: String,
    val name: String = "",
    val note: String? = null,
    // Same exercises and sets as a training's.
    val exercises: List<Training.Exercise> = emptyList(),
    val authorName: String? = null,
    val canEdit: Boolean = false,
    val createdAt: IsoInstant,
    val updatedAt: IsoInstant
) {
    // Creates a planned training from the template for each client.
    // Returns how many trainings were created.
    suspend fun assign(clients: List<String>, date: Instant, notifyClient: Boolean = true): Int {
        val response: AssignResponse = ApiClient.request(
            "training-templates/$id/assign",
            HttpMethod.POST,
            // ISO 8601, as the API expects a DateTimeOffset.
            AssignRequest(clients, date.truncatedTo(ChronoUnit.SECONDS).toString(), notifyClient)
        )
        return response.trainingIds.size
    }

    @Serializable
    private data class AssignRequest(val clients: List<String>, val date: String, val notifyClient: Boolean)

    @Serializable
    private data class AssignResponse(val trainingIds: List<String> = emptyList())

    companion object {
        // Templates the logged coach or administrator can see, by name.
        suspend fun loadAll(): List<TrainingTemplate> {
            val collator = Collator.getInstance()
            return ApiClient.get<List<TrainingTemplate>>("training-templates")
                .sortedWith { a, b -> collator.compare(a.name, b.name) }
        }

        // Creates a template, or updates it when an id is given.
        suspend fun save(request: SaveTrainingTemplateRequest, id: String? = null): TrainingTemplate =
            if (id == null) ApiClient.request("training-templates", HttpMethod.POST, request)
            else ApiClient.request("training-templates/$id", HttpMethod.PUT, request)

        // Deletes the template (soft delete on the API).
        suspend fun delete(id: String) {
            ApiClient.requestData("training-templates/$id", HttpMethod.DELETE)
        }
    }
}

// Body of POST training-templates and PUT training-templates/{id}.
@Serializable
data class SaveTrainingTemplateRequest(
    val name: String,
    val note: String?,
    val exercises: List<SaveTrainingRequest.Exercise>
) {
    companion object {
        // Template with the exercises and sets of a training ("Save as template").
        fun from(name: String, training: Training) = SaveTrainingTemplateRequest(
            name = name,
            note = training.note,
            exercises = training.exercises
                .sortedBy { it.order }
                .map { exercise ->
                    SaveTrainingRequest.Exercise(
                        workout = exercise.workoutId,
                        note = exercise.note,
                        sets = exercise.sets
                            .sortedBy { it.order }
                            .map {
                                SaveTrainingRequest.ExerciseSet(
                                    it.reps, it.weight, it.isDropSet, it.durationSeconds, it.distanceMeters
                                )
                            },
                        isSupersetWithPrevious = exercise.isSupersetWithPrevious
                    )
                }
        )
    }
}
