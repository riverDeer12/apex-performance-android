package software.rdd.apexperformance

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.network.ApexMessagingService
import software.rdd.apexperformance.core.util.AppLanguage
import software.rdd.apexperformance.core.util.AppearanceSettings

class ApexApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        app = this

        AppearanceSettings.init(this)
        AuthManager.init(this)
        createNotificationChannel()
    }

    override fun attachBaseContext(base: Context) {
        deviceContext = base
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            ApexMessagingService.CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        // Used for string lookups outside of composables (error messages).
        lateinit var appContext: Context
            private set

        private lateinit var app: ApexApp
        // The app's context before the picked language is applied.
        private lateinit var deviceContext: Context

        // Strings outside of composables follow a newly picked language.
        fun applyLanguage() {
            val wrapped = AppLanguage.wrap(deviceContext)
            appContext = if (wrapped === deviceContext) app else wrapped
        }
    }
}
