package software.rdd.apexperformance

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import software.rdd.apexperformance.core.auth.JwtDecoder
import software.rdd.apexperformance.core.auth.JwtDecoderError
import software.rdd.apexperformance.core.auth.JwtDecoderException
import java.util.Base64

// Same cases as JWTDecoderTests on iOS.
class JwtDecoderTest {

    private val now = System.currentTimeMillis() / 1000

    private fun base64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun makeToken(
        name: String = "milan",
        sub: String = "1",
        permissions: List<String> = listOf("CanGetAppointments"),
        role: String = "Coach",
        exp: Long = now + 3600
    ): String {
        val header = """{"alg":"HS256","typ":"JWT"}""".toByteArray()
        val payload = buildJsonObject {
            put("name", name)
            put("sub", sub)
            put("permissions", JsonArray(permissions.map { JsonPrimitive(it) }))
            put("role", role)
            put("exp", exp)
            put("iat", now)
            put("nbf", now)
        }.toString().toByteArray()
        return "${base64(header)}.${base64(payload)}.signature"
    }

    private fun assertError(expected: JwtDecoderError, token: String) {
        try {
            JwtDecoder.decodePayload(token)
            fail("Expected $expected")
        } catch (e: JwtDecoderException) {
            assertEquals(expected, e.error)
        }
    }

    @Test
    fun decodePayload_returnsCorrectFields() {
        val token = makeToken(name = "milan", sub = "42", permissions = listOf("CanGetClients"), role = "Coach", exp = 9999999999)

        val payload = JwtDecoder.decodePayload(token)

        assertEquals("milan", payload.name)
        assertEquals("42", payload.sub)
        assertEquals(listOf("CanGetClients"), payload.permissions)
        assertEquals("Coach", payload.role)
        assertEquals(9999999999, payload.exp)
    }

    @Test
    fun decodePayload_throwsInvalidFormat_whenNotThreeSegments() {
        assertError(JwtDecoderError.INVALID_FORMAT, "not.a.valid.jwt.token")
        assertError(JwtDecoderError.INVALID_FORMAT, "onlyonesegment")
    }

    @Test
    fun decodePayload_throwsInvalidBase64_whenPayloadSegmentIsNotBase64() {
        assertError(JwtDecoderError.INVALID_BASE64, "header.not-valid-base64!!!.signature")
    }

    @Test
    fun decodePayload_throwsInvalidJson_whenPayloadDoesNotMatchExpectedShape() {
        val badPayload = base64("""{"foo":"bar"}""".toByteArray())
        assertError(JwtDecoderError.INVALID_JSON, "header.$badPayload.signature")
    }

    @Test
    fun decodePayload_acceptsSinglePermissionAsString() {
        val payload = base64("""{"name":"ana","sub":"1","permissions":"CanGetClients","role":"Client","exp":9999999999}""".toByteArray())
        assertEquals(listOf("CanGetClients"), JwtDecoder.getUserPermissions("h.$payload.s"))
    }

    @Test
    fun isTokenValid_returnsTrue_forFutureExpiry() {
        assertTrue(JwtDecoder.isTokenValid(makeToken(exp = now + 3600)))
    }

    @Test
    fun isTokenValid_returnsFalse_forExpiredToken() {
        assertFalse(JwtDecoder.isTokenValid(makeToken(exp = now - 3600)))
    }

    @Test
    fun isTokenValid_returnsFalse_forMalformedToken() {
        assertFalse(JwtDecoder.isTokenValid("garbage"))
    }

    @Test
    fun getUserPermissions_returnsPermissionsFromToken() {
        val token = makeToken(permissions = listOf("CanGetWorkouts", "CanGetClients"))
        assertEquals(listOf("CanGetWorkouts", "CanGetClients"), JwtDecoder.getUserPermissions(token))
    }

    @Test
    fun getUserPermissions_returnsEmptyList_forMalformedToken() {
        assertEquals(emptyList<String>(), JwtDecoder.getUserPermissions("garbage"))
    }

    @Test
    fun getUserRole_returnsRoleFromToken() {
        assertEquals("Client", JwtDecoder.getUserRole(makeToken(role = "Client")))
    }

    @Test
    fun getUserRole_returnsEmptyString_forMalformedToken() {
        assertEquals("", JwtDecoder.getUserRole("garbage"))
    }

    @Test
    fun getUsername_returnsNameFromToken() {
        assertEquals("sarah", JwtDecoder.getUsername(makeToken(name = "sarah")))
    }

    @Test
    fun getUsername_returnsEmptyString_forMalformedToken() {
        assertEquals("", JwtDecoder.getUsername("garbage"))
    }
}
