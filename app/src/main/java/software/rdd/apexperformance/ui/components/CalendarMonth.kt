package software.rdd.apexperformance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun CalendarMonth(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    approvedDates: Set<LocalDate> = emptySet(),
    pendingDates: Set<LocalDate> = emptySet()
) {
    var displayedMonth by rememberSaveable { mutableStateOf(YearMonth.from(selectedDate)) }
    val locale = Locale.getDefault()
    val today = LocalDate.now()

    LaunchedEffect(selectedDate) {
        if (YearMonth.from(selectedDate) != displayedMonth) displayedMonth = YearMonth.from(selectedDate)
    }

    // Week starts on the locale's first weekday (Monday in Croatia and Italy).
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val weekdays = (0 until 7).map { firstDayOfWeek.plus(it.toLong()) }

    // Rows of 7 days, with nulls for days of the adjacent months.
    val leading = (displayedMonth.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val days: List<LocalDate?> = buildList {
        repeat(leading) { add(null) }
        for (day in 1..displayedMonth.lengthOfMonth()) add(displayedMonth.atDay(day))
        while (size % 7 != 0) add(null)
    }

    val monthTitle = DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(displayedMonth)
        .replaceFirstChar { it.titlecase(locale) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = ApexColors.main)
            }
            Spacer(Modifier.weight(1f))
            Text(monthTitle, style = ApexText.headline)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ApexColors.main)
            }
        }

        Row {
            weekdays.forEach { day: DayOfWeek ->
                Text(
                    day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    style = ApexText.caption.copy(fontWeight = FontWeight.Medium),
                    color = ApexColors.secondaryLabel,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            days.chunked(7).forEach { week ->
                Row {
                    week.forEach { day ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (day == null) {
                                Spacer(Modifier.height(41.dp))
                            } else {
                                DayCell(
                                    day = day,
                                    isSelected = day == selectedDate,
                                    isToday = day == today,
                                    hasApproved = day in approvedDates,
                                    hasPending = day in pendingDates,
                                    onClick = { onSelect(day) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasApproved: Boolean,
    hasPending: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isSelected) ApexColors.main else Color.Transparent)
                .border(1.dp, if (isToday && !isSelected) ApexColors.main else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${day.dayOfMonth}",
                style = ApexText.subheadline.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
                color = when {
                    // The main color is white in dark mode.
                    isSelected -> if (ApexColors.isDark) Color.Black else Color.White
                    isToday -> ApexColors.main
                    else -> ApexColors.label
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(5.dp)) {
            if (hasApproved) Dot(ApexColors.main)
            if (hasPending) Dot(ApexColors.orange)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(color)
    )
}
