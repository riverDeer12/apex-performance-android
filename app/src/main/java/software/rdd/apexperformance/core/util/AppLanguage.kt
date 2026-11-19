package software.rdd.apexperformance.core.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.core.network.PushTokenService

// Language of the app, picked in the app (profile or login) at any time,
// kept like @AppStorage("appLanguage") on iOS. "System" follows the device.
// Android 13+ also has the app's language in the system settings (Apps →
// Apexperformance → Language): both stay the same, whichever was changed last.
enum class AppLanguage(val value: String, val nativeName: String) {
    SYSTEM("system", ""),
    HR("hr", "Hrvatski"),
    EN("en", "English"),
    IT("it", "Italiano"),
    DE("de", "Deutsch");

    companion object {
        private const val PREFERENCES = "settings"
        private const val KEY = "appLanguage"
        // The system's language for the app as the app last saw or set it.
        private const val SYSTEM_KEY = "systemAppLanguage"

        fun selected(context: Context): AppLanguage {
            val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null)
            return entries.firstOrNull { it.value == stored } ?: SYSTEM
        }

        // The picked language for a language tag ("de-DE"), System for others.
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.substringBefore('-')?.substringBefore('_')?.lowercase()
            return entries.firstOrNull { it != SYSTEM && it.value == language } ?: SYSTEM
        }

        // Context with the picked language; activities and the app wrap
        // their base context with it in attachBaseContext.
        fun wrap(base: Context): Context {
            followSystemSettings(base)
            val language = selected(base)
            if (language == SYSTEM) {
                Locale.setDefault(base.resources.configuration.locales[0])
                return base
            }
            val locale = Locale.forLanguageTag(language.value)
            // Dates and numbers follow the picked language too.
            Locale.setDefault(locale)
            val configuration = Configuration().apply { setLocale(locale) }
            return base.createConfigurationContext(configuration)
        }

        // Texts change right away, pushes come in the new language from now on.
        fun select(activity: Activity, language: AppLanguage) {
            if (language == selected(activity)) return
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(KEY, language.value).commit()
            // The system settings show the same language.
            if (Build.VERSION.SDK_INT >= 33) {
                val tags = if (language == SYSTEM) "" else language.value
                saveSystemLanguage(activity, tags)
                runCatching {
                    activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
                }
            }
            ApexApp.applyLanguage()
            PushTokenService.registerCurrentToken()
            activity.recreate()
        }

        // Android 13+: a language picked in the system settings since the app
        // last looked becomes the app's language. Android restarts the
        // activity then, which wraps its context again (wrap).
        private fun followSystemSettings(context: Context) {
            if (Build.VERSION.SDK_INT < 33) return
            val tags = runCatching {
                context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags()
            }.getOrNull() ?: return
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val seen = preferences.getString(SYSTEM_KEY, null)
            if (tags == seen) return
            val editor = preferences.edit().putString(SYSTEM_KEY, tags)
            afterSystemChange(tags, seen, hasPicked = preferences.contains(KEY))?.let { editor.putString(KEY, it.value) }
            editor.commit()
        }

        // The language to use after the system's language for the app became
        // `systemTags` (it was `seen`, null when the app hadn't looked yet),
        // or null to keep the one picked in the app: the first time (an update
        // from a version that didn't look) a language picked in the app stays.
        internal fun afterSystemChange(systemTags: String, seen: String?, hasPicked: Boolean): AppLanguage? {
            if (systemTags == seen) return null
            if (seen == null && hasPicked) return null
            return fromTag(systemTags.split(',').firstOrNull())
        }

        private fun saveSystemLanguage(context: Context, tags: String) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(SYSTEM_KEY, tags).commit()
        }
    }
}
