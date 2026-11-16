package software.rdd.apexperformance.core.util

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import software.rdd.apexperformance.BuildConfig

enum class AppTab {
    HOME,
    APPOINTMENTS,
    APPOINTMENT_REQUESTS,
    WORKOUTS,
    CLIENTS,
    BODY_MEASUREMENTS,
    TRAININGS,
    CHAT,
    PROFILE
}

// Holds the tab a tapped push notification points to until the home screen
// is shown to open it, which also covers cold launches and logged-out users.
object NotificationRouter {

    var pendingTab by mutableStateOf<AppTab?>(null)

    // Requests open from the appointments tab when there is no requests tab.
    var opensAppointmentRequests by mutableStateOf(false)

    // Chat conversation a tapped message push opens.
    var pendingConversationId by mutableStateOf<String?>(null)

    // Training a tapped training push opens.
    var pendingTrainingId by mutableStateOf<String?>(null)

    // Appointment a tapped reminder ("Trening za 2 h") opens.
    var pendingAppointmentId by mutableStateOf<String?>(null)

    fun handle(
        notificationType: String,
        conversationId: String? = null,
        trainingId: String? = null,
        appointmentId: String? = null
    ) {
        when (notificationType) {
            // The reminder to end a training still running opens it too.
            "training", "training_end_reminder" -> {
                pendingTrainingId = trainingId
                pendingTab = AppTab.TRAININGS
            }
            "chat_message" -> {
                pendingConversationId = conversationId
                pendingTab = AppTab.CHAT
            }
            "appointment_request" -> pendingTab = AppTab.APPOINTMENT_REQUESTS
            "appointment_updated" -> pendingTab = AppTab.APPOINTMENTS
            // Two hours before an approved appointment: opens that appointment.
            "appointment_reminder" -> {
                pendingAppointmentId = appointmentId
                pendingTab = AppTab.APPOINTMENTS
            }
            "body_measurement" -> pendingTab = AppTab.BODY_MEASUREMENTS
            "client_goal", "monthly_review" -> pendingTab = AppTab.HOME
            "monthly_review_reminder" -> pendingTab = AppTab.CLIENTS
            else -> if (BuildConfig.DEBUG) Log.w("NotificationRouter", "Unknown notification type: $notificationType")
        }
    }
}
