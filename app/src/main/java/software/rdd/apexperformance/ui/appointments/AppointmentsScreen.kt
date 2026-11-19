package software.rdd.apexperformance.ui.appointments

import software.rdd.apexperformance.ui.components.AvatarWithBadge
import software.rdd.apexperformance.ui.components.UserAvatar
import software.rdd.apexperformance.ui.components.UserAvatarStack
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.NotificationRouter
import software.rdd.apexperformance.core.util.Permissions
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.Appointment
import software.rdd.apexperformance.model.AppointmentRequest
import software.rdd.apexperformance.model.AppointmentsStatus
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.core.widget.ApexWidget
import software.rdd.apexperformance.core.widget.WidgetSnapshot
import software.rdd.apexperformance.ui.components.EmptyStateView
import software.rdd.apexperformance.ui.trainings.trainingEditScreen
import java.time.Instant
import software.rdd.apexperformance.ui.components.ActionButton
import software.rdd.apexperformance.ui.components.ApexLabel
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import software.rdd.apexperformance.ui.components.ApexScreenHeader
import software.rdd.apexperformance.ui.components.Badge
import software.rdd.apexperformance.ui.components.CalendarMonth
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.IconCircle
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.ToolbarIcon
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.home.NotificationBell
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.LocalDate
import software.rdd.apexperformance.ui.components.ShowMoreItems

class AppointmentsScreen : Screen() {

    private var appointments by mutableStateOf<List<Appointment>>(emptyList())
    private var pendingAppointments by mutableStateOf<List<Appointment>>(emptyList())
    private var inProgressAppointments by mutableStateOf<List<Appointment>>(emptyList())
    // Staff: trainings of their clients, for "Danas" (trainings in progress).
    private var trainings by mutableStateOf<List<Training>>(emptyList())
    // Pending cancelation and join requests, shown to staff above the calendar.
    private var appointmentRequests by mutableStateOf<List<AppointmentRequest>>(emptyList())
    private var isInitialLoading by mutableStateOf(false)
    private var processingAppointmentId by mutableStateOf<String?>(null)
    private var processingRequestId by mutableStateOf<String?>(null)
    private var isGeneratingRecurring by mutableStateOf(false)
    private var selectedDate by mutableStateOf(LocalDate.now())

    private val isClient: Boolean get() = AuthManager.hasRole(Roles.CLIENT)

    // Staff only: approve / reject buttons and the pending requests.
    private val canManageRequests: Boolean get() = Roles.canApproveAppointments(AuthManager.userRole)

    private val showsRequestsButton: Boolean
        get() = isClient || AuthManager.hasPermission(Permissions.CAN_GET_APPOINTMENT_REQUESTS)

    private val cancelationRequests: List<AppointmentRequest>
        get() = appointmentRequests.filter { it.type.name.lowercase() == "cancelationrequest" }

    private val joinRequests: List<AppointmentRequest>
        get() = appointmentRequests.filter { it.type.name.lowercase() == "joinrequest" }

    private val appointmentsForSelectedDate: List<Appointment>
        get() = appointments
            .filter { DateFormats.localDate(it.startTime) == selectedDate }
            .sortedBy { it.startTime }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        // Reloads every time the screen is shown, like .task on iOS.
        LaunchedEffect(Unit) { loadData(showInitialSpinner = true) }

        // The reminder before an appointment opens it.
        val pendingAppointmentId = NotificationRouter.pendingAppointmentId
        LaunchedEffect(pendingAppointmentId) {
            val id = pendingAppointmentId ?: return@LaunchedEffect
            NotificationRouter.pendingAppointmentId = null
            if (findAppointment(id) == null) loadData(showInitialSpinner = false)
            val appointment = findAppointment(id)
            if (appointment != null) {
                navigator.push(AppointmentDetailsScreen(appointment))
            } else {
                ToastManager.show(R.string.appointment_not_found, ToastType.INFO)
            }
        }

        // A request notification opens the requests from here when there is no requests tab.
        LaunchedEffect(NotificationRouter.opensAppointmentRequests) {
            if (NotificationRouter.opensAppointmentRequests) {
                NotificationRouter.opensAppointmentRequests = false
                navigator.push(AppointmentRequestsScreen())
            }
        }

        ScrollScreen(
            topBar = {
                TopBar {
                    // Clients have the bell on home.
                    if (!isClient) NotificationBell()
                    if (showsRequestsButton) {
                        // Clients: requests they sent, with their status.
                        // Staff: requests waiting for approval.
                        ToolbarIcon(Icons.Outlined.PendingActions, stringResource(R.string.appointment_requests)) {
                            navigator.push(AppointmentRequestsScreen())
                        }
                    }
                    if (AuthManager.hasRole(Roles.COACH)) {
                        ToolbarIcon(
                            Icons.Filled.Autorenew,
                            stringResource(R.string.generate_recurring_appointments),
                            enabled = !isGeneratingRecurring
                        ) { launch { generateRecurringAppointments() } }
                    }
                }
            },
            showLoading = isInitialLoading && appointments.isEmpty(),
            onRefresh = { loadData(showInitialSpinner = false) },
            overlay = { if (isGeneratingRecurring) GeneratingOverlay() }
        ) {
            ApexScreenHeader(stringResource(R.string.appointments), stringResource(R.string.upcoming_past_sessions))

            // The only way to add an appointment, there is no + in the corner.
            ApexPrimaryButton(
                stringResource(if (isClient) R.string.book_appointment else R.string.new_appointment),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) { navigator.push(CreateAppointmentScreen()) }

            // Staff: today's appointments and trainings in progress first. The
            // pending requests below belong to it (with an empty state).
            if (!isClient) {
                TodayOverview(
                    appointments = todayAppointments,
                    trainings = trainings,
                    isLoading = isInitialLoading,
                    onOpenAppointment = { navigator.push(AppointmentDetailsScreen(it)) },
                    onOpenTraining = { training ->
                        // A training of a session opens the session with all its trainings.
                        launch {
                            navigator.push(
                                trainingEditScreen(
                                    training,
                                    trainings.filter { it.client.id == training.client.id }
                                ) { saved -> trainings = trainings.map { if (it.id == saved.id) saved else it } }
                            )
                        }
                    }
                )

                if (pendingAppointments.isEmpty() && cancelationRequests.isEmpty() && joinRequests.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(stringResource(R.string.pending_requests))
                        CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                            EmptyStateView(Icons.Outlined.PendingActions, stringResource(R.string.no_pending_requests))
                        }
                    }
                }
            }

            if (pendingAppointments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Clients: their own bookings waiting for the coach.
                    SectionLabel(stringResource(if (canManageRequests) R.string.pending_approvals else R.string.awaiting_coach_approval))
                    CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Column {
                            ShowMoreItems(pendingAppointments) { appointment ->
                                PendingAppointmentRow(appointment)
                                if (appointment.id != pendingAppointments.last().id) RowDivider()
                            }
                        }
                    }
                }
            }

            RequestsSection(stringResource(R.string.cancelation_requests), cancelationRequests)
            RequestsSection(stringResource(R.string.join_requests), joinRequests)

            if (pendingAppointments.isNotEmpty() || cancelationRequests.isNotEmpty() || joinRequests.isNotEmpty()) {
                SectionLabel(stringResource(R.string.approved_appointments), Modifier.padding(top = 8.dp))
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                CalendarMonth(
                    selectedDate = selectedDate,
                    onSelect = { selectedDate = it },
                    approvedDates = appointments.map { DateFormats.localDate(it.startTime) }.toSet(),
                    pendingDates = pendingAppointments.map { DateFormats.localDate(it.startTime) }.toSet()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(DateFormats.date(selectedDate))
                CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                    val dayAppointments = appointmentsForSelectedDate
                    if (dayAppointments.isEmpty() && !isInitialLoading) {
                        EmptyStateView(Icons.Outlined.EventBusy, stringResource(R.string.no_appointments_for_selected_day))
                    } else {
                        Column {
                            ShowMoreItems(dayAppointments) { appointment ->
                                AppointmentRow(appointment) {
                                    navigator.push(AppointmentDetailsScreen(appointment))
                                }
                                if (appointment.id != dayAppointments.last().id) RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    // Small uppercase label above a card (apexLabel).
    @Composable
    private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
        ApexLabel(text, modifier = modifier.padding(horizontal = 20.dp))
    }

    @Composable
    private fun RequestsSection(title: String, requests: List<AppointmentRequest>) {
        if (requests.isEmpty()) return
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(title)
            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                Column {
                    ShowMoreItems(requests) { request ->
                        AppointmentRequestRow(request)
                        if (request.id != requests.last().id) RowDivider()
                    }
                }
            }
        }
    }

    @Composable
    private fun AppointmentRequestRow(request: AppointmentRequest) {
        val isCancelation = request.type.name.lowercase() == "cancelationrequest"
        val tint = if (isCancelation) ApexColors.red else ApexColors.blue
        val isProcessing = processingRequestId == request.id

        Column(modifier = Modifier.testTag("appointment-request-row")) {
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarWithBadge(if (isCancelation) Icons.Outlined.EventBusy else Icons.Outlined.PersonAdd, tint) {
                    UserAvatar(request.sender.fullName, request.sender.pictureUrl, size = 40.dp)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(request.sender.fullName, style = ApexText.body)
                    Text(
                        DateFormats.date(request.appointment.startTime) + " · " + (request.appointment.timeSlot.description ?: ""),
                        style = ApexText.subheadline,
                        color = ApexColors.secondaryLabel
                    )
                    if (request.comment.isNotEmpty()) {
                        Text(request.comment, style = ApexText.footnote, color = ApexColors.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(modifier = Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(stringResource(R.string.approve), Icons.Filled.Check, ApexColors.green, isLoading = isProcessing, enabled = !isProcessing) {
                    launch { processRequest(request, "approve") }
                }
                ActionButton(stringResource(R.string.reject), Icons.Filled.Close, ApexColors.red, enabled = !isProcessing) {
                    launch { processRequest(request, "decline") }
                }
            }
        }
    }

    @Composable
    private fun AppointmentRow(appointment: Appointment, onClick: () -> Unit) {
        val tint = if (appointment.isActive) ApexColors.main else ApexColors.green

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (appointment.isActive) 1f else 0.8f)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Client picture(s), a check when it is over.
            val people = appointment.clients.map { it.fullName to it.pictureUrl }
            when {
                people.isEmpty() ->
                    IconCircle(if (appointment.isActive) Icons.Outlined.CalendarMonth else Icons.Outlined.TaskAlt, tint)
                appointment.isActive -> UserAvatarStack(people)
                else -> AvatarWithBadge(Icons.Filled.Check, ApexColors.green) { UserAvatarStack(people) }
            }

            // First client's name split into two lines.
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val client = appointment.clients.firstOrNull()
                if (client != null) {
                    Text(client.firstName, style = ApexText.subheadline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(client.lastName, style = ApexText.subheadline, color = ApexColors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(stringResource(R.string.no_client), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    appointment.timeSlot.description ?: stringResource(R.string.unknown_value),
                    style = ApexText.body.copy(fontWeight = FontWeight.Bold)
                )
                Text(DateFormats.date(appointment.startTime), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
            }

            Chevron()
        }
    }

    @Composable
    private fun PendingAppointmentRow(appointment: Appointment) {
        val isProcessing = processingAppointmentId == appointment.id

        Column {
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Client picture(s), marked as waiting.
                if (appointment.clients.isEmpty()) {
                    IconCircle(Icons.Outlined.EventRepeat, ApexColors.orange)
                } else {
                    AvatarWithBadge(Icons.Outlined.EventRepeat, ApexColors.orange) {
                        UserAvatarStack(appointment.clients.map { it.fullName to it.pictureUrl })
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(DateFormats.date(appointment.startTime), style = ApexText.body)
                    Text(
                        appointment.timeSlot.description ?: stringResource(R.string.unknown_value),
                        style = ApexText.subheadline,
                        color = ApexColors.secondaryLabel
                    )
                    if (appointment.clients.isNotEmpty()) {
                        Text(
                            appointment.clients.joinToString(", ") { it.fullName },
                            style = ApexText.caption,
                            color = ApexColors.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!canManageRequests) Badge(stringResource(R.string.status_pending), ApexColors.orange)
            }
            // Only staff decide; the API refuses a client anyway.
            if (canManageRequests) {
                Row(modifier = Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(stringResource(R.string.approve), Icons.Filled.Check, ApexColors.green, isLoading = isProcessing, enabled = !isProcessing) {
                        launch { updateAppointment(appointment, "approve", R.string.appointment_approved_successfully) }
                    }
                    ActionButton(stringResource(R.string.reject), Icons.Filled.Close, ApexColors.red, enabled = !isProcessing) {
                        launch { updateAppointment(appointment, "decline", R.string.appointment_declined_successfully) }
                    }
                }
            }
        }
    }

    @Composable
    private fun GeneratingOverlay() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(enabled = true, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 40.dp)
                    .shadow(20.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(ApexColors.background)
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(80.dp))
                CircularProgressIndicator(color = ApexColors.main)
                Text(
                    stringResource(R.string.generating_recurring_appointments),
                    style = ApexText.headline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    private suspend fun loadData(showInitialSpinner: Boolean) {
        if (showInitialSpinner) isInitialLoading = true
        try {
            val response: AppointmentsStatus = ApiClient.get("appointments")

            // Requests are loaded separately so a failure there
            // doesn't hide the appointments.
            val requests = if (canManageRequests) {
                try {
                    ApiClient.get<List<AppointmentRequest>>("appointment-requests/pending")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    appointmentRequests
                }
            } else emptyList()

            appointments = response.approvedAppointments
            pendingAppointments = response.pendingAppointments
            inProgressAppointments = response.inProgressAppointments
            appointmentRequests = requests.sortedBy { it.appointment.startTime }

            if (!isClient) {
                // Optional: "Danas" shows its appointments without them.
                try {
                    trainings = Training.loadAll()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keeps what is shown.
                }
                ApexWidget.saveStaff(
                    ApexApp.appContext,
                    nextToday = WidgetSnapshot.nextToday(appointments + inProgressAppointments, Instant.now(), DateFormats.zone),
                    pendingRequests = pendingAppointments.size + appointmentRequests.size
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Same as iOS, a failed load keeps what is shown.
        } finally {
            if (showInitialSpinner) isInitialLoading = false
        }
    }

    // Today's appointments of every state, by time.
    private val todayAppointments: List<Appointment>
        get() {
            val today = LocalDate.now()
            return (inProgressAppointments + appointments + pendingAppointments)
                .distinctBy { it.id }
                .filter { DateFormats.localDate(it.startTime) == today }
                .sortedBy { it.startTime }
        }

    private fun findAppointment(id: String): Appointment? =
        (appointments + inProgressAppointments + pendingAppointments).firstOrNull { it.id.equals(id, ignoreCase = true) }

    // action is "approve" or "decline", same endpoints as the requests tab.
    private suspend fun processRequest(request: AppointmentRequest, action: String) {
        processingRequestId = request.id
        try {
            ApiClient.requestData("appointment-requests/$action/${request.id}")
            ToastManager.show(
                if (action == "approve") R.string.request_approved_successfully else R.string.request_rejected_successfully,
                ToastType.SUCCESS
            )
            // Approving changes appointments too, so reload everything.
            loadData(showInitialSpinner = false)
        } catch (e: Exception) {
            showError(e)
        } finally {
            processingRequestId = null
        }
    }

    private suspend fun updateAppointment(appointment: Appointment, action: String, successMessage: Int) {
        processingAppointmentId = appointment.id
        try {
            ApiClient.requestData("appointments/$action/${appointment.id}")
            ToastManager.show(successMessage, ToastType.SUCCESS)
            loadData(showInitialSpinner = false)
        } catch (e: Exception) {
            showError(e)
        } finally {
            processingAppointmentId = null
        }
    }

    private suspend fun generateRecurringAppointments() {
        isGeneratingRecurring = true
        try {
            ApiClient.requestData("recurring-appointments/generate-next-week")
            ToastManager.show(R.string.recurring_appointments_generated_successfully, ToastType.SUCCESS)
            loadData(showInitialSpinner = false)
        } catch (e: Exception) {
            showError(e)
        } finally {
            isGeneratingRecurring = false
        }
    }
}
