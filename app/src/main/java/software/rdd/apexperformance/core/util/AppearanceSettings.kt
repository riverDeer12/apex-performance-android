package software.rdd.apexperformance.core.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import software.rdd.apexperformance.R

// App colour scheme the user picked in their profile.
enum class AppAppearance(val value: String, @StringRes val title: Int) {
    SYSTEM("system", R.string.appearance_system),
    LIGHT("light", R.string.appearance_light),
    DARK("dark", R.string.appearance_dark)
}

// Light, dark or system, kept like @AppStorage("appAppearance") on iOS.
object AppearanceSettings {
    private const val PREFERENCES = "settings"
    private const val KEY = "appAppearance"

    private var context: Context? = null

    var appearance by mutableStateOf(AppAppearance.SYSTEM)
        private set

    fun init(context: Context) {
        this.context = context
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null)
        appearance = AppAppearance.entries.firstOrNull { it.value == stored } ?: AppAppearance.SYSTEM
    }

    fun select(appearance: AppAppearance) {
        this.appearance = appearance
        context?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY, appearance.value)?.apply()
    }
}
