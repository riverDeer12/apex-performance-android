package software.rdd.apexperformance.core.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.core.network.PushTokenService

// Language of the app, picked in the app (profile or login) at any time,
// kept like @AppStorage("appLanguage") on iOS. "System" follows the device.
enum class AppLanguage(val value: String, val nativeName: String) {
    SYSTEM("system", ""),
    HR("hr", "Hrvatski"),
    EN("en", "English"),
    IT("it", "Italiano");

    companion object {
        private const val PREFERENCES = "settings"
        private const val KEY = "appLanguage"

        fun selected(context: Context): AppLanguage {
            val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null)
            return entries.firstOrNull { it.value == stored } ?: SYSTEM
        }

        // Context with the picked language; activities and the app wrap
        // their base context with it in attachBaseContext.
        fun wrap(base: Context): Context {
            val language = selected(base)
            if (language == SYSTEM) {
                Locale.setDefault(base.resources.configuration.locales[0])
                return base
            }
            val locale = Locale.forLanguageTag(language.value)
            // Dates and numbers follow the picked language too.
            Locale.setDefault(locale)
            val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
            return base.createConfigurationContext(configuration)
        }

        // Texts change right away, pushes come in the new language from now on.
        fun select(activity: Activity, language: AppLanguage) {
            if (language == selected(activity)) return
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(KEY, language.value).commit()
            ApexApp.applyLanguage()
            PushTokenService.registerCurrentToken()
            activity.recreate()
        }
    }
}
