package software.rdd.apexperformance.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import software.rdd.apexperformance.R
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.Instant
import java.time.ZonedDateTime

// Period the progress charts show.
enum class ProgressPeriod(val months: Int, @StringRes val title: Int) {
    ONE_MONTH(1, R.string.period_one_month),
    THREE_MONTHS(3, R.string.period_three_months),
    SIX_MONTHS(6, R.string.period_six_months),
    YEAR(12, R.string.period_year),
    ALL(0, R.string.period_all);

    fun includes(date: Instant): Boolean {
        if (months <= 0) return true
        val start = ZonedDateTime.now().minusMonths(months.toLong()).toInstant()
        return !date.isBefore(start)
    }
}

// Menu to pick the period of the progress charts, e.g. "6 MJESECI ▾".
@Composable
fun ProgressPeriodMenu(period: ProgressPeriod, onSelect: (ProgressPeriod) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(period.title).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = ApexColors.secondaryLabel
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = ApexColors.secondaryLabel, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = ApexColors.card) {
            ProgressPeriod.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.title)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

// Centered "APEX" wordmark with a small subtitle, e.g. the client's plan.
@Composable
fun ApexTitleBar(subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("APEX", style = ApexText.heading(16).copy(letterSpacing = 4.sp))
        if (subtitle != null) {
            Text(
                subtitle.uppercase(),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = ApexColors.secondaryLabel
            )
        }
    }
}

// Small accent button used on pictures, e.g. "REZERVIRAJ TERMIN →".
@Composable
fun ApexCompactButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ApexColors.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            color = ApexColors.onAccent
        )
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ApexColors.onAccent, modifier = Modifier.size(12.dp))
    }
}
