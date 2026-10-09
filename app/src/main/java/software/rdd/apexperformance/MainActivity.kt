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
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.util.NotificationRouter
import software.rdd.apexperformance.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // System bars follow the appearance picked in the app, see setDarkSystemBars.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Same as iOS, notification permission is asked on the first launch.
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (savedInstanceState == null) handleNotificationTap(intent)

        setContent { AppRoot() }
    }

    // Light or dark status and navigation bar icons for the app's appearance.
    fun setDarkSystemBars(isDark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (isDark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onResume() {
        super.onResume()
        // A token can expire while the app is in the background.
        AuthManager.checkSession()
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
