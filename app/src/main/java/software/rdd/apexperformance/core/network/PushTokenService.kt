package software.rdd.apexperformance.core.network

import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.BuildConfig
import software.rdd.apexperformance.core.auth.AuthManager

object PushTokenService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Serializable
    private data class FcmTokenRequest(
        val token: String,
        val platform: String = "android",
        val appVersion: String = BuildConfig.VERSION_NAME,
        val buildNumber: String = BuildConfig.VERSION_CODE.toString(),
        val osVersion: String = Build.VERSION.RELEASE,
        // e.g. "Google Pixel 8"
        val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
    )

    // Push works only when the app is built with google-services.json.
    private val isAvailable: Boolean
        get() = FirebaseApp.getApps(ApexApp.appContext).isNotEmpty()

    // The backend links the FCM token to the user from the bearer token,
    // so registration is skipped until the user is logged in.
    fun register(fcmToken: String) {
        if (AuthManager.token == null) return

        scope.launch {
            try {
                ApiClient.send("fcm-tokens", HttpMethod.POST, FcmTokenRequest(token = fcmToken))
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("PushTokenService", "Failed to send FCM token to server", e)
            }
        }
    }

    fun registerCurrentToken() {
        if (!isAvailable) return
        scope.launch {
            runCatching { FirebaseMessaging.getInstance().token.await() }
                .onSuccess { register(it) }
        }
    }

    // Invalidates this device's token so the backend can no longer reach the
    // logged-out user here; the next login registers a fresh token.
    fun unregister() {
        if (!isAvailable) return
        scope.launch {
            runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
                .onFailure { if (BuildConfig.DEBUG) Log.e("PushTokenService", "Failed to delete FCM token", it) }
        }
    }
}
