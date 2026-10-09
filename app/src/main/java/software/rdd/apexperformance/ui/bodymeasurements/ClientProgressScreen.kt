package software.rdd.apexperformance.ui.bodymeasurements

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.model.BodyMeasurement
import software.rdd.apexperformance.model.MonthlyReview
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.ui.clients.MonthlyReviewsScreen
import software.rdd.apexperformance.ui.components.ApexSectionTitle
import software.rdd.apexperformance.ui.components.ApexTitle
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.ProgressPeriod
import software.rdd.apexperformance.ui.components.ProgressPeriodMenu
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.home.ApexTitleTopBar
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import software.rdd.apexperformance.ui.trainings.ClientTrainingProgressScreen
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToLong

// Client's progress for a month: the coach's monthly review, body mass
// with the change since the previous month, main circumferences and a
// link to training progress. Opened from home.
class ClientProgressScreen : Screen() {

    // Chosen month.
    private var month by mutableStateOf(YearMonth.now(DateFormats.zone))

    private var measurements by mutableStateOf<List<BodyMeasurement>>(emptyList())
    private var reviews by mutableStateOf<List<MonthlyReview>>(emptyList())
    private var trainings by mutableStateOf<List<Training>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var hasLoaded = false

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        LaunchedEffect(Unit) {
            if (!hasLoaded) {
                hasLoaded = true
                load()
            }
        }

        ScrollScreen(
            topBar = { ApexTitleTopBar() },
            onRefresh = { load() }
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    ApexTitle(stringResource(R.string.progress))
                    Spacer(Modifier.weight(1f))
                    MonthMenu()
                }

                ReviewCard()

                BodyMassCard()

                CircumferencesCard()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .apexCard()
                        .clickable { navigator.push(ClientTrainingProgressScreen(clientName = "", trainings = trainings)) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ApexSectionTitle(stringResource(R.string.training_progress_title))
                    Spacer(Modifier.weight(1f))
                    Chevron()
                }
            }
        }
    }

    // MARK: - Month

    // This month and the 11 before it.
    private val months: List<YearMonth>
        get() {
            val current = YearMonth.now(DateFormats.zone)
            return (0L until 12L).map { current.minusMonths(it) }
        }

    // Start of the chosen month and of the next one.
    private val monthStart: Instant get() = month.atDay(1).atStartOfDay(DateFormats.zone).toInstant()
    private val monthEnd: Instant get() = month.plusMonths(1).atDay(1).atStartOfDay(DateFormats.zone).toInstant()

    @Composable
    private fun MonthMenu() {
        var expanded by remember { mutableStateOf(false) }

        Box {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        monthTitle(month),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = ApexColors.secondaryLabel
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = ApexColors.secondaryLabel,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ApexColors.border)
                )
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = ApexColors.card) {
                months.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(monthTitle(option)) },
                        onClick = {
                            expanded = false
                            month = option
                        }
                    )
                }
            }
        }
    }

    // MARK: - Monthly review

    // Review of the chosen month, or the latest one written before it.
    private val review: MonthlyReview?
        get() {
            val end = month.plusMonths(1).atDay(1)
            return reviews.firstOrNull { it.monthDate.isBefore(end) }
        }

    @Composable
    private fun ReviewCard() {
        val navigator = LocalNavigator.current
        val review = review

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .apexCard()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ApexSectionTitle(stringResource(R.string.coach_monthly_review))

            if (review != null) {
                Text(
                    monthTitle(review.monthDate),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = ApexColors.secondaryLabel
                )
                Text(review.content, style = ApexText.subheadline)
            } else if (!isLoading) {
                Text(stringResource(R.string.no_monthly_reviews), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
            }

            if (reviews.size > 1) {
                HorizontalDivider(color = ApexColors.border, thickness = 1.dp)
                AccentLink(R.string.previous_reviews, fillsWidth = true) {
                    navigator.push(MonthlyReviewsScreen(reviews))
                }
            }
        }
    }

    // Uppercase accent link with a chevron, e.g. "PRETHODNE RECENZIJE >".
    @Composable
    private fun AccentLink(@StringRes title: Int, fillsWidth: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Row(
            modifier = modifier
                .then(if (fillsWidth) Modifier.fillMaxWidth() else Modifier)
                .clickable(onClick = onClick)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(title).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = ApexColors.accent,
                modifier = if (fillsWidth) Modifier.weight(1f) else Modifier
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ApexColors.accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    // MARK: - Body mass

    // Newest first, measured before the end of the chosen month.
    private val measurementsUpToMonth: List<BodyMeasurement>
        get() {
            val end = monthEnd
            return measurements.filter { it.measuredAt.isBefore(end) }
        }

    // Latest measurement before the chosen month, to compare with.
    private val previousMonthMeasurement: BodyMeasurement?
        get() {
            val start = monthStart
            return measurements.firstOrNull { it.measuredAt.isBefore(start) }
        }

    @Composable
    private fun BodyMassCard() {
        val upToMonth = measurementsUpToMonth
        val latest = upToMonth.firstOrNull()
        val previous = previousMonthMeasurement
        val chartStart = month.minusMonths(2).atDay(1).atStartOfDay(DateFormats.zone).toInstant()
        val points = upToMonth
            .filter { !it.measuredAt.isBefore(chartStart) && it.weight > 0 }
            .sortedBy { it.measuredAt }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .apexCard()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ApexSectionTitle(stringResource(R.string.body_mass))
                    if (latest != null) {
                        Text(
                            "${formatMeasurement(latest.weight)} KG",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = ApexColors.label
                        )
                    } else {
                        Text(stringResource(R.string.no_measurements), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
                    }
                }

                Spacer(Modifier.weight(1f))

                if (latest != null && previous != null && previous.id != latest.id) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ChangeLabel(latest.weight - previous.weight, unit = "kg", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(
                                R.string.compared_to_month,
                                monthName(previous.measuredAt.atZone(DateFormats.zone).month)
                            ).uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                            color = ApexColors.secondaryLabel
                        )
                    }
                }
            }

            if (points.size > 1) {
                WeightLineChart(
                    points = points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                )
            }
        }
    }

    // MARK: - Circumferences

    private class Circumference(@StringRes val title: Int, val value: (BodyMeasurement) -> Double)

    private val circumferences = listOf(
        Circumference(R.string.waist) { it.waist },
        Circumference(R.string.upper_arm) { it.upperArm },
        Circumference(R.string.thigh) { it.thigh }
    )

    @Composable
    private fun CircumferencesCard() {
        val navigator = LocalNavigator.current
        val upToMonth = measurementsUpToMonth
        val latest = upToMonth.firstOrNull()
        val previous = upToMonth.getOrNull(1)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .apexCard()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ApexSectionTitle(stringResource(R.string.body_circumferences))
                Spacer(Modifier.weight(1f))
                Text("CM", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ApexColors.secondaryLabel)
            }

            if (latest != null) {
                circumferences.forEach { row ->
                    val value = row.value(latest)
                    HorizontalDivider(color = ApexColors.border, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(row.title).uppercase(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ApexColors.label,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (value > 0) formatMeasurement(value) else "—",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ApexColors.label,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(56.dp)
                        )
                        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
                            if (previous != null) {
                                ChangeLabel(value - row.value(previous), unit = "", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            } else {
                                Text("—", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ApexColors.secondaryLabel)
                            }
                        }
                    }
                }

                AccentLink(R.string.show_all_measurements, fillsWidth = false, modifier = Modifier.padding(top = 8.dp)) {
                    navigator.push(AllMeasurementsScreen(measurements))
                }
            } else {
                Text(stringResource(R.string.no_measurements), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
            }
        }
    }

    // Change in the accent color with an arrow, e.g. "↓ 0,8 kg".
    @Composable
    private fun ChangeLabel(change: Double, unit: String, fontSize: TextUnit, fontWeight: FontWeight) {
        val rounded = (change * 10).roundToLong() / 10.0
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (rounded != 0.0) {
                Icon(
                    if (rounded < 0) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = ApexColors.accent,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                "${formatMeasurement(abs(rounded))} $unit".trim(),
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = ApexColors.accent
            )
        }
    }

    // MARK: - Data

    private suspend fun load() {
        isLoading = true
        try {
            try {
                measurements = loadMyBodyMeasurements()
                trainings = Training.loadCompleted()
            } catch (e: Exception) {
                showError(e)
            }

            // Reviews are an extra, progress works without them.
            try {
                reviews = MonthlyReview.load()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        } finally {
            isLoading = false
        }
    }
}

// Month name and year of a month, e.g. "LISTOPAD 2026".
fun monthTitle(month: YearMonth): String =
    "${monthName(month.month)} ${month.year}".uppercase()

fun monthTitle(date: LocalDate): String = monthTitle(YearMonth.from(date))

// Weight line over a short time, used on the progress screen. Oldest first.
@Composable
fun WeightLineChart(points: List<BodyMeasurement>, modifier: Modifier = Modifier) {
    val weights = points.map { it.weight }
    val padding = maxOf(0.5, (weights.max() - weights.min()) * 0.2)
    val first = points.first().measuredAt
    val last = points.last().measuredAt

    MeasurementLineChart(
        points = points.map { ChartPoint(it.measuredAt, it.weight) },
        domain = (weights.min() - padding)..(weights.max() + padding),
        xLabels = dayLabels(first, last),
        yLabel = ::formatAxisNumber,
        description = stringResource(R.string.weight_progress),
        pointRadius = 2.dp,
        lastPointRadius = 3.2.dp,
        modifier = modifier
    )
}

// Up to five day and month labels spread over the dates, e.g. "8. 10.".
private fun dayLabels(from: Instant, to: Instant): List<Pair<Instant, String>> {
    val count = if (from == to) 1 else 5
    val step = if (count > 1) (to.epochSecond - from.epochSecond) / (count - 1) else 0L
    return (0 until count)
        .map { Instant.ofEpochSecond(from.epochSecond + step * it) }
        .map { instant ->
            val date = instant.atZone(DateFormats.zone)
            instant to "${date.dayOfMonth}.${date.monthValue}."
        }
        .distinctBy { it.second }
}

// Axis value without trailing zeros, e.g. "80" or "80,5".
private fun formatAxisNumber(value: Double): String =
    java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)

// All body measurements with the period charts and history.
class AllMeasurementsScreen(private val measurements: List<BodyMeasurement>) : Screen() {

    private var period by mutableStateOf(ProgressPeriod.SIX_MONTHS)

    @Composable
    override fun Content() {
        ScrollScreen(topBar = { TopBar() }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    ApexTitle(stringResource(R.string.body_measurements))
                    Spacer(Modifier.weight(1f))
                    ProgressPeriodMenu(period) { period = it }
                }

                BodyProgress(measurements, period)
            }
        }
    }
}
