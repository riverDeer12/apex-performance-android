package software.rdd.apexperformance.ui.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.core.util.localized
import software.rdd.apexperformance.model.AppointmentRequest
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScreenHeader
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

class AppointmentRequestDetailsScreen(private val request: AppointmentRequest) : Screen() {

    private var showApproveDialog by mutableStateOf(false)
    private var showRejectDialog by mutableStateOf(false)
    private var isProcessing by mutableStateOf(false)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen {
            ScreenHeader(stringResource(R.string.request_details), stringResource(R.string.review_and_take_action), large = false)

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.request_information)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoLine(stringResource(R.string.sender), request.sender.fullName)
                    HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                    InfoLine(stringResource(R.string.created_at), DateFormats.dateAndTime(request.createdAt))
                    HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                    InfoLine(stringResource(R.string.request_type), localized(request.type.description.lowercase()))

                    if (request.comment.isNotEmpty()) {
                        HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.comment), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
                            Text(request.comment, style = ApexText.subheadline)
                        }
                    }
                }
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.related_appointment)) {
                SettingsRow(
                    icon = Icons.Outlined.CalendarMonth,
                    iconTint = ApexColors.main,
                    title = DateFormats.date(request.appointment.startTime),
                    subtitle = request.appointment.timeSlot.description ?: stringResource(R.string.unknown_value),
                    showChevron = true,
                    onClick = { navigator.push(AppointmentDetailsScreen(request.appointment)) }
                )
            }

            if (!AuthManager.hasRole(Roles.CLIENT)) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.actions)) {
                    Column {
                        SettingsRow(
                            icon = Icons.Outlined.CheckCircle,
                            iconTint = ApexColors.green,
                            title = stringResource(R.string.approve_request),
                            subtitle = stringResource(R.string.approve_request_info),
                            showChevron = true,
                            titleColor = ApexColors.green,
                            enabled = !isProcessing,
                            onClick = { showApproveDialog = true }
                        )
                        RowDivider()
                        SettingsRow(
                            icon = Icons.Outlined.Cancel,
                            iconTint = ApexColors.red,
                            title = stringResource(R.string.reject_request),
                            subtitle = stringResource(R.string.reject_request_info),
                            showChevron = true,
                            titleColor = ApexColors.red,
                            enabled = !isProcessing,
                            onClick = { showRejectDialog = true }
                        )
                    }
                }
            }
        }

        if (showApproveDialog) {
            ConfirmDialog(
                title = stringResource(R.string.approve_request_question),
                message = stringResource(R.string.approve_request_confirmation),
                confirmText = stringResource(R.string.approve),
                destructive = false,
                onConfirm = { launch { process("approve", R.string.request_approved_successfully, navigator) } },
                onDismiss = { showApproveDialog = false }
            )
        }

        if (showRejectDialog) {
            ConfirmDialog(
                title = stringResource(R.string.reject_request_question),
                message = stringResource(R.string.reject_request_confirmation),
                confirmText = stringResource(R.string.reject),
                onConfirm = { launch { process("decline", R.string.request_rejected_successfully, navigator) } },
                onDismiss = { showRejectDialog = false }
            )
        }
    }

    @Composable
    private fun InfoLine(title: String, value: String) {
        Row {
            Text(title, style = ApexText.subheadline, color = ApexColors.secondaryLabel)
            Spacer(Modifier.weight(1f))
            Text(value, style = ApexText.subheadline.copy(fontWeight = FontWeight.Medium))
        }
    }

    private suspend fun process(action: String, successMessage: Int, navigator: Navigator) {
        isProcessing = true
        try {
            ApiClient.requestData("appointment-requests/$action/${request.id}")
            ToastManager.show(successMessage, ToastType.SUCCESS)
            delay(500)
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isProcessing = false
        }
    }
}
