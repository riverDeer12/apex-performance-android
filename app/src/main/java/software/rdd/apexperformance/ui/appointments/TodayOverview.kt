package software.rdd.apexperformance.ui.appointments

import software.rdd.apexperformance.ui.components.AvatarWithBadge
import software.rdd.apexperformance.ui.components.UserAvatar
import software.rdd.apexperformance.ui.components.UserAvatarStack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.model.Appointment
import software.rdd.apexperformance.model.BusinessStatus
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.ui.components.ApexLabel
import software.rdd.apexperformance.ui.components.ApexSectionTitle
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.EmptyStateView
import software.rdd.apexperformance.ui.components.IconCircle
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ShowMoreItems
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// "Danas" for coaches and administrators at the top of the appointments
// tab (a sixth tab doesn't fit the tab bar): today's appointments with their
// state and the trainings in progress with how long they have been running.
// The pending requests with Approve / Reject follow right below it.
@Composable
fun TodayOverview(
    appointments: List<Appointment>,
    trainings: List<Training>,
    isLoading: Boolean,
    onOpenAppointment: (Appointment) -> Unit,
    onOpenTraining: (Training) -> Unit
) {
    Column(modifier = Modifier.testTag("today-overview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ApexSectionTitle(stringResource(R.string.today))
            Text(DateFormats.date(LocalDate.now()), style = ApexText.footnote, color = ApexColors.secondaryLabel)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ApexLabel(stringResource(R.string.today_appointments), modifier = Modifier.padding(horizontal = 20.dp))
            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (appointments.isEmpty()) {
                    if (!isLoading) EmptyStateView(Icons.Outlined.EventAvailable, stringResource(R.string.no_appointments_today))
                } else {
                    Column {
                        ShowMoreItems(appointments) { appointment ->
                            TodayAppointmentRow(appointment) { onOpenAppointment(appointment) }
                            if (appointment.id != appointments.last().id) RowDivider()
                        }
                    }
                }
            }
        }

        val running = trainings.filter { it.isInProgress }.sortedBy { it.startedAt }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ApexLabel(stringResource(R.string.trainings_in_progress), modifier = Modifier.padding(horizontal = 20.dp))
            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (running.isEmpty()) {
                    if (!isLoading) EmptyStateView(Icons.Outlined.Timer, stringResource(R.string.no_trainings_in_progress))
                } else {
                    // Elapsed times tick while the screen is open.
                    var now by remember { mutableStateOf(Instant.now()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(30_000)
                            now = Instant.now()
                        }
                    }
                    Column {
                        ShowMoreItems(running) { training ->
                            RunningTrainingRow(training, now) { onOpenTraining(training) }
                            if (training.id != running.last().id) RowDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayAppointmentRow(appointment: Appointment, onClick: () -> Unit) {
    val (statusText, statusColor) = statusOf(appointment)
    Row(
        modifier = Modifier
            .testTag("today-appointment-row")
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (appointment.clients.isEmpty()) {
            IconCircle(Icons.Outlined.CalendarMonth, statusColor)
        } else {
            UserAvatarStack(appointment.clients.map { it.fullName to it.pictureUrl })
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${DateFormats.time(appointment.startTime)} – ${DateFormats.time(appointment.endTime)}",
                style = ApexText.body.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                appointment.clients.joinToString(", ") { it.fullName.trim() }.ifBlank { stringResource(R.string.no_client) },
                style = ApexText.subheadline,
                color = ApexColors.secondaryLabel
            )
        }
        StatusChip(statusText, statusColor)
        Chevron()
    }
}

@Composable
private fun RunningTrainingRow(training: Training, now: Instant, onClick: () -> Unit) {
    val startedAt = training.startedAt ?: return
    val minutes = maxOf(0L, Duration.between(startedAt, now).toMinutes())
    val elapsed = if (minutes >= 60) {
        stringResource(R.string.elapsed_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
    } else {
        stringResource(R.string.elapsed_minutes, minutes.toInt())
    }
    Row(
        modifier = Modifier
            .testTag("running-training-row")
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AvatarWithBadge(Icons.Outlined.Timer, ApexColors.orange) {
            UserAvatar(training.client.fullName, training.client.pictureUrl, size = 40.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(training.name, style = ApexText.body.copy(fontWeight = FontWeight.SemiBold))
            Text(training.client.fullName, style = ApexText.subheadline, color = ApexColors.secondaryLabel)
        }
        Text(elapsed, style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = ApexColors.orange)
        Chevron()
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(
        text,
        style = ApexText.caption.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun statusOf(appointment: Appointment): Pair<String, Color> = when (appointment.status.name) {
    BusinessStatus.APPROVED -> stringResource(R.string.status_approved) to ApexColors.green
    BusinessStatus.PENDING -> stringResource(R.string.status_pending) to ApexColors.orange
    "InProgress" -> stringResource(R.string.in_progress) to ApexColors.blue
    else -> appointment.status.name to ApexColors.gray
}
