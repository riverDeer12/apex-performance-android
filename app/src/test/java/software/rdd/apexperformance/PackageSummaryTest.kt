package software.rdd.apexperformance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.model.Appointment
import software.rdd.apexperformance.model.AppointmentStatus
import software.rdd.apexperformance.model.TimeSlot
import software.rdd.apexperformance.model.UserProfile
import software.rdd.apexperformance.ui.home.PackageSummary
import java.time.Duration
import java.time.Instant

class PackageSummaryTest {

    private val now = Instant.parse("2026-11-17T11:00:00Z")

    private fun approved(id: String, start: Instant) = Appointment(
        id = id,
        startTime = start,
        endTime = start.plus(Duration.ofHours(1)),
        timeSlot = TimeSlot(id = "slot"),
        status = AppointmentStatus(id = "approved", name = "Approved")
    )

    // GET appointments gives a client only upcoming appointments.
    private val upcoming = listOf(
        approved("a", now.plus(Duration.ofDays(1))),
        approved("b", now.plus(Duration.ofDays(3)))
    )

    @Test
    fun doneComesFromTheApiSoItIsNotAlwaysZero() {
        val summary = PackageSummary(UserProfile(credits = 6, doneThisMonth = 4), upcoming, now)

        assertEquals(4, summary.done)
        assertEquals(2, summary.reserved)
        assertEquals(6, summary.available)
        assertEquals(8, summary.remaining)
        assertEquals(12, summary.total)
    }

    @Test
    fun totalStaysTheSameWhenAnAppointmentIsOver() {
        // The day after the first one: one more done, one less booked.
        val later = now.plus(Duration.ofDays(2))
        val summary = PackageSummary(UserProfile(credits = 6, doneThisMonth = 5), upcoming, later)

        assertEquals(5, summary.done)
        assertEquals(12, summary.total)
    }

    @Test
    fun withoutTheApiCountPastApprovedAppointmentsOfThisMonth() {
        // Demo data and an older API: past appointments are in the list.
        val appointments = upcoming + approved("p", now.minus(Duration.ofDays(2)))
        val summary = PackageSummary(UserProfile(credits = 3), appointments, now)

        assertEquals(1, summary.done)
        assertEquals(6, summary.total)
    }

    @Test
    fun onlyStaffApproveOrRejectAppointments() {
        assertFalse(Roles.canApproveAppointments(Roles.CLIENT))
        assertFalse(Roles.canApproveAppointments(""))
        assertTrue(Roles.canApproveAppointments(Roles.COACH))
        assertTrue(Roles.canApproveAppointments("Administrator"))
        assertTrue(Roles.canApproveAppointments("SuperAdmin"))
    }
}
