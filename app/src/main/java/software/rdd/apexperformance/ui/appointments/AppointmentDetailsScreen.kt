package software.rdd.apexperformance.ui.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.Appointment
import software.rdd.apexperformance.model.CancelationRequest
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.IconCircle
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScreenHeader
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.components.StatTile
import software.rdd.apexperformance.ui.components.TextArea
import software.rdd.apexperformance.ui.components.TintedButton
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

class AppointmentDetailsScreen(private val appointment: Appointment) : Screen() {

    private var showCancelRequestSheet by mutableStateOf(false)
    private var showCancelConfirmation by mutableStateOf(false)
    private var cancelationComment by mutableStateOf("")

    private val isClient: Boolean get() = AuthManager.hasRole(Roles.CLIENT)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen {
            ScreenHeader(stringResource(R.string.appointment), stringResource(R.string.manage_appointment_details), large = false)

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Outlined.CalendarMonth, ApexColors.main, size = 56.dp, background = ApexColors.systemGray5, iconSize = 26.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(DateFormats.date(appointment.startTime), style = ApexText.headline)
                        Text(stringResource(R.string.date), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
                    }
                }
            }

            Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(DateFormats.dayName(appointment.startTime), stringResource(R.string.day), Modifier.weight(1f))
                StatTile(
                    appointment.timeSlot.description ?: stringResource(R.string.unknown_value),
                    stringResource(R.string.time_slot),
                    Modifier.weight(1f)
                )
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.clients)) {
                Column {
                    appointment.clients.forEach { client ->
                        SettingsRow(icon = Icons.Outlined.Person, iconTint = ApexColors.main, title = "${client.firstName} ${client.lastName}")
                        if (client.id != appointment.clients.last().id) RowDivider()
                    }
                }
            }

            if (!appointment.isCompleted) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.actions)) {
                    Column {
                        if (isClient) {
                            SettingsRow(
                                icon = Icons.AutoMirrored.Outlined.Send,
                                iconTint = ApexColors.main,
                                title = stringResource(R.string.send_cancelation_request),
                                subtitle = stringResource(R.string.notify_coach_about_cancelation),
                                showChevron = true,
                                onClick = { showCancelRequestSheet = true }
                            )
                        } else {
                            SettingsRow(
                                icon = Icons.Outlined.Cancel,
                                iconTint = ApexColors.red,
                                title = stringResource(R.string.cancel_appointment),
                                subtitle = stringResource(R.string.cancel_appointment_info),
                                showChevron = true,
                                titleColor = ApexColors.red,
                                onClick = { showCancelConfirmation = true }
                            )
                        }
                    }
                }
            }
        }

        if (showCancelConfirmation) {
            ConfirmDialog(
                title = stringResource(R.string.cancel_appointment_question),
                message = stringResource(R.string.can_not_be_undone),
                confirmText = stringResource(R.string.cancel_appointment),
                dismissText = stringResource(R.string.keep),
                onConfirm = { launch { cancelAppointment(navigator) } },
                onDismiss = { showCancelConfirmation = false }
            )
        }

        if (showCancelRequestSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCancelRequestSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = ApexColors.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(stringResource(R.string.confirm_your_action), style = ApexText.headline)
                    Text(stringResource(R.string.write_comment_for_cancelation), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
                    TextArea(
                        placeholder = stringResource(R.string.cancelation_comment_placeholder),
                        value = cancelationComment,
                        onValueChange = { cancelationComment = it }
                    )
                    TintedButton(stringResource(R.string.send_request), Icons.AutoMirrored.Outlined.Send) {
                        showCancelRequestSheet = false
                        launch { sendCancelation() }
                    }
                }
            }
        }
    }

    private suspend fun sendCancelation() {
        try {
            ApiClient.send(
                "appointment-requests/cancelation/${appointment.id}",
                HttpMethod.POST,
                CancelationRequest(cancelationComment)
            )
            ToastManager.show(R.string.successfully_sent_cancelation_request, ToastType.SUCCESS)
        } catch (e: Exception) {
            showError(e)
        }
    }

    private suspend fun cancelAppointment(navigator: Navigator) {
        try {
            ApiClient.requestData("appointments/cancel/${appointment.id}")
            ToastManager.show(R.string.successfully_canceled_appointment, ToastType.SUCCESS)
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        }
    }
}
