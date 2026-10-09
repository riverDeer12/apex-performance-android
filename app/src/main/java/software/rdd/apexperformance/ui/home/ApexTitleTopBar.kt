package software.rdd.apexperformance.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.ui.components.ApexTitleBar
import software.rdd.apexperformance.ui.theme.ApexColors
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Navigation bar with the "APEX" wordmark in the middle (ApexTitleBar in the
// principal toolbar slot on iOS) and the back chevron when a screen is pushed.
@Composable
fun ApexTitleTopBar(subtitle: String? = null) {
    val navigator = LocalNavigator.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ApexColors.groupedBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(48.dp)
            .padding(horizontal = 4.dp)
    ) {
        if (navigator.canPop) {
            IconButton(
                onClick = { navigator.pop() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = stringResource(R.string.back_button),
                    tint = ApexColors.main,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        ApexTitleBar(subtitle = subtitle, modifier = Modifier.align(Alignment.Center))
    }
}

// For example "08.10.2026 18:00".
fun appointmentDateAndTime(instant: Instant): String {
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(instant.atZone(DateFormats.zone))
    return "${DateFormats.date(instant)} $time"
}
