package software.rdd.apexperformance.core.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import software.rdd.apexperformance.ApexApp

// Resource name for a key from the iOS string catalog: lowercase,
// other characters as "_" and error codes prefixed with "err_".
fun resourceName(key: String): String {
    var name = key.lowercase().replace(Regex("[^a-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_')
    if (name.firstOrNull()?.isDigit() == true) name = "err_$name"
    return name
}

// Translation for a value that comes from the API (error codes, appointment
// and request types), or the value itself when there is none. Same as
// LocalizedStringKey(value) on iOS. Strings are kept by res/raw/keep.xml.
@SuppressLint("DiscouragedApi")
fun localizedKey(key: String, context: Context = ApexApp.appContext): String {
    if (key.isBlank()) return key
    val id = context.resources.getIdentifier(resourceName(key), "string", context.packageName)
    return if (id != 0) context.getString(id) else key
}

@Composable
fun localized(key: String): String = localizedKey(key, LocalContext.current)
