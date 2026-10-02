package software.rdd.apexperformance.ui.appointments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.core.util.localized
import software.rdd.apexperformance.model.AppointmentRequest
import software.rdd.apexperformance.model.MyAppointmentRequest
import software.rdd.apexperformance.model.SentAppointmentRequest
import software.rdd.apexperformance.ui.components.Badge
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.IconTile
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScreenHeader
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

class AppointmentRequestsScreen : Screen() {

    private var requests by mutableStateOf<List<AppointmentRequest>>(emptyList())
    // Clients see all requests they sent, with status.
    private var sentRequests by mutableStateOf<List<SentAppointmentRequest>>(emptyList())
    private var isInitialLoading by mutableStateOf(false)
    private var hasLoaded = false

    private val isClient: Boolean get() = AuthManager.hasRole(Roles.CLIENT)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        // First load shows a spinner, coming back to the screen reloads quietly.
        LaunchedEffect(Unit) { loadData(showInitialSpinner = !hasLoaded) }

        ScrollScreen(
            showLoading = isInitialLoading && requests.isEmpty() && sentRequests.isEmpty(),
            onRefresh = { loadData(showInitialSpinner = false) }
        ) {
            ScreenHeader(
                stringResource(R.string.appointment_requests),
                stringResource(if (isClient) R.string.my_requests_subtitle else R.string.manage_client_requests)
            )

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (isClient) {
                    if (sentRequests.isEmpty() && !isInitialLoading) {
                        EmptyText(stringResource(R.string.no_requests))
                    } else {
                        Column {
                            sentRequests.forEach { request ->
                                SentRequestRow(request)
                                if (request.id != sentRequests.last().id) RowDivider()
                            }
                        }
                    }
                } else {
                    if (requests.isEmpty() && !isInitialLoading) {
                        EmptyText(stringResource(R.string.no_requests))
                    } else {
                        Column {
                            requests.forEach { request ->
                                RequestRow(request) { navigator.push(AppointmentRequestDetailsScreen(request)) }
                                if (request.id != requests.last().id) RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SentRequestRow(request: SentAppointmentRequest) {
        val (statusTitle, statusColor) = statusStyle(request.status.name)

        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(requestIcon(request.type.name), requestColor(request.type.name))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        requestTypeTitle(request.type.name),
                        style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Badge(statusTitle, statusColor)
                }
                Text(
                    DateFormats.date(request.appointment.startTime) + " · " + (request.appointment.timeSlot.description ?: ""),
                    style = ApexText.subheadline,
                    color = ApexColors.secondaryLabel
                )
                if (request.comment.isNotEmpty()) {
                    Text(request.comment, style = ApexText.caption, color = ApexColors.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    stringResource(R.string.sent_at) + " " + DateFormats.dateAndTime(request.createdAt),
                    style = ApexText.caption2,
                    color = ApexColors.tertiaryLabel
                )
            }
        }
    }

    @Composable
    private fun RequestRow(request: AppointmentRequest, onClick: () -> Unit) {
        val typeDescription = localized(request.type.description.lowercase())
        val title = if (isClient) typeDescription else request.sender.fullName
        val subtitle = if (isClient) {
            DateFormats.date(request.appointment.startTime) + " - " + (request.appointment.timeSlot.description ?: "")
        } else typeDescription

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp)
        ) {
            SettingsRow(
                icon = requestIcon(request.type.name),
                iconTint = requestColor(request.type.name),
                title = title,
                subtitle = subtitle,
                showChevron = true
            )
            if (request.comment.isNotEmpty()) {
                Text(
                    request.comment,
                    style = ApexText.caption,
                    color = ApexColors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 46.dp, end = 12.dp, top = 4.dp)
                )
            }
        }
    }

    @Composable
    private fun requestTypeTitle(typeName: String): String = when (typeName.lowercase()) {
        "cancelationrequest" -> stringResource(R.string.cancelation_request)
        "joinrequest" -> stringResource(R.string.join_request)
        "newappointment" -> stringResource(R.string.new_appointment_request)
        else -> localized(typeName)
    }

    // Status names from the API (BusinessStatuses).
    @Composable
    private fun statusStyle(statusName: String): Pair<String, Color> = when (statusName.lowercase()) {
        "approved" -> stringResource(R.string.request_status_approved) to ApexColors.green
        "declined" -> stringResource(R.string.request_status_declined) to ApexColors.red
        "canceled", "cancelled" -> stringResource(R.string.request_status_canceled) to ApexColors.gray
        "inprogress" -> stringResource(R.string.request_status_in_progress) to ApexColors.blue
        else -> stringResource(R.string.request_status_pending) to ApexColors.orange
    }

    private fun requestIcon(typeName: String): ImageVector = when (typeName.lowercase()) {
        "cancelationrequest" -> Icons.Outlined.EventBusy
        "joinrequest" -> Icons.Outlined.PersonAdd
        "newappointment" -> Icons.Outlined.EventAvailable
        else -> Icons.AutoMirrored.Outlined.HelpOutline
    }

    private fun requestColor(typeName: String): Color =
        if (typeName.lowercase() == "cancelationrequest") ApexColors.red else ApexColors.main

    private suspend fun loadData(showInitialSpinner: Boolean) {
        if (showInitialSpinner) isInitialLoading = true
        try {
            if (isClient) {
                val response: List<SentAppointmentRequest> = ApiClient.get("appointment-requests")

                // New appointment requests come from a separate endpoint. If it
                // fails, the other requests are still shown.
                val newAppointments = try {
                    ApiClient.get<List<MyAppointmentRequest>>("appointments/my-requests")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }

                sentRequests = (response + newAppointments.map(SentAppointmentRequest::fromNewAppointment))
                    .sortedByDescending { it.createdAt }
            } else {
                requests = ApiClient.get("appointment-requests/pending")
            }
        } catch (e: Exception) {
            showError(e)
        } finally {
            isInitialLoading = false
            hasLoaded = true
        }
    }
}
