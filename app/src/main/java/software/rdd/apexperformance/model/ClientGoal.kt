package software.rdd.apexperformance.model

import kotlinx.serialization.Serializable
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import java.time.LocalDate

// Client's goal and plan, written by the coach (api/client-goals).
// All fields are empty while the coach hasn't written it yet.
@Serializable
data class ClientGoal(
    val goal: String? = null,
    val currentBlock: String? = null,
    val focus: String? = null,
    val nextAssessment: String? = null,
    val nextAssessmentDate: IsoInstant? = null,
    val updatedAt: IsoInstant? = null
) {
    val isEmpty: Boolean
        get() = listOf(goal, currentBlock, focus, nextAssessment).all { it.isNullOrEmpty() } &&
            nextAssessmentDate == null

    // Saves the goal and plan of a client (staff).
    suspend fun save(clientId: String): ClientGoal =
        ApiClient.request(
            "client-goals/$clientId",
            HttpMethod.PUT,
            Request(goal, currentBlock, focus, nextAssessment, nextAssessmentDate?.toString())
        )

    // Only the editable fields are sent, the date as ISO 8601 for the API.
    @Serializable
    private data class Request(
        val goal: String?,
        val currentBlock: String?,
        val focus: String?,
        val nextAssessment: String?,
        val nextAssessmentDate: String?
    )

    companion object {
        // Goal and plan of the logged client.
        suspend fun loadMine(): ClientGoal = ApiClient.get("client-goals/my")

        // Goal and plan of a client (staff).
        suspend fun load(clientId: String): ClientGoal = ApiClient.get("client-goals/$clientId")
    }
}

// Coach's review of a client's month (api/monthly-reviews).
@Serializable
data class MonthlyReview(
    val id: String,
    val clientId: String,
    val year: Int,
    val month: Int,
    val content: String = "",
    val updatedAt: IsoInstant
) {
    // First day of the reviewed month, for formatting.
    val monthDate: LocalDate
        get() = runCatching { LocalDate.of(year, month, 1) }.getOrElse { LocalDate.now() }

    @Serializable
    private data class SaveRequest(val client: String, val year: Int, val month: Int, val content: String)

    companion object {
        // Reviews newest month first. Clients get their own, staff pass the client.
        suspend fun load(clientId: String? = null): List<MonthlyReview> =
            ApiClient.get(if (clientId != null) "monthly-reviews?clientId=$clientId" else "monthly-reviews")

        // Writes or updates the review of a client's month (staff).
        suspend fun save(clientId: String, year: Int, month: Int, content: String): MonthlyReview =
            ApiClient.request("monthly-reviews", HttpMethod.PUT, SaveRequest(clientId, year, month, content))

        suspend fun delete(id: String) {
            ApiClient.requestData("monthly-reviews/$id", HttpMethod.DELETE)
        }
    }
}
