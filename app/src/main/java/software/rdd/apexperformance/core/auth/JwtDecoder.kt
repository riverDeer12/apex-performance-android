package software.rdd.apexperformance.core.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.util.Base64

data class JwtPayload(
    val name: String,
    val sub: String,
    val permissions: List<String>,
    val role: String,
    val exp: Long
)

enum class JwtDecoderError { INVALID_FORMAT, INVALID_BASE64, INVALID_JSON }

class JwtDecoderException(val error: JwtDecoderError) : Exception(error.name)

object JwtDecoder {

    fun decodePayload(token: String): JwtPayload {
        val segments = token.split(".")
        if (segments.size != 3) throw JwtDecoderException(JwtDecoderError.INVALID_FORMAT)

        val bytes = try {
            // Accepts both base64url (JWT) and plain base64, with or without padding.
            val normalized = segments[1].replace('-', '+').replace('_', '/').trimEnd('=')
            Base64.getDecoder().decode(normalized + "=".repeat((4 - normalized.length % 4) % 4))
        } catch (e: IllegalArgumentException) {
            throw JwtDecoderException(JwtDecoderError.INVALID_BASE64)
        }

        val obj = try {
            Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        } catch (e: Exception) {
            throw JwtDecoderException(JwtDecoderError.INVALID_JSON)
        }

        val exp = (obj["exp"] as? JsonPrimitive)?.longOrNull
        val name = obj.string("name")
        val role = obj.strings("role").firstOrNull()
        if (exp == null || name == null || role == null) {
            throw JwtDecoderException(JwtDecoderError.INVALID_JSON)
        }

        return JwtPayload(
            name = name,
            sub = obj.string("sub").orEmpty(),
            permissions = obj.strings("permissions"),
            role = role,
            exp = exp
        )
    }

    fun isTokenValid(token: String): Boolean {
        val payload = runCatching { decodePayload(token) }.getOrNull() ?: return false
        return payload.exp > System.currentTimeMillis() / 1000
    }

    fun getUserPermissions(token: String): List<String> =
        runCatching { decodePayload(token).permissions }.getOrDefault(emptyList())

    fun getUserRole(token: String): String =
        runCatching { decodePayload(token).role }.getOrDefault("")

    fun getUsername(token: String): String =
        runCatching { decodePayload(token).name }.getOrDefault("")

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    // A claim with one value comes as a string, with more values as an array.
    private fun JsonObject.strings(key: String): List<String> = when (val value: JsonElement? = this[key]) {
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(value.contentOrNull)
        else -> emptyList()
    }
}
