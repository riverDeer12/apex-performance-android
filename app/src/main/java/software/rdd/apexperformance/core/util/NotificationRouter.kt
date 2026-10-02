package software.rdd.apexperformance.core.util

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import software.rdd.apexperformance.BuildConfig

enum class AppTab {
    APPOINTMENTS,
    APPOINTMENT_REQUESTS,
    WORKOUTS,
    CLIENTS,
    BODY_MEASUREMENTS,
    PROFILE
}

// Holds the tab a tapped push notification points to until the home screen
// is shown to open it, which also covers cold launches and logged-out users.
object NotificationRouter {

    var pendingTab by mutableStateOf<AppTab?>(null)

    fun handle(notificationType: String) {
        when (notificationType) {
            "appointment_request" -> pendingTab = AppTab.APPOINTMENT_REQUESTS
            "appointment_updated" -> pendingTab = AppTab.APPOINTMENTS
            "body_measurement" -> pendingTab = AppTab.BODY_MEASUREMENTS
            else -> if (BuildConfig.DEBUG) Log.w("NotificationRouter", "Unknown notification type: $notificationType")
        }
    }
}
