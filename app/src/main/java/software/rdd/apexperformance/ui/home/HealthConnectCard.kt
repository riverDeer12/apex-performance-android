package software.rdd.apexperformance.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.health.HealthConnect
import software.rdd.apexperformance.core.health.HealthMath
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import software.rdd.apexperformance.ui.components.ApexSectionTitle
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.text.NumberFormat

// The client's day from Health Connect on home (Mi Fitness, Google Fit, Fitbit,
// Samsung Health... write into it): sleep last night, steps, latest resting heart
// rate and active energy today. Read on the phone and shown only here; nothing is
// sent to the server. Same as the Apple Health card on the iPhone.
@Composable
fun HealthConnectCard(refreshKey: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val availability = remember(refreshKey) { HealthConnect.availability(context) }
    // Health Connect isn't on this phone and can't be installed (Android 8).
    if (availability == HealthConnect.Availability.UNAVAILABLE) return

    var isConnected by remember { mutableStateOf<Boolean?>(null) }
    var summary by remember { mutableStateOf<HealthConnect.DailySummary?>(null) }
    var isConnecting by remember { mutableStateOf(false) }

    // Changes when home reloads (pull to refresh): the card reads Health Connect again.
    LaunchedEffect(refreshKey, availability) {
        if (availability != HealthConnect.Availability.AVAILABLE) return@LaunchedEffect
        val connected = HealthConnect.isDailyConnected()
        isConnected = connected
        if (connected) summary = HealthConnect.dailySummary()
    }

    val permissions = rememberLauncherForActivityResult(HealthConnect.permissionContract()) { granted ->
        scope.launch {
            val connected = granted.any { it in HealthConnect.dailyPermissions } || HealthConnect.isDailyConnected()
            isConnected = connected
            if (connected) {
                summary = HealthConnect.dailySummary()
            } else {
                ToastManager.show(R.string.health_not_allowed, ToastType.ERROR)
            }
            isConnecting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("health-card")
            .apexCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Header(onClick = {
            if (availability == HealthConnect.Availability.AVAILABLE) HealthConnect.openSettings(context)
            else HealthConnect.openStore(context)
        })

        when {
            availability == HealthConnect.Availability.NEEDS_INSTALL_OR_UPDATE -> {
                Text(stringResource(R.string.health_install_hint), style = ApexText.footnote, color = ApexColors.secondaryLabel)
                ApexPrimaryButton(
                    stringResource(R.string.health_install),
                    icon = Icons.Filled.Favorite
                ) { HealthConnect.openStore(context) }
            }
            isConnected == true -> Values(summary)
            isConnected == false -> {
                Text(stringResource(R.string.health_daily_connect_hint), style = ApexText.footnote, color = ApexColors.secondaryLabel)
                ApexPrimaryButton(
                    stringResource(R.string.health_connect_button),
                    modifier = Modifier.testTag("health-connect-button"),
                    icon = Icons.Filled.Favorite,
                    isLoading = isConnecting
                ) {
                    isConnecting = true
                    permissions.launch(HealthConnect.dailyPermissions)
                }
            }
        }
    }
}

@Composable
private fun Header(onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = ApexColors.accent,
            modifier = Modifier.size(26.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ApexSectionTitle("Health Connect")
            Text(stringResource(R.string.health_daily_subtitle), style = ApexText.footnote, color = ApexColors.secondaryLabel)
        }
        Chevron()
    }
}

@Composable
private fun Values(summary: HealthConnect.DailySummary?) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val bpm = stringResource(R.string.bpm)
    val sleep = summary?.sleepMinutes?.let { minutes ->
        val (hours, rest) = HealthMath.sleepParts(minutes)
        stringResource(R.string.health_sleep_value, hours, rest)
    }

    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Tile(Icons.Filled.Bed, stringResource(R.string.health_sleep), sleep ?: "–", stringResource(R.string.health_last_night))
        VerticalDivider(modifier = Modifier.fillMaxHeight(), color = ApexColors.separator)
        Tile(
            Icons.AutoMirrored.Filled.DirectionsWalk, stringResource(R.string.health_steps),
            summary?.steps?.let { numbers.format(it) } ?: "–", stringResource(R.string.health_today)
        )
        VerticalDivider(modifier = Modifier.fillMaxHeight(), color = ApexColors.separator)
        Tile(
            Icons.Outlined.MonitorHeart, stringResource(R.string.health_resting_heart_rate),
            summary?.restingHeartRate?.let { "$it $bpm" } ?: "–", stringResource(R.string.health_latest)
        )
    }

    HorizontalDivider(color = ApexColors.separator)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.LocalFireDepartment,
            contentDescription = null,
            tint = ApexColors.accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.health_active_energy), style = ApexText.subheadline, color = ApexColors.label)
        Spacer(Modifier.weight(1f))
        Text(
            summary?.activeEnergyKilocalories?.let { "${numbers.format(it)} kcal" } ?: "–",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = ApexColors.label
        )
    }

    if (summary != null) {
        Text(
            if (summary.isEmpty) stringResource(R.string.health_no_data)
            else stringResource(R.string.health_updated_at, DateFormats.time(summary.updatedAt)),
            style = ApexText.caption,
            color = ApexColors.secondaryLabel
        )
    }
}

@Composable
private fun RowScope.Tile(icon: ImageVector, title: String, value: String, caption: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = ApexColors.accent, modifier = Modifier.size(15.dp))
            Text(
                title,
                style = ApexText.footnote.copy(fontWeight = FontWeight.Medium),
                color = ApexColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = ApexColors.label, maxLines = 1)
        Text(caption, style = ApexText.caption, color = ApexColors.secondaryLabel)
    }
}
