package software.rdd.apexperformance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.formatNumber
import software.rdd.apexperformance.model.BodyMeasurement
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.max

// Client's weight over time, with latest weight and change
// since the first measurement. Same as the chart on the web.
@Composable
fun WeightProgressChart(measurements: List<BodyMeasurement>, modifier: Modifier = Modifier) {
    val points = measurements
        .filter { it.weight > 0 }
        .sortedBy { it.measuredAt }
        .map { it.measuredAt to it.weight }

    CardView(modifier = modifier, title = stringResource(R.string.weight_progress)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val latest = points.lastOrNull()
            if (latest != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat(
                        label = stringResource(R.string.latest_weight),
                        value = "${formatNumber(latest.second)} kg",
                        hint = DateFormats.date(latest.first)
                    )

                    if (points.size > 1) {
                        val change = latest.second - points.first().second
                        Stat(
                            label = stringResource(R.string.weight_change),
                            value = "${if (change > 0) "+" else ""}${formatNumber(change)} kg",
                            hint = stringResource(R.string.since_first_measurement)
                        )
                    }

                    Stat(label = stringResource(R.string.measurements_count), value = "${points.size}", hint = null)
                }
            }

            if (points.size > 1) {
                Chart(points)
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
private fun Stat(label: String, value: String, hint: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = ApexText.caption, color = ApexColors.secondaryLabel)
        Text(value, style = ApexText.title3)
        if (hint != null) {
            Text(hint, style = ApexText.caption2, color = ApexColors.secondaryLabel)
        }
    }
}

@Composable
private fun Chart(points: List<Pair<Instant, Double>>) {
    val textMeasurer = rememberTextMeasurer()
    val description = stringResource(R.string.weight_progress)
    val axisDateFormat = DateTimeFormatter.ofPattern("dd.MM.")

    val weights = points.map { it.second }
    // Some room above and below so the line isn't glued to the edges.
    val padding = max(1.0, (weights.max() - weights.min()) * 0.1)
    val minY = weights.min() - padding
    val maxY = weights.max() + padding
    val minX = points.first().first.epochSecond.toDouble()
    val maxX = points.last().first.epochSecond.toDouble()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = description }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp).padding(bottom = 4.dp)) {
            val labelWidth = 52.dp.toPx()
            val bottomLabels = 20.dp.toPx()
            val chartLeft = labelWidth
            val chartRight = size.width - 8.dp.toPx()
            val chartTop = 8.dp.toPx()
            val chartBottom = size.height - bottomLabels

            fun x(epoch: Double) =
                if (maxX == minX) (chartLeft + chartRight) / 2
                else chartLeft + ((epoch - minX) / (maxX - minX) * (chartRight - chartLeft)).toFloat()

            fun y(weight: Double) =
                chartBottom - ((weight - minY) / (maxY - minY) * (chartBottom - chartTop)).toFloat()

            // Horizontal grid lines with weight labels.
            val gridCount = 4
            for (i in 0..gridCount) {
                val weight = minY + (maxY - minY) * i / gridCount
                val gy = y(weight)
                drawLine(ApexColors.separator, Offset(chartLeft, gy), Offset(chartRight, gy), strokeWidth = 1f)
                drawLabel(textMeasurer, "${formatNumber(weight)} kg", Offset(0f, gy - 7.dp.toPx()))
            }

            // Up to four date labels along the bottom.
            val labelIndexes = if (points.size <= 4) points.indices.toList()
            else (0 until 4).map { it * (points.size - 1) / 3 }.distinct()
            labelIndexes.forEach { index ->
                val date = points[index].first.atZone(DateFormats.zone)
                val label = axisDateFormat.format(date)
                val lx = x(points[index].first.epochSecond.toDouble()) - 14.dp.toPx()
                drawLabel(textMeasurer, label, Offset(lx.coerceIn(chartLeft - 8.dp.toPx(), chartRight - 30.dp.toPx()), chartBottom + 4.dp.toPx()))
            }

            // Smooth line through the points (Catmull-Rom, as on iOS).
            val offsets = points.map { Offset(x(it.first.epochSecond.toDouble()), y(it.second)) }
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
            drawPath(path, ApexColors.main, style = Stroke(width = 2.dp.toPx()))
            offsets.forEach { drawCircle(ApexColors.main, radius = 4.dp.toPx(), center = it) }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    textMeasurer: TextMeasurer,
    text: String,
    topLeft: Offset
) {
    drawText(
        textMeasurer = textMeasurer,
        text = text,
        topLeft = topLeft,
        style = ApexText.caption2.copy(color = ApexColors.secondaryLabel)
    )
}
