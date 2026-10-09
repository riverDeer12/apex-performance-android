package software.rdd.apexperformance.ui.bodymeasurements

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.model.BodyMeasurement
import software.rdd.apexperformance.model.BodyMeasurementResponse
import software.rdd.apexperformance.ui.components.ApexLabel
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.ProgressPeriod
import software.rdd.apexperformance.ui.components.SmallProgress
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

// Client's body progress: weight over the chosen period, latest body
// circumferences with the change since the previous measurement, and
// the measurement history. Measurements are newest first.
@Composable
fun BodyProgress(measurements: List<BodyMeasurement>, period: ProgressPeriod, isLoading: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (measurements.isEmpty()) {
            CardView {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            SmallProgress()
                        }
                    } else {
                        Text(stringResource(R.string.no_measurements), style = ApexText.body, color = ApexColors.secondaryLabel)
                    }
                }
            }
        } else {
            BodyMassCard(measurements, period)
            CircumferencesCard(measurements)
            HistoryCard(measurements)
        }
    }
}

// MARK: - Body mass

@Composable
private fun BodyMassCard(measurements: List<BodyMeasurement>, period: ProgressPeriod) {
    val points = measurements
        .filter { period.includes(it.measuredAt) && it.weight > 0 }
        .sortedBy { it.measuredAt }
        .map { ChartPoint(it.measuredAt, it.weight) }
    val latest = measurements.firstOrNull()?.weight ?: 0.0

    CardView(title = stringResource(R.string.body_mass)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${formatMeasurement(latest)} KG",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = ApexColors.label,
                    modifier = Modifier.alignByBaseline()
                )

                Spacer(Modifier.weight(1f))

                if (points.size > 1) {
                    val change = points.last().value - points.first().value
                    Text(
                        "${signedMeasurement(change)} kg",
                        style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = measurementChangeColor(change),
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }

            if (points.size > 1) {
                WeightChart(points)
            } else {
                Text(
                    stringResource(R.string.not_enough_for_chart),
                    style = ApexText.subheadline,
                    color = ApexColors.secondaryLabel
                )
            }
        }
    }
}

@Composable
private fun WeightChart(points: List<ChartPoint>) {
    val weights = points.map { it.value }
    val padding = maxOf(1.0, (weights.max() - weights.min()) * 0.15)
    val lower = weights.min() - padding
    val upper = weights.max() + padding

    MeasurementLineChart(
        points = points,
        domain = lower..upper,
        xLabels = monthLabels(points.first().date, points.last().date),
        yLabel = ::formatMeasurement,
        description = stringResource(R.string.weight_progress),
        smooth = true,
        showsArea = true,
        showsLastValue = true,
        pointRadius = 2.5.dp,
        lastPointRadius = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
    )
}

// Short month names at the start of each month between the dates,
// at most about five of them.
private fun monthLabels(from: Instant, to: Instant): List<Pair<Instant, String>> {
    val start = DateFormats.localDate(from)
    val end = DateFormats.localDate(to)
    val months = mutableListOf<LocalDate>()
    var month = start.withDayOfMonth(1).let { if (it.isBefore(start)) it.plusMonths(1) else it }
    while (!month.isAfter(end)) {
        months.add(month)
        month = month.plusMonths(1)
    }
    if (months.isEmpty()) months.add(start)
    val stride = (months.size + 4) / 5
    return months
        .filterIndexed { index, _ -> index % stride == 0 }
        .map { date ->
            val instant = maxOf(date.atStartOfDay(DateFormats.zone).toInstant(), from)
            instant to date.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())
        }
}

// MARK: - Circumferences

private class CircumferenceRow(@StringRes val title: Int, val value: (BodyMeasurement) -> Double)

private val circumferenceRows = listOf(
    CircumferenceRow(R.string.shoulders) { it.shoulders },
    CircumferenceRow(R.string.chest) { it.chest },
    CircumferenceRow(R.string.waist) { it.waist },
    CircumferenceRow(R.string.upper_arm) { it.upperArm },
    CircumferenceRow(R.string.glutes) { it.glutes },
    CircumferenceRow(R.string.thigh) { it.thigh },
    CircumferenceRow(R.string.calves) { it.calves }
)

@Composable
private fun CircumferencesCard(measurements: List<BodyMeasurement>) {
    val latest = measurements[0]
    val previous = measurements.getOrNull(1)

    CardView {
        Column {
            Row(
                modifier = Modifier.padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ApexLabel(stringResource(R.string.body_circumferences))
                Spacer(Modifier.weight(1f))
                Text(DateFormats.date(latest.measuredAt), style = ApexText.caption, color = ApexColors.secondaryLabel)
            }

            TableRow(label = "", value = "CM", change = "Δ", isHeader = true)

            circumferenceRows.forEach { row ->
                val value = row.value(latest)
                val change = previous?.let { value - row.value(it) }

                HorizontalDivider(color = ApexColors.border, thickness = 1.dp)
                TableRow(
                    label = stringResource(row.title),
                    value = if (value > 0) formatMeasurement(value) else "—",
                    change = change?.let { signedMeasurement(it) } ?: "—",
                    changeColor = change?.let { measurementChangeColor(it) } ?: ApexColors.secondaryLabel
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    label: String,
    value: String,
    change: String,
    isHeader: Boolean = false,
    changeColor: Color = ApexColors.secondaryLabel
) {
    val style = if (isHeader) {
        ApexText.caption2.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    } else {
        ApexText.subheadline
    }
    val color = if (isHeader) ApexColors.secondaryLabel else ApexColors.label

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isHeader) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), style = style, color = color, modifier = Modifier.weight(1f))
        Text(value.uppercase(), style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
        Text(
            change.uppercase(),
            style = style,
            color = if (isHeader) ApexColors.secondaryLabel else changeColor,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp)
        )
    }
}

// MARK: - History

@Composable
private fun HistoryCard(measurements: List<BodyMeasurement>) {
    val navigator = LocalNavigator.current

    CardView(title = stringResource(R.string.measurement_history)) {
        Column {
            measurements.forEachIndexed { index, measurement ->
                HistoryRow(measurement, previous = measurements.getOrNull(index + 1)) {
                    navigator.push(BodyMeasurementDetailsScreen(measurement, isEditable = false))
                }
                if (index != measurements.lastIndex) {
                    HorizontalDivider(color = ApexColors.border, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(measurement: BodyMeasurement, previous: BodyMeasurement?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(DateFormats.date(measurement.measuredAt), style = ApexText.subheadline)

        Spacer(Modifier.weight(1f))

        Text(
            "${formatMeasurement(measurement.weight)} kg",
            style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold)
        )

        if (previous != null) {
            Text(
                signedMeasurement(measurement.weight - previous.weight),
                style = ApexText.caption,
                color = ApexColors.secondaryLabel,
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp)
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ApexColors.tertiaryLabel,
            modifier = Modifier.size(18.dp)
        )
    }
}

// MARK: - Formatting

// Weight down is shown as progress (green), up as orange.
fun measurementChangeColor(change: Double): Color = when {
    change < 0 -> ApexColors.green
    change > 0 -> ApexColors.orange
    else -> ApexColors.secondaryLabel
}

// One decimal in the device's format, e.g. "80,4".
fun formatMeasurement(value: Double): String =
    NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(value)

// For example "+1,0" or "−0,8".
fun signedMeasurement(value: Double): String {
    val rounded = (value * 10).roundToLong() / 10.0
    val number = formatMeasurement(abs(rounded))
    return when {
        rounded > 0 -> "+$number"
        rounded < 0 -> "−$number"
        else -> number
    }
}

// Full month name in the app language, e.g. "October".
fun monthName(month: Month): String =
    month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())

// Logged client's own measurements, newest first.
suspend fun loadMyBodyMeasurements(): List<BodyMeasurement> {
    // GET body-measurements returns only the logged-in client's own
    // measurements, with the measurement date sent as createdAt.
    val response: List<BodyMeasurementResponse> = ApiClient.get("body-measurements")
    return response.map { it.toBodyMeasurement() }.sortedByDescending { it.measuredAt }
}
