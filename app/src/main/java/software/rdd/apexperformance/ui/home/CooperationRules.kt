package software.rdd.apexperformance.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.ApiException
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.model.IsoInstant
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Cooperation rules for individual trainings (GET rules). Clients on
// private coaching accept them before using the app; a new version has
// to be accepted again. Like CooperationRules on iOS.
@Serializable
data class CooperationRules(
    val document: String = "",
    // Version to accept, sent back when accepting.
    val version: String,
    // PDF of the rules.
    val documentUrl: String = "",
    val isRequired: Boolean = false,
    // The current version is accepted.
    val isAccepted: Boolean = false,
    val acceptedVersion: String? = null,
    val acceptedAt: IsoInstant? = null
) {
    val needsAcceptance: Boolean get() = isRequired && !isAccepted

    @Serializable
    data class AcceptRequest(
        val version: String,
        val guardianName: String?,
        val independentTravelApproved: Boolean
    )

    companion object {
        // ErrorCodes.NotValid on the API: the rules changed since they were loaded.
        const val OUTDATED_VERSION_ERROR_CODE = "1001"

        suspend fun load(): CooperationRules = ApiClient.get("rules")

        suspend fun accept(version: String, guardianName: String?, independentTravelApproved: Boolean): CooperationRules =
            ApiClient.request("rules/accept", HttpMethod.POST, AcceptRequest(version, guardianName, independentTravelApproved))
    }
}

// Rules the logged client still has to accept, shown as a popup over the app.
object CooperationRulesGate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var pending by mutableStateOf<CooperationRules?>(null)

    // On entry and when the app comes back: a new version can come any time.
    fun checkLater() {
        if (AuthManager.token == null || !AuthManager.hasRole(Roles.CLIENT) || pending != null) return
        scope.launch {
            val rules = runCatching { CooperationRules.load() }.getOrNull() ?: return@launch
            if (rules.needsAcceptance) pending = rules
        }
    }

    // After logout.
    fun reset() {
        pending = null
    }
}

// Popup over the whole app that a client who has to accept the rules
// can't close: they accept them or log out.
@Composable
fun CooperationRulesPopup(rules: CooperationRules, onAccepted: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember(rules) { mutableStateOf(rules) }
    var hasReadRules by remember { mutableStateOf(false) }
    // Accepting as a parent or guardian of a minor client.
    var isGuardian by remember { mutableStateOf(false) }
    var guardianName by remember { mutableStateOf("") }
    var approvesIndependentTravel by remember { mutableStateOf(false) }
    var isAccepting by remember { mutableStateOf(false) }
    val canAccept = hasReadRules && (!isGuardian || guardianName.isNotBlank()) && !isAccepting
    val newVersionMessage = stringResource(R.string.rules_new_version)

    // Back doesn't close it either.
    BackHandler {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Covers the app, nothing behind can be used.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ApexColors.groupedBackground)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                stringResource(R.string.rules_title),
                style = ApexText.headline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = ApexColors.main,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                stringResource(R.string.rules_message),
                style = ApexText.body,
                color = ApexColors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // The PDF opens in the browser or a PDF app.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexColors.main.copy(alpha = 0.1f))
                    .clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.documentUrl))) }
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = ApexColors.main, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.rules_open_document),
                    style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = ApexColors.main,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            RulesCheckbox(stringResource(R.string.rules_checkbox, current.version), hasReadRules) { hasReadRules = it }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexColors.card)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RulesCheckbox(stringResource(R.string.rules_guardian), isGuardian) { isGuardian = it }
                if (isGuardian) {
                    PlainTextField(
                        value = guardianName,
                        onValueChange = { guardianName = it },
                        placeholder = stringResource(R.string.rules_guardian_name),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ApexColors.label.copy(alpha = 0.06f))
                            .padding(12.dp)
                    )
                    RulesCheckbox(stringResource(R.string.rules_independent_travel), approvesIndependentTravel) {
                        approvesIndependentTravel = it
                    }
                }
            }

            ApexPrimaryButton(
                text = stringResource(R.string.rules_accept),
                icon = Icons.Filled.Check,
                enabled = canAccept,
                isLoading = isAccepting
            ) {
                isAccepting = true
                scope.launch {
                    try {
                        CooperationRules.accept(
                            version = current.version,
                            guardianName = if (isGuardian) guardianName.trim() else null,
                            independentTravelApproved = isGuardian && approvesIndependentTravel
                        )
                        onAccepted()
                    } catch (e: ApiException.Validation) {
                        if (e.response.errors?.get("generalErrors")?.contains(CooperationRules.OUTDATED_VERSION_ERROR_CODE) == true) {
                            // The rules changed in the meantime: show the new version.
                            runCatching { CooperationRules.load() }.onSuccess {
                                current = it
                                hasReadRules = false
                            }
                            ToastManager.show(newVersionMessage, ToastType.ERROR)
                        } else {
                            showError(e)
                        }
                    } catch (e: Exception) {
                        showError(e)
                    } finally {
                        isAccepting = false
                    }
                }
            }

            Text(
                stringResource(R.string.logout),
                style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = ApexColors.red,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onLogout)
                    .padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun RulesCheckbox(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (checked) ApexColors.main else ApexColors.secondaryLabel
        )
        Text(title, style = ApexText.subheadline, color = ApexColors.label, modifier = Modifier.weight(1f))
    }
}

// Accepted rules in the client's profile: version, date and the PDF.
@Composable
fun CooperationRulesRow() {
    val context = LocalContext.current
    var rules by remember { mutableStateOf<CooperationRules?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        rules = runCatching { CooperationRules.load() }.getOrNull()
    }
    val loaded = rules ?: return
    val acceptedVersion = loaded.acceptedVersion ?: return
    SettingsRow(
        icon = Icons.Outlined.Description,
        iconTint = ApexColors.main,
        title = stringResource(R.string.rules_title),
        subtitle = stringResource(R.string.rules_accepted_version, acceptedVersion) +
            (loaded.acceptedAt?.let { " · " + DateFormats.date(it) } ?: ""),
        showChevron = true,
        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(loaded.documentUrl))) } }
    )
    RowDivider()
}
