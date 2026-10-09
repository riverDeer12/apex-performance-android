package software.rdd.apexperformance.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.util.localizedKey
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// API errors come as codes (e.g. "1102"), translated the same way the iOS
// app does it through the string catalog.
fun mapError(error: Throwable): String {
    val context = ApexApp.appContext

    return when (error) {
        is UnknownHostException -> context.getString(R.string.error_no_internet)
        is SocketTimeoutException -> context.getString(R.string.error_timed_out)
        is IOException -> context.getString(R.string.error_network)
        // The user is taken to login, which explains it.
        is UnauthorizedException -> context.getString(R.string.session_expired)
        is ApiException.Validation -> {
            val response = error.response
            val messages = response.errors.orEmpty().flatMap { (key, values) ->
                values.map { value ->
                    val message = localizedKey(value)
                    // General errors aren't tied to a form field.
                    if (key.equals("generalErrors", ignoreCase = true)) message
                    else "${key.replaceFirstChar { it.uppercase() }}: $message"
                }
            }
            if (messages.isNotEmpty()) messages.joinToString("\n")
            else localizedKey(response.message)
        }
        is ApiException.Server -> context.getString(R.string.error_unknown_server)
        is SerializationException, is IllegalArgumentException ->
            context.getString(R.string.error_unexpected_response)
        else -> context.getString(R.string.error_generic)
    }
}

// Request was cancelled because the screen went away or a refresh
// started again. Not a real error, so nothing should be shown.
val Throwable.isCancellation: Boolean
    get() = this is CancellationException
