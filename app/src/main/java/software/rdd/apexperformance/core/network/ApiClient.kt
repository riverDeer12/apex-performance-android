package software.rdd.apexperformance.core.network

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import software.rdd.apexperformance.BuildConfig
import software.rdd.apexperformance.core.AppEnvironment
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.model.ApiErrorResponse
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

sealed class ApiException(message: String) : Exception(message) {
    class Validation(val response: ApiErrorResponse) : ApiException(response.message)
    class Server(message: String) : ApiException(message)
}

class UnauthorizedException : Exception("Unauthorized")

object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json".toMediaType()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend inline fun <reified T> get(path: String): T =
        decode(requestData(path))

    suspend inline fun <reified B, reified T> request(path: String, method: HttpMethod, body: B): T =
        decode(requestData(path, method, jsonBody(body)))

    // For endpoints whose response body isn't needed.
    suspend inline fun <reified B> send(path: String, method: HttpMethod, body: B) {
        requestData(path, method, jsonBody(body))
    }

    inline fun <reified B> jsonBody(body: B): RequestBody =
        json.encodeToString(body).toRequestBody("application/json".toMediaType())

    inline fun <reified T> decode(data: ByteArray): T = try {
        json.decodeFromString<T>(data.decodeToString())
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e("ApiClient", "Decoding error", e)
        throw e
    }

    // Raw response body, for endpoints that return files or no content.
    suspend fun requestData(
        path: String,
        method: HttpMethod = HttpMethod.GET,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap()
    ): ByteArray {
        val token = AuthManager.token
        val url = AppEnvironment.apiUrl(path)

        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
        token?.let { builder.header("Authorization", "Bearer $it") }
        headers.forEach { (key, value) -> builder.header(key, value) }

        val requestBody = body ?: if (method == HttpMethod.GET || method == HttpMethod.DELETE) null
        else ByteArray(0).toRequestBody(jsonMediaType)
        builder.method(method.name, requestBody)

        if (BuildConfig.DEBUG) Log.d("ApiClient", "${method.name} $url")

        val response = try {
            http.newCall(builder.build()).await()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("ApiClient", "Request failed: $url", e)
            throw e
        }

        val data = response.body
        if (BuildConfig.DEBUG) Log.d("ApiClient", "Response ${response.code}")

        if (response.code == 401) {
            // The token expired or was revoked, so the user has to log in again.
            if (token != null) AuthManager.logout()
            throw UnauthorizedException()
        }

        if (!response.isSuccessful) {
            val apiError = runCatching {
                json.decodeFromString<ApiErrorResponse>(data.decodeToString())
            }.getOrNull()
            throw if (apiError != null) ApiException.Validation(apiError)
            else ApiException.Server("")
        }

        return data
    }
}

class RawResponse(val code: Int, val body: ByteArray) {
    val isSuccessful: Boolean get() = code in 200..299
}

// The body is read on OkHttp's thread, since reading it on the main
// thread can hit the network (NetworkOnMainThreadException).
suspend fun Call.await(): RawResponse = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            val result = try {
                response.use { RawResponse(it.code, it.body?.bytes() ?: ByteArray(0)) }
            } catch (e: IOException) {
                onFailure(call, e)
                return
            }
            continuation.resume(result)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
    })
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
