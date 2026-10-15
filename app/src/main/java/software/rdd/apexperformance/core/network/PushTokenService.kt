package software.rdd.apexperformance.core.network

import android.content.Context
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
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager

object PushTokenService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Last token sent to the backend. A new one replaces it there, so the
    // device doesn't get every push twice.
    private const val LAST_TOKEN_KEY = "lastRegisteredFcmToken"
    private val preferences
        get() = ApexApp.appContext.getSharedPreferences("push", Context.MODE_PRIVATE)

    @Serializable
    private data class FcmTokenRequest(
        val token: String,
        val platform: String = "android",
        val appVersion: String = BuildConfig.VERSION_NAME,
        val buildNumber: String = BuildConfig.VERSION_CODE.toString(),
        val osVersion: String = Build.VERSION.RELEASE,
        // e.g. "Google Pixel 8"
        val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
        // The backend sends pushes in the app's language ("hr", "en" or "it").
        val language: String = ApexApp.appContext.getString(R.string.app_language_code),
        val previousToken: String? = null
    )

    // Push works only when the app is built with google-services.json.
    private val isAvailable: Boolean
        get() = FirebaseApp.getApps(ApexApp.appContext).isNotEmpty()

    // The backend links the FCM token to the user from the bearer token,
    // so registration is skipped until the user is logged in.
    fun register(fcmToken: String) {
        if (AuthManager.token == null) return

        val previousToken = preferences.getString(LAST_TOKEN_KEY, null)?.takeIf { it != fcmToken }
        scope.launch {
            try {
                ApiClient.send("fcm-tokens", HttpMethod.POST, FcmTokenRequest(token = fcmToken, previousToken = previousToken))
                preferences.edit().putString(LAST_TOKEN_KEY, fcmToken).apply()
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
