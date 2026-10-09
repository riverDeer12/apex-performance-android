package software.rdd.apexperformance.model

import androidx.annotation.StringRes
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import java.time.Instant

// MARK: - Auth

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val token: String)

@Serializable
data class ApiErrorResponse(
    val statusCode: Int = 0,
    val message: String = "",
    val errors: Map<String, List<String>>? = null
)

@Serializable
data class StatusResponse(val id: String)

// MARK: - Appointments

@Serializable
data class TimeSlot(
    val id: String,
    val name: String? = null,
    val day: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val description: String? = null,
    val isTaken: Boolean? = null,
    val appointmentId: String? = null
)

@Serializable
data class AppointmentStatus(
    val id: String,
    val name: String,
    val description: String = ""
)

@Serializable
data class Appointment(
    val id: String,
    val startTime: IsoInstant,
    val endTime: IsoInstant,
    val timeSlot: TimeSlot,
    val status: AppointmentStatus,
    val clients: List<Client> = emptyList()
) {
    val isActive: Boolean
        get() = status.name == BusinessStatus.APPROVED && startTime.isAfter(Instant.now())

    val isCompleted: Boolean
        get() = !isActive && status.name == BusinessStatus.APPROVED
}

object BusinessStatus {
    const val APPROVED = "Approved"
    const val PENDING = "Pending"
    const val CANCELLED = "Cancelled"
}

@Serializable
data class AppointmentsStatus(
    val approvedAppointments: List<Appointment> = emptyList(),
    val pendingAppointments: List<Appointment> = emptyList(),
    val inProgressAppointments: List<Appointment> = emptyList()
)

@Serializable
data class CatalogData(
    val id: String,
    val name: String,
    val description: String = ""
)

@Serializable
data class AppointmentRequest(
    val id: String,
    val comment: String = "",
    val sender: Client,
    val type: CatalogData,
    val appointment: Appointment,
    val createdAt: IsoInstant
)

// Request as returned by GET appointment-requests (all requests with
// their status). Clients use it to follow requests they have sent.
@Serializable
data class SentAppointmentRequest(
    val id: String,
    val comment: String = "",
    val type: CatalogData,
    val status: Status,
    val appointment: Appointment,
    val createdAt: IsoInstant,
    val updatedAt: IsoInstant
) {
    @Serializable
    data class Status(val name: String)

    companion object {
        const val NEW_APPOINTMENT_TYPE_NAME = "NewAppointment"

        // Shows a requested appointment in the same list as other requests.
        fun fromNewAppointment(request: MyAppointmentRequest): SentAppointmentRequest {
            // An appointment that is in progress was approved.
            val statusName = if (request.appointment.status.name == "InProgress") "Approved"
            else request.appointment.status.name

            return SentAppointmentRequest(
                id = request.appointment.id,
                comment = "",
                type = CatalogData(request.appointment.id, NEW_APPOINTMENT_TYPE_NAME, ""),
                status = Status(statusName),
                appointment = request.appointment,
                createdAt = request.createdAt,
                updatedAt = request.updatedAt
            )
        }
    }
}

// Appointment the client requested (created) themselves, from
// GET appointments/my-requests. Its status is the request status.
@Serializable
data class MyAppointmentRequest(
    val appointment: Appointment,
    val createdAt: IsoInstant,
    val updatedAt: IsoInstant
)

@Serializable
data class CancelationRequest(val comment: String)

@Serializable
data class CreateAppointmentRequest(
    val type: String,
    val timeSlot: String,
    val location: String? = null,
    val clients: List<String>,
    val coaches: List<String>,
    val startTime: String,
    val endTime: String
)

@Serializable
data class GetTimeSlotsRequest(val coaches: List<String>, val day: String)

// MARK: - Clients and coaches

@Serializable
data class Client(
    val id: String,
    val firstName: String = "",
    val lastName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val credits: Int? = null,
    val bodyMeasurements: List<BodyMeasurement>? = null,
    val lastCreditsIncrease: IsoInstant? = null,
    // One of ClientPlan values.
    val plan: String? = null
) {
    val fullName: String get() = "$firstName $lastName"

    val isOutOfCredits: Boolean get() = (credits ?: 0) < 1
}

// How the client trains, same values as the API (ClientPlans).
enum class ClientPlan(val value: String, @StringRes val title: Int) {
    PRIVATE_COACHING("PrivateCoaching", R.string.plan_private_coaching),
    ONLINE_COACHING("OnlineCoaching", R.string.plan_online_coaching),
    MEMBERSHIP("Membership", R.string.plan_membership);

    companion object {
        fun from(value: String?): ClientPlan? = entries.firstOrNull { it.value == value }
    }
}

@Serializable
data class UpdateClientRequest(
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val credits: Int?,
    val plan: String?
)

@Serializable
data class CreateClientRequest(
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val credits: Int?,
    val coaches: List<String>,
    val plan: String
)

@Serializable
data class Coach(
    val id: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null
) {
    // Null when the coach has no name, shown as "Unknown Coach".
    val fullName: String?
        get() = "${firstName.orEmpty()} ${lastName.orEmpty()}".trim().ifEmpty { null }
}

// Logged-in client's data (clients/current-client).
@Serializable
data class UserProfile(
    val id: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val email: String? = null,
    val credits: Int? = null,
    val plan: String? = null
)

// MARK: - Profile

// Profile of the logged user (api/profile), same for all roles.
@Serializable
data class Profile(
    val userId: String,
    val username: String,
    val email: String = "",
    val roles: List<String> = emptyList(),
    // Client, Coach, Administrator or null when user
    // has no personal data (e.g. only super admin).
    val profileType: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val hasProfilePicture: Boolean = false,
    // Kept as a string, only used to reload the picture when it changes.
    val profilePictureUpdatedAt: String? = null
) {
    val hasPersonalData: Boolean get() = profileType != null

    val hasPhone: Boolean get() = profileType == "Client" || profileType == "Coach"
}

@Serializable
data class UpdateProfileRequest(
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val phone: String?
)

@Serializable
data class ChangePasswordRequest(val newPassword: String)

@Serializable
data class ChangeUsernameRequest(val username: String)

// MARK: - Body measurements

@Serializable
data class BodyMeasurement(
    val id: String,
    val height: Double = 0.0,
    val weight: Double = 0.0,
    val shoulders: Double = 0.0,
    val chest: Double = 0.0,
    val upperArm: Double = 0.0,
    val waist: Double = 0.0,
    val thigh: Double = 0.0,
    val calves: Double = 0.0,
    val glutes: Double = 0.0,
    val measuredAt: IsoInstant,
    val client: Client? = null
)

// GET body-measurements returns only the logged-in client's own
// measurements, with the measurement date sent as createdAt.
@Serializable
data class BodyMeasurementResponse(
    val id: String,
    val height: Double = 0.0,
    val weight: Double = 0.0,
    val shoulders: Double = 0.0,
    val chest: Double = 0.0,
    val upperArm: Double = 0.0,
    val waist: Double = 0.0,
    val thigh: Double = 0.0,
    val calves: Double = 0.0,
    val glutes: Double = 0.0,
    val createdAt: IsoInstant
) {
    fun toBodyMeasurement() = BodyMeasurement(
        id, height, weight, shoulders, chest, upperArm, waist, thigh, calves, glutes,
        measuredAt = createdAt
    )
}

@Serializable
data class BodyMeasurementValues(
    val height: Double,
    val weight: Double,
    val shoulders: Double,
    val chest: Double,
    val upperArm: Double,
    val waist: Double,
    val thigh: Double,
    val calves: Double,
    val glutes: Double
)

@Serializable
data class CreateBodyMeasurementRequest(
    val client: String,
    val height: Double,
    val weight: Double,
    val shoulders: Double,
    val chest: Double,
    val upperArm: Double,
    val waist: Double,
    val thigh: Double,
    val calves: Double,
    val glutes: Double
)

// MARK: - Functional Movement Screen

// Functional Movement Screen (FMS) of a client. Each test
// result is free text, same as on the web.
@Serializable
data class FunctionalMovementScreen(
    val id: String,
    val deepSquat: String = "",
    val hurdleStep: String = "",
    val inLineLunge: String = "",
    val activeStraightLegRaise: String = "",
    val trunkStabilityPushUp: String = "",
    val rotaryStability: String = "",
    val shoulderMobility: String = "",
    val xTest: String? = null,
    val description: String? = null,
    val createdAt: IsoInstant,
    val client: Person
) {
    @Serializable
    data class Person(
        val id: String,
        val firstName: String? = null,
        val lastName: String? = null
    )
}

// Body of both create (POST) and update (PUT) requests.
@Serializable
data class FunctionalMovementScreenRequest(
    val client: String,
    val deepSquat: String,
    val hurdleStep: String,
    val inLineLunge: String,
    val activeStraightLegRaise: String,
    val trunkStabilityPushUp: String,
    val rotaryStability: String,
    val shoulderMobility: String,
    val xTest: String,
    val description: String?
)

// MARK: - Workouts

@Serializable
data class Workout(
    val id: String,
    val name: LocalizedText = LocalizedText(),
    val description: LocalizedText = LocalizedText(),
    val thumbnailUrl: String? = null,
    val videoUrl: String? = null,
    val workoutTypes: List<WorkoutType> = emptyList()
) {
    // Every translation is searched, so the query language does not matter.
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true

        val searchableValues = name.allValues + description.allValues +
            workoutTypes.flatMap { it.name.allValues }

        return searchableValues.any { it.contains(q, ignoreCase = true) }
    }
}

@Serializable
data class WorkoutType(
    val id: String,
    val name: LocalizedText = LocalizedText()
)

@Serializable
data class ImportWorkoutsResponse(
    val id: String? = null,
    val status: Boolean = false,
    val queuedWorkoutsCount: Int = 0
)

@Serializable
data class UpdateWorkoutRequest(
    val name: LocalizedText,
    val description: LocalizedText,
    val thumbnailUrl: String,
    val videoUrl: String,
    val workoutTypes: List<String>
)

/** Mirrors the API's LocalizedProperty, translations keyed by language code (HR, EN, IT). */
@Serializable(with = LocalizedTextSerializer::class)
data class LocalizedText(val translations: Map<String, String> = emptyMap()) {

    fun value(languageCode: String): String? =
        translations.entries.firstOrNull {
            it.key.equals(languageCode, ignoreCase = true) && it.value.isNotEmpty()
        }?.value

    // Uses the same language the app's own strings are shown in.
    val localized: String
        get() = value(ApexApp.appContext.getString(R.string.app_language_code))
            ?: value("HR")
            ?: translations.values.firstOrNull { it.isNotEmpty() }
            ?: ""

    val allValues: List<String> get() = translations.values.toList()
}

object LocalizedTextSerializer : KSerializer<LocalizedText> {
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): LocalizedText {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when (element) {
            is JsonObject -> {
                val translations = element["translations"]
                if (translations is JsonObject) LocalizedText(stringMap(translations))
                else LocalizedText()
            }
            // Workout types nested in a workout come back as the persisted JSON string.
            is JsonPrimitive -> {
                val string = element.contentOrNull ?: return LocalizedText()
                runCatching { LocalizedText(Json.decodeFromString(mapSerializer, string)) }
                    .getOrElse { LocalizedText(mapOf("HR" to string)) }
            }
            else -> LocalizedText()
        }
    }

    override fun serialize(encoder: Encoder, value: LocalizedText) {
        (encoder as JsonEncoder).encodeJsonElement(buildJsonObject {
            putJsonObject("translations") {
                value.translations.forEach { (key, text) -> put(key, text) }
            }
        })
    }

    private fun stringMap(obj: JsonObject): Map<String, String> =
        obj.mapNotNull { (key, value) ->
            if (value is JsonNull) null else (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
        }.toMap()
}

// MARK: - Workout library and deletes

// Which workouts a client can see in the library, agreed with the coach.
enum class WorkoutLibraryAccess {
    // Private coaching: only mobility and stretching workouts.
    MOBILITY_AND_STRETCHING,
    // Online coaching: all workouts.
    ALL,
    // Membership or no plan: no library.
    NONE;

    fun allows(workout: Workout): Boolean = when (this) {
        MOBILITY_AND_STRETCHING -> workout.isMobilityOrStretching
        ALL -> true
        NONE -> false
    }

    companion object {
        fun from(plan: String?): WorkoutLibraryAccess = when (ClientPlan.from(plan)) {
            ClientPlan.PRIVATE_COACHING -> MOBILITY_AND_STRETCHING
            ClientPlan.ONLINE_COACHING -> ALL
            ClientPlan.MEMBERSHIP, null -> NONE
        }
    }
}

// Parts of workout type names, in any language, for mobility and stretching.
private val mobilityAndStretchingKeywords = listOf("mobil", "stretch", "istez", "fleksib", "flexib", "allung")

// True when one of the workout's types is mobility or stretching.
val Workout.isMobilityOrStretching: Boolean
    get() = workoutTypes.any { type ->
        type.name.allValues.any { name ->
            val lowercased = name.lowercase()
            mobilityAndStretchingKeywords.any { lowercased.contains(it) }
        }
    }

// Deletes the workout (soft delete on the API, staff only).
suspend fun deleteWorkout(id: String) {
    ApiClient.requestData("workouts/$id", HttpMethod.DELETE)
}

// Wheel range and starting value of a body measurement, by its title key.
data class MeasurementWheel(val range: IntRange, val start: Double)

fun measurementWheel(key: String): MeasurementWheel = when (key) {
    "height" -> MeasurementWheel(100..230, 175.0)
    "weight" -> MeasurementWheel(30..250, 75.0)
    "shoulders" -> MeasurementWheel(60..180, 110.0)
    "chest" -> MeasurementWheel(50..180, 100.0)
    "upper_arm" -> MeasurementWheel(15..70, 32.0)
    "waist" -> MeasurementWheel(40..180, 85.0)
    "thigh" -> MeasurementWheel(30..100, 55.0)
    "calves" -> MeasurementWheel(20..70, 37.0)
    "glutes" -> MeasurementWheel(50..180, 100.0)
    else -> MeasurementWheel(0..300, 0.0)
}
