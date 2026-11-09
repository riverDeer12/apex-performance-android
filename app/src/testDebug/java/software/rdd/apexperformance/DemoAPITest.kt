package software.rdd.apexperformance

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import software.rdd.apexperformance.core.auth.JwtDecoder
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.DemoAPI
import software.rdd.apexperformance.core.network.DemoAPI.Role
import software.rdd.apexperformance.model.Appointment
import software.rdd.apexperformance.model.AppointmentRequest
import software.rdd.apexperformance.model.AppointmentsStatus
import software.rdd.apexperformance.model.BodyMeasurementResponse
import software.rdd.apexperformance.model.CatalogData
import software.rdd.apexperformance.model.ChatContact
import software.rdd.apexperformance.model.ChatConversation
import software.rdd.apexperformance.model.ChatMessage
import software.rdd.apexperformance.model.Client
import software.rdd.apexperformance.model.ClientGoal
import software.rdd.apexperformance.model.Coach
import software.rdd.apexperformance.model.FunctionalMovementScreen
import software.rdd.apexperformance.model.LoginResponse
import software.rdd.apexperformance.model.MonthlyReview
import software.rdd.apexperformance.model.MyAppointmentRequest
import software.rdd.apexperformance.model.PersonalRecord
import software.rdd.apexperformance.model.Profile
import software.rdd.apexperformance.model.SentAppointmentRequest
import software.rdd.apexperformance.model.TimeSlot
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.model.TrainingTemplate
import software.rdd.apexperformance.model.UserProfile
import software.rdd.apexperformance.model.Workout
import software.rdd.apexperformance.model.WorkoutType
import software.rdd.apexperformance.ui.home.AppNotification
import software.rdd.apexperformance.ui.home.CooperationRules

// Every demo answer (store screenshots, debug builds only) decodes into the
// app's models, so the screenshots show data instead of errors. Like
// DemoAPITests on iOS.
class DemoAPITest {

    private val languages = listOf("hr", "en", "it", "de")

    private inline fun <reified T> decode(
        path: String,
        role: Role,
        language: String,
        method: String = "GET",
        body: String? = null
    ): T {
        val reply = DemoAPI.fixture(method, path, role, body, language)
        assertEquals("$method $path ($role, $language)", 200, reply.status)
        return try {
            ApiClient.json.decodeFromString<T>(reply.body.decodeToString())
        } catch (e: Exception) {
            throw AssertionError("$method $path ($role, $language) doesn't decode: ${e.message}", e)
        }
    }

    @Test
    fun everyAnswerDecodes() {
        for (language in languages) {
            for (role in Role.entries) {
                decode<Profile>("profile", role, language)
                decode<CooperationRules>("rules", role, language)
                decode<List<AppNotification>>("notifications", role, language)
                decode<List<ChatConversation>>("chat/conversations", role, language)
                decode<List<ChatContact>>("chat/contacts", role, language)
                decode<AppointmentsStatus>("appointments", role, language)
                decode<List<Training>>("trainings", role, language)
                decode<List<Workout>>("workouts", role, language)
                decode<List<WorkoutType>>("workout-types", role, language)
                decode<List<MonthlyReview>>("monthly-reviews", role, language)
                decode<List<PersonalRecord>>("personal-records", role, language)
                decode<List<FunctionalMovementScreen>>("functional-movement-screens", role, language)
                decode<List<CatalogData>>("appointment-types", role, language)
                decode<List<TimeSlot>>(
                    "time-slots/available", role, language, method = "POST",
                    body = """{"coaches":["${DemoAPI.coachId}"],"day":"2026-11-10"}"""
                )
            }

            // Client
            decode<UserProfile>("clients/current-client", Role.CLIENT, language)
            decode<Client>("clients/current-client", Role.CLIENT, language)
            decode<List<BodyMeasurementResponse>>("body-measurements", Role.CLIENT, language)
            decode<ClientGoal>("client-goals/my", Role.CLIENT, language)
            decode<List<Coach>>("coaches/client", Role.CLIENT, language)
            decode<List<MyAppointmentRequest>>("appointments/my-requests", Role.CLIENT, language)
            decode<List<SentAppointmentRequest>>("appointment-requests", Role.CLIENT, language)

            // Coach
            decode<Coach>("coaches/current-coach", Role.COACH, language)
            decode<List<Client>>("clients", Role.COACH, language)
            decode<ClientGoal>("client-goals/${DemoAPI.anaClientId}", Role.COACH, language)
            decode<List<MonthlyReview>>("monthly-reviews?clientId=${DemoAPI.anaClientId}", Role.COACH, language)
            decode<List<PersonalRecord>>("personal-records?clientId=${DemoAPI.anaClientId}", Role.COACH, language)
            decode<List<TrainingTemplate>>("training-templates", Role.COACH, language)
            decode<List<AppointmentRequest>>("appointment-requests/pending", Role.COACH, language)

            // Every conversation's messages and every workout.
            for (conversation in decode<List<ChatConversation>>("chat/conversations", Role.COACH, language)) {
                decode<List<ChatMessage>>("chat/conversations/${conversation.id}/messages?take=30", Role.COACH, language)
            }
            for (workout in decode<List<Workout>>("workouts", Role.COACH, language)) {
                decode<Workout>("workouts/${workout.id}", Role.CLIENT, language)
            }
        }
    }

    @Test
    fun screenshotsHaveContent() {
        for (language in languages) {
            // Client home: next appointment, goal; training details with sets.
            val status = decode<AppointmentsStatus>("appointments", Role.CLIENT, language)
            assertTrue(status.approvedAppointments.any { it.isActive })
            val trainings = decode<List<Training>>("trainings", Role.CLIENT, language)
            assertTrue(trainings.all { it.isCompleted })
            assertTrue(trainings.maxBy { it.date }.exercises.all { it.sets.isNotEmpty() })
            assertTrue(decode<ClientGoal>("client-goals/my", Role.CLIENT, language).goal!!.isNotEmpty())

            // Coach: pending requests, clients with measurements.
            assertEquals(2, decode<List<AppointmentRequest>>("appointment-requests/pending", Role.COACH, language).size)
            val clients = decode<List<Client>>("clients", Role.COACH, language)
            assertEquals("Ana", clients.first().firstName)
            assertTrue(clients.first().bodyMeasurements!!.size > 3)

            // Workouts of both categories.
            val workouts = decode<List<Workout>>("workouts", Role.COACH, language)
            assertEquals(2, workouts.map { it.workoutCategory }.toSet().size)
            assertTrue(workouts.all { it.name.translations.keys.containsAll(listOf("EN", "HR", "IT", "DE")) })

            // Chat with a picture and a shared workout.
            val messages = decode<List<ChatMessage>>(
                "chat/conversations/${DemoAPI.anaConversationId}/messages?take=30", Role.CLIENT, language
            )
            assertTrue(messages.any { it.isImage })
            assertTrue(messages.any { it.isWorkout && it.workout != null })
        }
    }

    @Test
    fun loginTokens() {
        val coach = decode<LoginResponse>(
            "authentication/login", Role.CLIENT, "en", method = "POST",
            body = """{"username":"demo.coach","password":"demo"}"""
        ).token
        assertTrue(JwtDecoder.isTokenValid(coach))
        assertEquals("Coach", JwtDecoder.getUserRole(coach))
        assertEquals(DemoAPI.coachUserId, JwtDecoder.getUserId(coach))

        val client = DemoAPI.token(Role.CLIENT)
        assertEquals("Client", JwtDecoder.getUserRole(client))
        assertEquals(DemoAPI.anaUserId, JwtDecoder.getUserId(client))
        assertEquals(Role.CLIENT, DemoAPI.role("Bearer $client"))
        assertEquals(Role.COACH, DemoAPI.role("Bearer $coach"))
    }

    @Test
    fun appointmentsHaveClientsAndHubIsNotFaked() {
        val status = decode<AppointmentsStatus>("appointments", Role.COACH, "en")
        val appointment: Appointment = status.approvedAppointments.first()
        assertTrue(appointment.clients.isNotEmpty())
        // The chat hub fails quietly and retries, as on iOS.
        assertEquals(404, DemoAPI.fixture("GET", "hubs/chat", Role.CLIENT).status)
    }
}
