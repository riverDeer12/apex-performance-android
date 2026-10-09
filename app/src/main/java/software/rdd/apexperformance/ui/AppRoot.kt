package software.rdd.apexperformance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import software.rdd.apexperformance.MainActivity
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.NavigatorHost
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.util.AppTab
import software.rdd.apexperformance.core.util.NotificationRouter
import software.rdd.apexperformance.core.util.Permissions
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.ui.appointments.AppointmentRequestsScreen
import software.rdd.apexperformance.ui.appointments.AppointmentsScreen
import software.rdd.apexperformance.ui.bodymeasurements.ClientProgressScreen
import software.rdd.apexperformance.ui.clients.ClientsScreen
import software.rdd.apexperformance.ui.components.SplashOverlay
import software.rdd.apexperformance.ui.components.ToastView
import software.rdd.apexperformance.ui.home.ClientHomeScreen
import software.rdd.apexperformance.ui.login.LoginScreen
import software.rdd.apexperformance.ui.profile.UserProfileScreen
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexTheme
import software.rdd.apexperformance.ui.trainings.CompletedTrainingsScreen
import software.rdd.apexperformance.ui.workouts.WorkoutsScreen

@Composable
fun AppRoot() {
    // Animated splash shown over the app right after launch.
    var showSplash by rememberSaveable { mutableStateOf(true) }

    ApexTheme {
        val activity = LocalContext.current as? MainActivity
        val isDark = ApexColors.isDark
        LaunchedEffect(isDark) { activity?.setDarkSystemBars(isDark) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ApexColors.groupedBackground)
        ) {
            if (AuthManager.isAuthenticated) {
                HomeView()
            } else {
                LoginScreen()
            }

            val toast = ToastManager.toast
            AnimatedVisibility(
                visible = toast != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 60.dp)
            ) {
                // Keeps showing the last toast while it slides out.
                var shown by androidx.compose.runtime.remember { mutableStateOf(toast) }
                if (toast != null) shown = toast
                shown?.let { ToastView(it) }
            }

            if (showSplash) {
                SplashOverlay { showSplash = false }
            }
        }
    }
}

// Tabs and their navigation stacks for the logged-in user. Kept in a
// ViewModel so they survive rotation; a new login starts fresh.
class HomeViewModel : ViewModel() {
    private var token: String? = null
    var state: HomeState? = null
        private set

    fun stateFor(token: String?): HomeState {
        val current = state
        if (current != null && this.token == token) return current
        current?.dispose()
        this.token = token
        return HomeState().also { state = it }
    }

    override fun onCleared() {
        state?.dispose()
    }
}

class HomeState {
    val tabs: List<AppTab> = availableTabs()
    var selectedTab by mutableStateOf(tabs.first())

    private val navigators = mutableMapOf<AppTab, Navigator>()

    fun navigator(tab: AppTab): Navigator = navigators.getOrPut(tab) { Navigator(rootScreen(tab)) }

    fun select(tab: AppTab) {
        // Tapping the open tab again goes back to its first screen, as on iOS.
        if (tab == selectedTab) navigators[tab]?.popToRoot()
        selectedTab = tab
    }

    fun openPendingTab() {
        var tab = NotificationRouter.pendingTab ?: return
        NotificationRouter.pendingTab = null
        when {
            tab == AppTab.BODY_MEASUREMENTS -> {
                // Clients see measurements in progress on home,
                // coaches through their clients list.
                if (AppTab.HOME in tabs) {
                    tab = AppTab.HOME
                    val home = navigator(AppTab.HOME)
                    home.popToRoot()
                    home.push(ClientProgressScreen())
                } else {
                    tab = AppTab.CLIENTS
                }
            }
            // Clients open their sent requests from appointments.
            tab == AppTab.APPOINTMENT_REQUESTS && tab !in tabs -> tab = AppTab.APPOINTMENTS
        }
        if (tab in tabs) selectedTab = tab
    }

    fun dispose() {
        navigators.values.forEach { it.disposeAll() }
    }

    private fun rootScreen(tab: AppTab): Screen = when (tab) {
        AppTab.HOME -> ClientHomeScreen(openTab = ::select)
        AppTab.APPOINTMENTS -> AppointmentsScreen()
        AppTab.APPOINTMENT_REQUESTS -> AppointmentRequestsScreen()
        AppTab.WORKOUTS -> WorkoutsScreen()
        AppTab.CLIENTS -> ClientsScreen()
        // Clients see their measurements in progress on home.
        AppTab.BODY_MEASUREMENTS, AppTab.PROFILE -> UserProfileScreen()
        AppTab.TRAININGS -> CompletedTrainingsScreen()
    }

    private companion object {
        fun availableTabs(): List<AppTab> {
            val isClient = AuthManager.hasRole(Roles.CLIENT)
            val canGetAppointments = AuthManager.hasPermission(Permissions.CAN_GET_APPOINTMENTS)
            return buildList {
                if (isClient) {
                    // Clients: home, appointments, trainings and profile. Sent requests
                    // open from appointments, body measurements from home (progress).
                    add(AppTab.HOME)
                    add(if (canGetAppointments) AppTab.APPOINTMENTS else AppTab.APPOINTMENT_REQUESTS)
                    add(AppTab.TRAININGS)
                } else {
                    if (canGetAppointments) add(AppTab.APPOINTMENTS)
                    if (AuthManager.hasPermission(Permissions.CAN_GET_APPOINTMENT_REQUESTS)) {
                        add(AppTab.APPOINTMENT_REQUESTS)
                    }
                    // Role-based like the web: coaches and administrators manage workouts.
                    add(AppTab.WORKOUTS)
                    add(AppTab.CLIENTS)
                }
                add(AppTab.PROFILE)
            }
        }
    }
}

@Composable
private fun HomeView() {
    val viewModel: HomeViewModel = viewModel()
    val state = viewModel.stateFor(AuthManager.token)

    LaunchedEffect(state, NotificationRouter.pendingTab) {
        state.openPendingTab()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            // Each tab keeps its own stack, like a NavigationStack per tab on iOS.
            androidx.compose.runtime.key(state.selectedTab) {
                NavigatorHost(state.navigator(state.selectedTab))
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = ApexColors.separator)
        NavigationBar(containerColor = ApexColors.card, tonalElevation = 0.dp) {
            state.tabs.forEach { tab ->
                val (icon, label) = tab.iconAndLabel()
                NavigationBarItem(
                    selected = tab == state.selectedTab,
                    onClick = { state.select(tab) },
                    icon = { Icon(icon, contentDescription = label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ApexColors.accent,
                        unselectedIconColor = ApexColors.gray,
                        indicatorColor = ApexColors.accent.copy(alpha = 0.14f)
                    )
                )
            }
        }
    }
}

@Composable
private fun AppTab.iconAndLabel(): Pair<ImageVector, String> = when (this) {
    AppTab.HOME -> Icons.Outlined.Home to stringResource(R.string.tab_home)
    AppTab.APPOINTMENTS -> Icons.Outlined.CalendarMonth to stringResource(R.string.tab_appointments)
    AppTab.APPOINTMENT_REQUESTS -> Icons.Outlined.PendingActions to stringResource(R.string.tab_requests)
    AppTab.WORKOUTS -> Icons.Filled.FitnessCenter to stringResource(R.string.tab_workouts)
    AppTab.CLIENTS -> Icons.Outlined.Groups to stringResource(R.string.tab_clients)
    AppTab.BODY_MEASUREMENTS -> Icons.Outlined.Straighten to stringResource(R.string.body_measurements)
    AppTab.TRAININGS -> Icons.Outlined.FitnessCenter to stringResource(R.string.tab_trainings)
    AppTab.PROFILE -> Icons.Outlined.Person to stringResource(R.string.tab_profile)
}
