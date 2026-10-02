package software.rdd.apexperformance.core.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.model.LoginRequest
import software.rdd.apexperformance.model.LoginResponse

object AuthManager {

    private lateinit var storage: TokenStorage

    // Decrypting on every request is wasteful, so the token is kept in memory.
    var token: String? by mutableStateOf(null)
        private set

    var isAuthenticated by mutableStateOf(false)
        private set

    fun init(context: Context) {
        storage = TokenStorage(context)
        token = storage.getToken()
        validateToken()
    }

    val userPermissions: List<String>
        get() = token?.let { JwtDecoder.getUserPermissions(it) }.orEmpty()

    val userRole: String
        get() = token?.let { JwtDecoder.getUserRole(it) }.orEmpty()

    val username: String
        get() = token?.let { JwtDecoder.getUsername(it) }.orEmpty()

    fun validateToken() {
        val current = token
        if (current == null || !JwtDecoder.isTokenValid(current)) {
            logout()
            return
        }
        isAuthenticated = true
    }

    fun login(token: String) {
        storage.saveToken(token)
        this.token = token
        isAuthenticated = true
    }

    fun logout() {
        storage.deleteToken()
        token = null
        isAuthenticated = false
    }

    fun hasPermission(permission: String): Boolean = userPermissions.contains(permission)

    fun hasRole(role: String): Boolean = userRole == role

    suspend fun sendLoginRequest(username: String, password: String): LoginResponse =
        ApiClient.request("authentication/login", HttpMethod.POST, LoginRequest(username, password))
}
