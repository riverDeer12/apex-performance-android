package software.rdd.apexperformance.ui.trainings

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.model.Workout
import software.rdd.apexperformance.model.mainSetCount
import software.rdd.apexperformance.ui.components.ApexLabel
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.ProgressPeriod
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import software.rdd.apexperformance.ui.workouts.WorkoutMuscle
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

// Charts from the client's completed trainings in the chosen period:
// summary, max weight per exercise, volume, repetitions, trainings per
// week and the share of sets per muscle group.
@Composable
fun TrainingProgress(
    trainings: List<Training>,
    workouts: List<Workout>,
    period: ProgressPeriod,
    isLoading: Boolean = false
) {
    val sessions = remember(trainings, period) { buildSessions(trainings, period) }
    // All completed trainings, for the summary of the last weeks whatever the period.
    val allSessions = remember(trainings) { buildSessions(trainings, null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (allSessions.isNotEmpty()) HighlightCard(allSessions)

        if (sessions.isEmpty()) {
            CardView {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ApexColors.main)
                    }
                } else {
                    EmptyText(stringResource(R.string.no_completed_trainings_in_period))
                }
            }
        } else {
            SummaryCard(sessions)
            MaxWeightCard(sessions)
            val volumePoints = sessions.filter { it.volume > 0 }.map { ChartPoint(it.date, it.volume) }
            ChartCard(stringResource(R.string.volume_per_training), volumePoints, caption = firstLastCaption(volumePoints, "kg")) {
                BarChart(sessions.map { ChartPoint(it.date, it.volume) }, unit = ChronoUnit.DAYS)
            }
            val repPoints = sessions.map { ChartPoint(it.date, it.reps.toDouble()) }
            ChartCard(stringResource(R.string.total_reps_per_training), repPoints) {
                LineChart(repPoints)
            }
            val averagePoints = sessions
                .filter { it.repSets > 0 }
                .map { ChartPoint(it.date, it.reps.toDouble() / it.repSets) }
            ChartCard(stringResource(R.string.average_reps_per_set), averagePoints) {
                LineChart(averagePoints)
            }
            // No change badge: the first and last week say little about a trend.
            ChartCard(stringResource(R.string.trainings_per_week), points = emptyList()) {
                BarChart(trainingsPerWeek(sessions), unit = ChronoUnit.WEEKS)
            }
            MuscleGroupsCard(sessions, workouts)
        }
    }
}

// MARK: - Data

private class ChartPoint(val date: Instant, val value: Double)

private class Session(
    val date: Instant,
    val training: Training,
    // Sum of repetitions × weight.
    val volume: Double,
    val reps: Int,
    val sets: Int,
    // Sets with a number of repetitions, for the average.
    val repSets: Int
)

// Completed trainings in the period (all of them without one).
private fun buildSessions(trainings: List<Training>, period: ProgressPeriod?): List<Session> =
    trainings
        .filter { it.isCompleted && (period == null || period.includes(it.date)) }
        .sortedBy { it.date }
        .map { training ->
            val sets = training.exercises.flatMap { it.sets }
            val reps = sets.mapNotNull { parseReps(it.reps) }
            val volume = sets.sumOf { set ->
                val setReps = parseReps(set.reps)
                val weight = set.weight
                if (setReps != null && weight != null) setReps * weight else 0.0
            }
            Session(
                date = training.date,
                training = training,
                volume = volume,
                reps = reps.sum(),
                sets = sets.count { !it.isDropSet },
                repSets = reps.size
            )
        }

// First number in the repetitions text, e.g. 8 for "8-10".
private fun parseReps(value: String?): Int? =
    value?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()

// MARK: - Highlight

// Accent card at the top: volume of the last 30 days against the 30 days
// before ("+12% volume"), or the trainings this month when there is nothing
// to compare, and the weeks in a row with a training. Only computed numbers.
@Composable
private fun HighlightCard(sessions: List<Session>) {
    val now = Instant.now()
    val day = 86_400L
    val last30 = sessions.filter { it.date.isAfter(now.minusSeconds(30 * day)) && !it.date.isAfter(now) }
    val previous30 = sessions.filter { it.date.isAfter(now.minusSeconds(60 * day)) && !it.date.isAfter(now.minusSeconds(30 * day)) }
    val lastVolume = last30.sumOf { it.volume }
    val previousVolume = previous30.sumOf { it.volume }

    val title: String
    val value: String
    if (previousVolume > 0 && last30.isNotEmpty()) {
        val change = Math.round((lastVolume - previousVolume) / previousVolume * 100).toInt()
        title = stringResource(R.string.last_30_days)
        value = stringResource(R.string.volume_change, (if (change > 0) "+" else "") + "$change%")
    } else {
        val thisMonth = java.time.YearMonth.now(DateFormats.zone)
        val count = sessions.count { java.time.YearMonth.from(it.date.atZone(DateFormats.zone)) == thisMonth }
        title = stringResource(R.string.this_month)
        value = pluralStringResource(R.plurals.trainings_count, count, count)
    }

    val streak = weeksInARow(sessions)
    val line = when {
        streak >= 2 -> pluralStringResource(R.plurals.weeks_in_a_row, streak, streak)
        last30.isNotEmpty() -> pluralStringResource(R.plurals.trainings_last_30_days, last30.size, last30.size)
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("progress-highlight")
            .clip(RoundedCornerShape(14.dp))
            .background(ApexColors.accent)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = ApexText.subheadline.copy(fontWeight = FontWeight.Medium), color = ApexColors.onAccent.copy(alpha = 0.8f))
        Text(value, style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp), color = ApexColors.onAccent)
        if (line != null) {
            Text(line, style = ApexText.subheadline, color = ApexColors.onAccent)
        }
    }
}

// Weeks in a row with at least one training, up to this week (or the last
// one, when there is no training yet this week).
private fun weeksInARow(sessions: List<Session>): Int {
    val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(firstDay))
    val weeks = sessions.map { weekStart(DateFormats.localDate(it.date)) }.toSet()
    var week = weekStart(LocalDate.now(DateFormats.zone))
    if (week !in weeks) week = week.minusWeeks(1)
    var count = 0
    while (week in weeks) {
        count += 1
        week = week.minusWeeks(1)
    }
    return count
}

// Change from the first to the last value, e.g. "↑ 29%" in green or "↓ 8%"
// in orange; nothing with fewer than two values or a first value of zero.
@Composable
private fun ChangeBadge(points: List<ChartPoint>) {
    if (points.size < 2) return
    val first = points.first().value
    val last = points.last().value
    if (first <= 0) return
    val change = Math.round((last - first) / first * 100).toInt()
    val (arrow, color) = when {
        change > 0 -> "↑" to ApexColors.green
        change < 0 -> "↓" to ApexColors.orange
        else -> "→" to ApexColors.secondaryLabel
    }
    Text(
        "$arrow ${kotlin.math.abs(change)}%",
        style = ApexText.subheadline.copy(fontWeight = FontWeight.Bold),
        color = color
    )
}

// "Last 6,700 kg · first 5,200 kg", for charts with at least two values.
@Composable
private fun firstLastCaption(points: List<ChartPoint>, unit: String): String? {
    if (points.size < 2) return null
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    return stringResource(
        R.string.last_and_first_value,
        "${format.format(points.last().value)} $unit",
        "${format.format(points.first().value)} $unit"
    )
}

// MARK: - Summary

@Composable
private fun SummaryCard(sessions: List<Session>) {
    val volume = sessions.sumOf { it.volume }
    val sets = sessions.sumOf { it.sets }
    val volumeText = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(volume)

    CardView(title = stringResource(R.string.progress_summary)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryValue("${sessions.size}", stringResource(R.string.trainings), Modifier.weight(1f))
            SummaryValue("$sets", stringResource(R.string.sets), Modifier.weight(1f))
            SummaryValue("$volumeText kg", stringResource(R.string.volume), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryValue(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            value,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ApexLabel(label)
    }
}

// MARK: - Max weight

private class ExerciseOption(val id: String, val name: String, val count: Int)

// Exercises with a weight, most frequent first.
private fun exerciseOptions(sessions: List<Session>): List<ExerciseOption> {
    val names = mutableMapOf<String, String>()
    val counts = mutableMapOf<String, Int>()
    sessions.flatMap { it.training.exercises }
        .filter { exercise -> exercise.sets.any { it.weight != null } }
        .forEach { exercise ->
            names[exercise.workoutId] = exercise.workoutName.localized
            counts[exercise.workoutId] = (counts[exercise.workoutId] ?: 0) + 1
        }
    return counts
        .map { (id, count) -> ExerciseOption(id, names[id].orEmpty(), count) }
        .sortedWith(compareByDescending<ExerciseOption> { it.count }.thenBy { it.name })
}

@Composable
private fun MaxWeightCard(sessions: List<Session>) {
    // Exercise for the chart, null picks the most frequent one.
    var selectedWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val options = exerciseOptions(sessions)
    val selected = options.firstOrNull { it.id == selectedWorkoutId } ?: options.firstOrNull()

    // Heaviest set of the exercise on every training it was done.
    val points = if (selected == null) emptyList() else sessions.mapNotNull { session ->
        session.training.exercises
            .filter { it.workoutId == selected.id }
            .flatMap { it.sets }
            .mapNotNull { it.weight }
            .maxOrNull()
            ?.let { ChartPoint(session.date, it) }
    }

    CardView {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.max_weight))
            Spacer(Modifier.width(8.dp))
            ChangeBadge(points)
            Spacer(Modifier.weight(1f))
            if (selected != null) {
                Box(modifier = Modifier.widthIn(max = 200.dp)) {
                    Row(
                        modifier = Modifier.clickable { showMenu = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            selected.name,
                            style = ApexText.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = ApexColors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = ApexColors.accent, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = ApexColors.card) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                trailingIcon = {
                                    if (option.id == selected.id) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = ApexColors.main)
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    selectedWorkoutId = option.id
                                }
                            )
                        }
                    }
                }
            }
        }

        if (points.isEmpty()) {
            Text(stringResource(R.string.no_weights_logged), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
        } else {
            BarChart(points, unit = ChronoUnit.DAYS, height = 150.dp)
            firstLastCaption(points, "kg")?.let { ChartCaption(it) }
        }
    }
}

// MARK: - Trainings per week

// Trainings per week, including weeks without trainings.
private fun trainingsPerWeek(sessions: List<Session>): List<ChartPoint> {
    val first = sessions.firstOrNull()?.date ?: return emptyList()
    val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    fun weekStart(date: Instant): LocalDate =
        DateFormats.localDate(date).with(TemporalAdjusters.previousOrSame(firstDay))

    val counts = sessions.groupingBy { weekStart(it.date) }.eachCount()

    val result = mutableListOf<ChartPoint>()
    var week = weekStart(first)
    val end = LocalDate.now(DateFormats.zone)
    while (!week.isAfter(end) && result.size < 60) {
        result.add(ChartPoint(week.atStartOfDay(DateFormats.zone).toInstant(), (counts[week] ?: 0).toDouble()))
        week = week.plusWeeks(1)
    }
    return result
}

// MARK: - Muscle groups

private class MuscleGroup(val id: String, val name: String, val sets: Int)

// Logged sets per primary muscle of the exercise's workout. Exercises whose
// workout has no muscles count as other.
private fun muscleGroups(
    sessions: List<Session>,
    workouts: List<Workout>,
    other: String,
    muscleName: (String) -> String
): List<MuscleGroup> {
    val workoutsById = workouts.distinctBy { it.id }.associateBy { it.id }
    val otherId = "__other"

    val sets = linkedMapOf<String, Int>()
    sessions.flatMap { it.training.exercises }
        .filter { it.sets.isNotEmpty() }
        .forEach { exercise ->
            val muscles = workoutsById[exercise.workoutId]?.muscles.orEmpty().distinct()
            for (muscle in muscles.ifEmpty { listOf(otherId) }) {
                sets[muscle] = (sets[muscle] ?: 0) + exercise.sets.sortedBy { it.order }.mainSetCount
            }
        }

    val sorted = sets
        .map { (code, count) -> MuscleGroup(code, if (code == otherId) other else muscleName(code), count) }
        .sortedWith(compareByDescending<MuscleGroup> { it.sets }.thenBy { it.name })

    // At most six slices: five muscles, the rest is grouped as other.
    if (sorted.size <= 6) return sorted
    val muscles = sorted.filter { it.id != otherId }
    val rest = muscles.drop(5).sumOf { it.sets } + (sorted.firstOrNull { it.id == otherId }?.sets ?: 0)
    return muscles.take(5) + MuscleGroup(otherId, other, rest)
}

// Accent, teal, indigo, orange, pink and gray, as on iOS.
private fun sliceColors(): List<Color> = listOf(
    ApexColors.accent,
    if (ApexColors.isDark) Color(0xFF40C8E0) else Color(0xFF30B0C7),
    if (ApexColors.isDark) Color(0xFF5E5CE6) else Color(0xFF5856D6),
    ApexColors.orange,
    if (ApexColors.isDark) Color(0xFFFF375F) else Color(0xFFFF2D55),
    ApexColors.gray
)

@Composable
private fun MuscleGroupsCard(sessions: List<Session>, workouts: List<Workout>) {
    val context = LocalContext.current
    val groups = muscleGroups(sessions, workouts, stringResource(R.string.other)) {
        WorkoutMuscle.name(context, it)
    }
    val total = max(groups.sumOf { it.sets }, 1)
    val colors = sliceColors()
    val percent = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 0 }

    // Tagged for the store screenshots once the muscles of the workouts are known.
    CardView(
        modifier = Modifier.testTag(if (workouts.isEmpty()) "muscle-groups-card" else "muscle-groups-chart"),
        title = stringResource(R.string.muscle_group_distribution)
    ) {
        if (groups.isEmpty()) {
            Text(stringResource(R.string.no_sets_logged), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(groups.map { it.sets }, colors, modifier = Modifier.size(120.dp))

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        groups.forEachIndexed { index, group ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(colors[index % colors.size], CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    group.name,
                                    style = ApexText.footnote,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    percent.format(group.sets.toDouble() / total),
                                    style = ApexText.footnote,
                                    color = ApexColors.secondaryLabel
                                )
                            }
                        }
                    }
                }

                Text(stringResource(R.string.share_of_logged_sets), style = ApexText.footnote, color = ApexColors.secondaryLabel)
            }
        }
    }
}

// Ring of slices with a hole in the middle (60% of the radius).
@Composable
private fun DonutChart(values: List<Int>, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = values.sum().toFloat()
    Canvas(modifier = modifier) {
        if (total <= 0f) return@Canvas
        val radius = size.minDimension / 2
        val thickness = radius * 0.4f
        val arcRadius = radius - thickness / 2
        val topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
        val arcSize = Size(arcRadius * 2, arcRadius * 2)
        // Small gap between slices, like angularInset on iOS.
        val gap = if (values.size > 1) Math.toDegrees((1.5.dp.toPx() * 2 / arcRadius).toDouble()).toFloat() else 0f

        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = value / total * 360f
            drawArc(
                color = colors[index % colors.size],
                startAngle = start + gap / 2,
                sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = thickness)
            )
            start += sweep
        }
    }
}

// MARK: - Chart helpers

// Chart with its title, the change over the period on the right of the
// title and an optional caption under it.
@Composable
private fun ChartCard(title: String, points: List<ChartPoint>, caption: String? = null, content: @Composable () -> Unit) {
    CardView {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(title, modifier = Modifier.weight(1f))
            ChangeBadge(points)
        }
        content()
        caption?.let { ChartCaption(it) }
    }
}

@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = ApexColors.label, modifier = modifier)
}

@Composable
private fun ChartCaption(text: String) {
    Text(text, style = ApexText.footnote, color = ApexColors.secondaryLabel)
}

private val axisDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")

// Rounded top of the y axis and the values of its grid lines, from 0.
private fun axisTicks(maxValue: Double, desiredCount: Int = 3): List<Double> {
    if (maxValue <= 0) return listOf(0.0, 1.0)
    val rough = maxValue / desiredCount
    val magnitude = 10.0.pow(floor(log10(rough)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rough }
    val count = ceil(maxValue / step - 1e-9).toInt().coerceAtLeast(1)
    return (0..count).map { it * step }
}

private fun axisLabel(value: Double): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)

// Draws the y axis grid with labels on the left and returns the plot's left edge.
private fun DrawScope.drawValueAxis(
    textMeasurer: TextMeasurer,
    ticks: List<Double>,
    chartTop: Float,
    chartBottom: Float
): Float {
    val style = ApexText.caption2.copy(color = ApexColors.secondaryLabel)
    val labels = ticks.map { textMeasurer.measure(axisLabel(it), style) }
    val labelWidth = (labels.maxOfOrNull { it.size.width } ?: 0).toFloat()
    val left = labelWidth + 6.dp.toPx()
    val top = ticks.last()

    ticks.forEachIndexed { index, tick ->
        val y = chartBottom - (tick / top * (chartBottom - chartTop)).toFloat()
        drawLine(ApexColors.border, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        val label = labels[index]
        drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))
    }
    return left
}

// Up to four date labels along the bottom.
private fun DrawScope.drawDateAxis(
    textMeasurer: TextMeasurer,
    dates: List<Instant>,
    x: (Instant) -> Float,
    chartLeft: Float,
    chartBottom: Float
) {
    if (dates.isEmpty()) return
    val style = ApexText.caption2.copy(color = ApexColors.secondaryLabel)
    val indexes = if (dates.size <= 4) dates.indices.toList()
    else (0 until 4).map { it * (dates.size - 1) / 3 }.distinct()

    indexes.forEach { index ->
        val label = textMeasurer.measure(axisDateFormat.format(dates[index].atZone(DateFormats.zone)), style)
        val lx = (x(dates[index]) - label.size.width / 2f)
            .coerceIn(chartLeft, (size.width - label.size.width).coerceAtLeast(chartLeft))
        drawText(label, topLeft = Offset(lx, chartBottom + 4.dp.toPx()))
    }
}

// Bars on a time axis, each one as wide as its day or week.
@Composable
private fun BarChart(points: List<ChartPoint>, unit: ChronoUnit, height: Dp = 140.dp) {
    val textMeasurer = rememberTextMeasurer()
    val color = ApexColors.accent

    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
        if (points.isEmpty()) return@Canvas
        val chartTop = 6.dp.toPx()
        val chartBottom = size.height - 20.dp.toPx()
        val ticks = axisTicks(points.maxOf { it.value })
        val chartLeft = drawValueAxis(textMeasurer, ticks, chartTop, chartBottom)
        val chartRight = size.width
        val top = ticks.last()

        // Bars start at the beginning of their day and span the unit.
        val unitSeconds = if (unit == ChronoUnit.WEEKS) 7 * 86_400L else 86_400L
        fun start(date: Instant): Instant =
            if (unit == ChronoUnit.WEEKS) date
            else DateFormats.localDate(date).atStartOfDay(DateFormats.zone).toInstant()

        val starts = points.map { start(it.date) }
        val minX = starts.min().epochSecond.toDouble()
        val maxX = starts.max().epochSecond.toDouble() + unitSeconds
        fun x(date: Instant): Float =
            chartLeft + ((date.epochSecond - minX) / (maxX - minX) * (chartRight - chartLeft)).toFloat()

        val unitWidth = (unitSeconds / (maxX - minX) * (chartRight - chartLeft)).toFloat()
        val barWidth = (unitWidth * 0.8f).coerceIn(3.dp.toPx(), 28.dp.toPx())

        points.forEachIndexed { index, point ->
            val center = x(starts[index]) + unitWidth / 2
            val barTop = chartBottom - (point.value / top * (chartBottom - chartTop)).toFloat()
            if (chartBottom - barTop > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(center - barWidth / 2, barTop),
                    size = Size(barWidth, chartBottom - barTop),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }

        drawDateAxis(textMeasurer, starts, { x(it) + unitWidth / 2 }, chartLeft, chartBottom)
    }
}

// Smooth line through the points (Catmull-Rom, as on iOS) with dots.
@Composable
private fun LineChart(points: List<ChartPoint>, height: Dp = 140.dp) {
    val textMeasurer = rememberTextMeasurer()
    val color = ApexColors.accent

    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
        if (points.isEmpty()) return@Canvas
        val chartTop = 6.dp.toPx()
        val chartBottom = size.height - 20.dp.toPx()
        val ticks = axisTicks(points.maxOf { it.value })
        val chartLeft = drawValueAxis(textMeasurer, ticks, chartTop, chartBottom) + 6.dp.toPx()
        val chartRight = size.width - 6.dp.toPx()
        val top = ticks.last()

        val minX = points.first().date.epochSecond.toDouble()
        val maxX = points.last().date.epochSecond.toDouble()
        fun x(date: Instant): Float =
            if (maxX == minX) (chartLeft + chartRight) / 2
            else chartLeft + ((date.epochSecond - minX) / (maxX - minX) * (chartRight - chartLeft)).toFloat()

        fun y(value: Double): Float = chartBottom - (value / top * (chartBottom - chartTop)).toFloat()

        val offsets = points.map { Offset(x(it.date), y(it.value)) }
        if (offsets.size > 1) {
            val path = Path().apply {
                moveTo(offsets[0].x, offsets[0].y)
                for (i in 0 until offsets.size - 1) {
                    val p0 = offsets[max(i - 1, 0)]
                    val p1 = offsets[i]
                    val p2 = offsets[i + 1]
                    val p3 = offsets[minOf(i + 2, offsets.size - 1)]
                    cubicTo(
                        p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                        p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                        p2.x, p2.y
                    )
                }
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
        offsets.forEach { drawCircle(color, radius = 2.5.dp.toPx(), center = it) }

        drawDateAxis(textMeasurer, points.map { it.date }, { x(it) }, chartLeft, chartBottom)
    }
}
