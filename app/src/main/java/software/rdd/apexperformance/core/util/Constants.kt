package software.rdd.apexperformance.core.util

object Permissions {
    const val CAN_GET_CLIENTS = "CanGetClients"
    const val CAN_GET_APPOINTMENTS = "CanGetAppointments"
    const val CAN_CREATE_APPOINTMENT = "CanCreateAppointment"
    const val CAN_GET_APPOINTMENT_REQUESTS = "CanGetAppointmentRequests"
}

object Roles {
    const val CLIENT = "Client"
    const val COACH = "Coach"

    // Approving or rejecting appointments and requests is for staff only; a
    // client sees their own bookings waiting for the coach, without the buttons.
    fun canApproveAppointments(role: String): Boolean = role.isNotEmpty() && role != CLIENT
}
