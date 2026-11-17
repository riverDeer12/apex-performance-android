package software.rdd.apexperformance

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import software.rdd.apexperformance.model.ChatContact
import software.rdd.apexperformance.model.ChatParticipant
import software.rdd.apexperformance.model.Client
import software.rdd.apexperformance.model.Coach
import software.rdd.apexperformance.model.Training

// Profile picture links of other users, as the API sends them.
class UserPictureTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun pictureLinkIsDecoded() {
        val client = json.decodeFromString<Client>(
            """{"id":"c1","firstName":"Ana","lastName":"Kovač","pictureUrl":"users/u1/picture?v=1"}"""
        )
        assertEquals("users/u1/picture?v=1", client.pictureUrl)

        val coach = json.decodeFromString<Coach>("""{"id":"k1","firstName":"Luka","pictureUrl":"users/u2/picture?v=2"}""")
        assertEquals("users/u2/picture?v=2", coach.pictureUrl)

        val person = json.decodeFromString<Training.Person>("""{"id":"c1","pictureUrl":"users/u1/picture?v=1"}""")
        assertEquals("users/u1/picture?v=1", person.pictureUrl)

        val contact = json.decodeFromString<ChatContact>(
            """{"id":"k1","userId":"u2","fullName":"Luka Marić","pictureUrl":"users/u2/picture?v=2"}"""
        )
        assertEquals("users/u2/picture?v=2", contact.pictureUrl)
    }

    @Test
    fun noPictureOrOlderApiDecodesToNull() {
        val participant = json.decodeFromString<ChatParticipant>("""{"id":"c1","userId":"u1","fullName":"Ana Kovač"}""")
        assertNull(participant.pictureUrl)

        val client = json.decodeFromString<Client>("""{"id":"c1","firstName":"Ana","lastName":"Kovač","pictureUrl":null}""")
        assertNull(client.pictureUrl)
    }
}
