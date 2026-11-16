package software.rdd.apexperformance.core.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import software.rdd.apexperformance.model.Appointment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// What the home screen widget shows, written by the app whenever it loads
// home or appointments (and on login and logout). The widget only reads it.
@Serializable
data class WidgetSnapshot(
    val role: String,
    // Client: next appointment. Coach: next appointment today.
    val nextStart: Long? = null,
    val nextEnd: Long? = null,
    // Client: requested, not approved yet.
    val nextIsPending: Boolean = false,
    // Client: the coach; coach: the client.
    val personName: String? = null,
    // Client's package, e.g. 5 / 12.
    val packageDone: Int? = null,
    val packageTotal: Int? = null,
    // Coach: requests waiting for approval.
    val pendingRequests: Int? = null
) {
    companion object {
        const val LOGGED_OUT = "loggedOut"
        // Logged in, nothing loaded yet.
        const val LOGGED_IN = "loggedIn"
        const val CLIENT = "client"
        const val STAFF = "staff"

        private val json = Json { ignoreUnknownKeys = true }

        fun encode(snapshot: WidgetSnapshot): String = json.encodeToString(serializer(), snapshot)

        fun decode(text: String?): WidgetSnapshot? =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }

        // Coach: the appointment of today that is next or still going on.
        fun nextToday(appointments: List<Appointment>, now: Instant, zone: ZoneId): Appointment? {
            val today: LocalDate = now.atZone(zone).toLocalDate()
            return appointments
                .filter { it.startTime.atZone(zone).toLocalDate() == today && it.endTime.isAfter(now) }
                .distinctBy { it.id }
                .minByOrNull { it.startTime }
        }
    }
}
