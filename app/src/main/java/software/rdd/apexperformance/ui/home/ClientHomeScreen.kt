package software.rdd.apexperformance.ui.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SportsGymnastics
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.util.AppTab
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.Permissions
import software.rdd.apexperformance.model.Appointment
import software.rdd.apexperformance.model.AppointmentsStatus
import software.rdd.apexperformance.model.ClientGoal
import software.rdd.apexperformance.model.ClientPlan
import software.rdd.apexperformance.model.Profile
import software.rdd.apexperformance.model.UserProfile
import software.rdd.apexperformance.model.WorkoutLibraryAccess
import software.rdd.apexperformance.ui.appointments.CreateAppointmentScreen
import software.rdd.apexperformance.ui.bodymeasurements.ClientProgressScreen
import software.rdd.apexperformance.ui.clients.ClientGoalContent
import software.rdd.apexperformance.ui.clients.ClientGoalDetailScreen
import software.rdd.apexperformance.ui.components.ApexCompactButton
import software.rdd.apexperformance.ui.components.ApexPictureBackground
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import software.rdd.apexperformance.ui.components.ApexProgressBar
import software.rdd.apexperformance.ui.components.ApexScreenHeader
import software.rdd.apexperformance.ui.components.ApexSectionTitle
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.ProfilePicture
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.workouts.WorkoutsScreen
import java.time.Instant

// Client's home: greeting, next training with booking, package,
// goal and plan, and shortcuts to trainings, progress and workouts.
class ClientHomeScreen(private val openTab: (AppTab) -> Unit) : Screen() {

    private var client by mutableStateOf<UserProfile?>(null)
    private var profile by mutableStateOf<Profile?>(null)
    private var approvedAppointments by mutableStateOf<List<Appointment>>(emptyList())
    private var pendingAppointments by mutableStateOf<List<Appointment>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var hasLoaded = false
    private var goal by mutableStateOf<ClientGoal?>(null)
    private var showGoalSheet by mutableStateOf(false)

    private val canGetAppointments: Boolean
        get() = AuthManager.hasPermission(Permissions.CAN_GET_APPOINTMENTS)

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) {
            if (!hasLoaded) {
                hasLoaded = true
                load()
            }
        }

        val planTitle = ClientPlan.from(client?.plan)?.let { stringResource(it.title) }

        ScrollScreen(
            topBar = { ApexTitleTopBar(planTitle) },
            onRefresh = { load() }
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Greeting()

                NextAppointmentCard()

                PackageCard()

                val goal = goal
                if (goal != null && !goal.isEmpty) {
                    GoalCard(goal)
                }

                Shortcuts()
            }
        }

        val goal = goal
        if (showGoalSheet && goal != null) {
            GoalSheet(goal) {
                showGoalSheet = false
                markGoalSeen()
            }
        }
    }

    // MARK: - Sections

    @Composable
    private fun Greeting() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.clip(CircleShape)) {
                ProfilePicture(profile, size = 44.dp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val firstName = client?.firstName
                if (!firstName.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.hello_name, firstName).uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = ApexColors.label
                    )
                }
                Text(
                    stringResource(R.string.ready_for_next_training).uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    color = ApexColors.secondaryLabel
                )
            }
        }
    }

    @Composable
    private fun NextAppointmentCard() {
        val navigator = LocalNavigator.current
        val appointment = nextAppointment

        ApexPictureBackground(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            icon = Icons.Filled.FitnessCenter,
            shape = RoundedCornerShape(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.8f)
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    when {
                        appointment != null -> {
                            Text(
                                stringResource(
                                    if (appointment.isPending) R.string.next_appointment_pending
                                    else R.string.next_appointment
                                ).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                appointmentDateAndTime(appointment.startTime),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        isLoading -> CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        else -> Text(
                            stringResource(R.string.no_upcoming_appointments).uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                    }
                }

                if (canGetAppointments) {
                    ApexCompactButton(stringResource(R.string.book_appointment)) {
                        navigator.push(CreateAppointmentScreen())
                    }
                }
            }
        }
    }

    @Composable
    private fun PackageCard() {
        val navigator = LocalNavigator.current
        val summary = PackageSummary(client, approvedAppointments)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .apexCard()
                .clickable {
                    navigator.push(MyPackageScreen(client, approvedAppointments, pendingAppointments))
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ApexSectionTitle(stringResource(R.string.my_package))
                Spacer(Modifier.weight(1f))
                Chevron()
            }

            Row {
                SmallCaps(stringResource(R.string.package_done_of_total, summary.done, summary.total))
                Spacer(Modifier.weight(1f))
                SmallCaps(stringResource(R.string.package_remaining, summary.remaining))
            }

            ApexProgressBar(summary.progress)
        }
    }

    @Composable
    private fun SmallCaps(text: String) {
        Text(
            text.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = ApexColors.secondaryLabel
        )
    }

    @Composable
    private fun GoalCard(goal: ClientGoal) {
        val navigator = LocalNavigator.current

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .apexCard()
                .clickable { navigator.push(ClientGoalDetailScreen(goal)) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(modifier = Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.TrackChanges,
                    contentDescription = null,
                    tint = ApexColors.accent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ApexSectionTitle(
                    stringResource(R.string.my_goal_and_plan),
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                goal.goal?.takeIf { it.isNotEmpty() }?.let {
                    GoalLine(stringResource(R.string.goal_line, it))
                }
                goal.currentBlock?.takeIf { it.isNotEmpty() }?.let {
                    GoalLine(stringResource(R.string.current_block_line, it))
                }
                goal.focus?.takeIf { it.isNotEmpty() }?.let {
                    GoalLine(stringResource(R.string.focus_line, it))
                }
                val date = goal.nextAssessmentDate
                val text = goal.nextAssessment
                if (date != null) {
                    GoalLine(stringResource(R.string.next_assessment_line, DateFormats.date(date)))
                } else if (!text.isNullOrEmpty()) {
                    GoalLine(stringResource(R.string.next_assessment_line, text))
                }
            }

            Chevron()
        }
    }

    @Composable
    private fun GoalLine(text: String) {
        Text(
            text.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            color = ApexColors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    @Composable
    private fun Shortcuts() {
        val navigator = LocalNavigator.current
        val access = WorkoutLibraryAccess.from(client?.plan)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .apexCard()
                .padding(horizontal = 16.dp)
        ) {
            ShortcutRow(stringResource(R.string.my_trainings), Icons.Outlined.FitnessCenter) {
                openTab(AppTab.TRAININGS)
            }

            HorizontalDivider(color = ApexColors.border, thickness = 1.dp)

            ShortcutRow(stringResource(R.string.progress), Icons.Outlined.BarChart) {
                navigator.push(ClientProgressScreen())
            }

            // Workout library depends on the plan agreed with the coach.
            if (access != WorkoutLibraryAccess.NONE) {
                val isAll = access == WorkoutLibraryAccess.ALL
                HorizontalDivider(color = ApexColors.border, thickness = 1.dp)

                ShortcutRow(
                    stringResource(if (isAll) R.string.exercise_library else R.string.mobility_and_stretching),
                    if (isAll) Icons.Outlined.SportsGymnastics else Icons.Outlined.SelfImprovement
                ) {
                    navigator.push(
                        WorkoutsScreen(
                            workoutFilter = access::allows,
                            title = if (isAll) R.string.exercise_library else R.string.mobility_and_stretching,
                            subtitle = if (isAll) R.string.exercise_library_subtitle else R.string.mobility_and_stretching_subtitle
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun ShortcutRow(title: String, icon: ImageVector, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = ApexColors.label, modifier = Modifier.size(20.dp))
            }
            Text(
                title.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = ApexColors.label,
                modifier = Modifier.weight(1f)
            )
            Chevron()
        }
    }

    // MARK: - Goal shown after login

    // Shown to the client when the coach changed the goal and plan.
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GoalSheet(goal: ClientGoal, onDismiss: () -> Unit) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ApexColors.groupedBackground
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ApexScreenHeader(
                    title = stringResource(R.string.my_goal_and_plan),
                    subtitle = stringResource(R.string.my_goal_and_plan_subtitle)
                )

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .apexCard()
                            .padding(16.dp)
                    ) {
                        ClientGoalContent(goal)
                    }

                    ApexPrimaryButton(
                        text = stringResource(R.string.got_it),
                        icon = Icons.Filled.Check,
                        onClick = onDismiss
                    )
                }
            }
        }
    }

    private val seenGoalKey: String
        get() = "seenClientGoalUpdatedAt-${AuthManager.username}"

    private val preferences
        get() = ApexApp.appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    // Shows the goal and plan when the coach wrote or changed it
    // since the client last saw it.
    private fun showGoalIfChanged() {
        val goal = goal ?: return
        val updatedAt = goal.updatedAt ?: return
        if (goal.isEmpty) return
        val seen = preferences.getLong(seenGoalKey, 0L)
        if (updatedAt.toEpochMilli() > seen + 1000) {
            showGoalSheet = true
        }
    }

    private fun markGoalSeen() {
        val updatedAt = goal?.updatedAt ?: return
        preferences.edit().putLong(seenGoalKey, updatedAt.toEpochMilli()).apply()
    }

    // MARK: - Data

    private class NextAppointment(val startTime: Instant, val isPending: Boolean)

    // Next approved appointment, or the next one waiting for approval.
    private val nextAppointment: NextAppointment?
        get() {
            val now = Instant.now()
            approvedAppointments
                .filter { it.startTime.isAfter(now) }
                .minByOrNull { it.startTime }
                ?.let { return NextAppointment(it.startTime, isPending = false) }
            pendingAppointments
                .filter { it.startTime.isAfter(now) }
                .minByOrNull { it.startTime }
                ?.let { return NextAppointment(it.startTime, isPending = true) }
            return null
        }

    private suspend fun load() {
        isLoading = true
        try {
            // Loaded separately so one failing doesn't hide the other.
            coroutineScope {
                val clientResult = async { loadClient() }
                val appointmentsResult = async { loadAppointments() }

                clientResult.await()?.let { client = it }
                appointmentsResult.await()?.let {
                    approvedAppointments = it.approvedAppointments
                    pendingAppointments = it.pendingAppointments
                }
            }

            // Picture, goal and plan are extras, home works without them.
            try {
                profile = ApiClient.get<Profile>("profile")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
            try {
                goal = ClientGoal.loadMine()
                showGoalIfChanged()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        } finally {
            isLoading = false
        }
    }

    private suspend fun loadClient(): UserProfile? =
        try {
            ApiClient.get<UserProfile>("clients/current-client")
        } catch (e: Exception) {
            showError(e)
            null
        }

    private suspend fun loadAppointments(): AppointmentsStatus? {
        if (!canGetAppointments) return null
        return try {
            ApiClient.get<AppointmentsStatus>("appointments")
        } catch (e: Exception) {
            showError(e)
            null
        }
    }

    private companion object {
        const val PREFERENCES = "settings"
    }
}
