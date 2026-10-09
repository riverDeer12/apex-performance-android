package software.rdd.apexperformance.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.Permissions
import software.rdd.apexperformance.model.Appointment
import software.rdd.apexperformance.model.BusinessStatus
import software.rdd.apexperformance.model.ClientPlan
import software.rdd.apexperformance.model.UserProfile
import software.rdd.apexperformance.ui.appointments.AppointmentDetailsScreen
import software.rdd.apexperformance.ui.appointments.CreateAppointmentScreen
import software.rdd.apexperformance.ui.components.ApexPictureBackground
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import software.rdd.apexperformance.ui.components.ApexSectionTitle
import software.rdd.apexperformance.ui.components.ApexTitle
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.Instant
import java.time.YearMonth

// Client's package worked out from credits and appointments: credits are
// taken when an appointment is approved, so the credits left are still
// to book, upcoming approved appointments are booked and past ones are done.
class PackageSummary(client: UserProfile?, approvedAppointments: List<Appointment>, now: Instant = Instant.now()) {
    // Approved appointments this month that are over.
    val done: Int
    // Upcoming approved appointments.
    val reserved: Int
    // Credits left to book.
    val available: Int

    init {
        val thisMonth = YearMonth.from(now.atZone(DateFormats.zone))
        done = approvedAppointments.count {
            !it.endTime.isAfter(now) && YearMonth.from(it.startTime.atZone(DateFormats.zone)) == thisMonth
        }
        reserved = approvedAppointments.count { it.startTime.isAfter(now) }
        available = maxOf(client?.credits ?: 0, 0)
    }

    val remaining: Int get() = reserved + available
    val total: Int get() = done + remaining
    val progress: Double get() = if (total > 0) done.toDouble() / total else 0.0
    val isActive: Boolean get() = remaining > 0
}

// "Moj paket": trainings done, booked and left to book, with booking
// and the upcoming appointments.
class MyPackageScreen(
    private val client: UserProfile?,
    private val approvedAppointments: List<Appointment>,
    private val pendingAppointments: List<Appointment>
) : Screen() {

    private val summary get() = PackageSummary(client, approvedAppointments)

    // Upcoming appointments, approved and waiting for approval, soonest first.
    private val upcoming: List<Appointment>
        get() {
            val now = Instant.now()
            return (approvedAppointments + pendingAppointments)
                .filter { it.startTime.isAfter(now) }
                .sortedBy { it.startTime }
        }

    @Composable
    override fun Content() {
        val planTitle = ClientPlan.from(client?.plan)?.let { stringResource(it.title) }

        ScrollScreen(topBar = { ApexTitleTopBar(planTitle) }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ApexTitle(stringResource(R.string.my_package))

                ApexPictureBackground(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    shape = RoundedCornerShape(14.dp)
                )

                SummaryCard()

                val upcoming = upcoming
                if (upcoming.isNotEmpty()) {
                    UpcomingCard(upcoming)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = ApexColors.secondaryLabel,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        stringResource(R.string.cancelation_terms_by_agreement).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.6.sp,
                        color = ApexColors.secondaryLabel
                    )
                }
            }
        }
    }

    @Composable
    private fun SummaryCard() {
        val navigator = LocalNavigator.current
        val summary = summary

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .apexCard()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ApexSectionTitle(stringResource(R.string.package_trainings, summary.total))
                Spacer(Modifier.weight(1f))
                if (summary.isActive) {
                    Text(
                        stringResource(R.string.active_package).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = ApexColors.green,
                        modifier = Modifier
                            .border(1.dp, ApexColors.green.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .border(1.dp, ApexColors.border, RoundedCornerShape(8.dp))
            ) {
                Stat(summary.done, stringResource(R.string.done_this_month))
                StatDivider()
                Stat(summary.reserved, stringResource(R.string.reserved))
                StatDivider()
                Stat(summary.available, stringResource(R.string.to_book))
            }

            Text(
                stringResource(R.string.remaining_includes_reserved, summary.remaining, summary.reserved).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = ApexColors.secondaryLabel
            )

            if (AuthManager.hasPermission(Permissions.CAN_GET_APPOINTMENTS)) {
                ApexPrimaryButton(
                    text = stringResource(R.string.book_appointment),
                    enabled = summary.available > 0
                ) {
                    navigator.push(CreateAppointmentScreen())
                }
            }
        }
    }

    @Composable
    private fun RowScope.Stat(value: Int, label: String) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            Text("$value", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ApexColors.label)
            Text(
                label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = ApexColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    private fun StatDivider() {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(ApexColors.border)
        )
    }

    @Composable
    private fun UpcomingCard(upcoming: List<Appointment>) {
        val navigator = LocalNavigator.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .apexCard()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)
        ) {
            ApexSectionTitle(
                stringResource(R.string.upcoming_appointments),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            upcoming.forEach { appointment ->
                HorizontalDivider(color = ApexColors.border, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigator.push(AppointmentDetailsScreen(appointment)) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = ApexColors.secondaryLabel,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        appointmentDateAndTime(appointment.startTime),
                        style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (appointment.status.name == BusinessStatus.PENDING) {
                        Text(
                            stringResource(R.string.pending).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ApexColors.orange
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Chevron()
                }
            }
        }
    }
}
