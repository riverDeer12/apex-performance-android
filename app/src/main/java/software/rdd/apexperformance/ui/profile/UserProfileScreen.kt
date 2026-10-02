package software.rdd.apexperformance.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.PushTokenService
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.model.ClientPlan
import software.rdd.apexperformance.model.Profile
import software.rdd.apexperformance.model.UserProfile
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.IconTile
import software.rdd.apexperformance.ui.components.ProfilePicture
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScreenHeader
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.components.StatTile
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

class UserProfileScreen : Screen() {

    // Credits and plan, only for clients.
    private var clientProfile by mutableStateOf<UserProfile?>(null)
    // Account data from api/profile, same for all roles.
    private var accountProfile by mutableStateOf<Profile?>(null)
    private var isLoading by mutableStateOf(false)
    private var hasLoaded = false
    private var showLogoutDialog by mutableStateOf(false)

    private val isClient: Boolean get() = AuthManager.hasRole(Roles.CLIENT)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        LaunchedEffect(Unit) {
            if (!hasLoaded) {
                hasLoaded = true
                loadUserProfile()
            }
        }

        ScrollScreen(showLoading = isLoading && accountProfile == null) {
            ScreenHeader(stringResource(R.string.my_profile), stringResource(R.string.account_details_and_settings))

            ProfileContent()

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.profile_management)) {
                Column {
                    accountProfile?.let { profile ->
                        SettingsRow(
                            icon = Icons.Outlined.AccountCircle,
                            iconTint = ApexColors.main,
                            title = stringResource(R.string.edit_profile),
                            showChevron = true,
                            onClick = { navigator.push(EditProfileScreen(profile) { accountProfile = it }) }
                        )
                        RowDivider()
                    }

                    SettingsRow(
                        icon = Icons.Outlined.Edit,
                        iconTint = ApexColors.main,
                        title = stringResource(R.string.change_username),
                        showChevron = true,
                        onClick = { navigator.push(ChangeUsernameScreen()) }
                    )
                    RowDivider()

                    SettingsRow(
                        icon = Icons.Outlined.Key,
                        iconTint = ApexColors.main,
                        title = stringResource(R.string.change_password),
                        showChevron = true,
                        onClick = { navigator.push(ChangePasswordScreen()) }
                    )
                    RowDivider()

                    SettingsRow(
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        iconTint = ApexColors.red,
                        title = stringResource(R.string.logout),
                        showChevron = true,
                        titleColor = ApexColors.red,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }
        }

        if (showLogoutDialog) {
            ConfirmDialog(
                title = stringResource(R.string.logout_question),
                message = stringResource(R.string.you_will_be_signed_out),
                confirmText = stringResource(R.string.logout),
                onConfirm = {
                    PushTokenService.unregister()
                    AuthManager.logout()
                },
                onDismiss = { showLogoutDialog = false }
            )
        }
    }

    @Composable
    private fun ProfileContent() {
        val profile = accountProfile
        if (profile == null) {
            if (!isLoading) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.profile), style = ApexText.headline)
                        Text(stringResource(R.string.profile_is_not_provided), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
                    }
                }
            }
            return
        }

        CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfilePicture(profile)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fullName(profile), style = ApexText.headline)
                    Text("@${AuthManager.username}", style = ApexText.subheadline, color = ApexColors.secondaryLabel)
                }
            }
        }

        if (isClient) {
            Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                StatTile("${clientProfile?.credits ?: 0}", stringResource(R.string.credits), Modifier.weight(1f))
            }
        }

        CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.details)) {
            Column {
                if (profile.hasPersonalData) {
                    InfoRow(Icons.Outlined.Person, stringResource(R.string.first_name), profile.firstName ?: "—")
                    RowDivider()
                    InfoRow(Icons.Outlined.Person, stringResource(R.string.last_name), profile.lastName ?: "—")
                    RowDivider()
                }
                InfoRow(Icons.Outlined.AlternateEmail, stringResource(R.string.username), AuthManager.username)
                RowDivider()
                InfoRow(Icons.Outlined.Email, stringResource(R.string.email), profile.email)
                if (profile.hasPhone) {
                    RowDivider()
                    InfoRow(Icons.Outlined.Phone, stringResource(R.string.mobile_phone), profile.phone ?: "—")
                }
                if (isClient) {
                    RowDivider()
                    InfoRow(Icons.Outlined.CreditCard, stringResource(R.string.credits), "${clientProfile?.credits ?: 0}")
                    ClientPlan.from(clientProfile?.plan)?.let { plan ->
                        RowDivider()
                        InfoRow(Icons.Outlined.FitnessCenter, stringResource(R.string.plan), stringResource(plan.title))
                    }
                }
            }
        }
    }

    @Composable
    private fun InfoRow(icon: ImageVector, title: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(icon, ApexColors.main, backgroundAlpha = 0.25f)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = ApexText.caption, color = ApexColors.secondaryLabel)
                Text(value, style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.weight(1f))
        }
    }

    private fun fullName(profile: Profile): String {
        if (!profile.hasPersonalData) return AuthManager.username
        return "${profile.firstName ?: "—"} ${profile.lastName.orEmpty()}".trim()
    }

    private suspend fun loadUserProfile() {
        isLoading = true
        try {
            accountProfile = ApiClient.get("profile")
            // Credits are only on client data.
            if (isClient) clientProfile = ApiClient.get("clients/current-client")
        } catch (e: Exception) {
            // Same as iOS, the card below says the profile isn't available.
        } finally {
            isLoading = false
        }
    }
}
