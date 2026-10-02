package software.rdd.apexperformance

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import software.rdd.apexperformance.core.util.NotificationRouter
import software.rdd.apexperformance.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light status and navigation bars, the app is light only like on iOS.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Same as iOS, notification permission is asked on the first launch.
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (savedInstanceState == null) handleNotificationTap(intent)

        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationTap(intent)
    }

    // Tapped push notifications carry their "type" in the intent extras, both
    // when shown by the system (app in background) and by ApexMessagingService.
    private fun handleNotificationTap(intent: Intent?) {
        val type = intent?.getStringExtra(EXTRA_NOTIFICATION_TYPE) ?: return
        intent.removeExtra(EXTRA_NOTIFICATION_TYPE)
        NotificationRouter.handle(type)
    }

    companion object {
        const val EXTRA_NOTIFICATION_TYPE = "type"
    }
}
