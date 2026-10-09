package software.rdd.apexperformance.ui.bodymeasurements

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

// One point of a measurement chart.
class ChartPoint(val date: Instant, val value: Double)

// Line chart of a measurement over time, drawn like the Swift Charts on iOS:
// accent line with points, horizontal grid with value labels on the leading
// side and date labels along the bottom. Points are oldest first.
@Composable
fun MeasurementLineChart(
    points: List<ChartPoint>,
    domain: ClosedFloatingPointRange<Double>,
    xLabels: List<Pair<Instant, String>>,
    yLabel: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
    // Catmull-Rom curve instead of straight segments.
    smooth: Boolean = false,
    // Fades the accent color below the line.
    showsArea: Boolean = false,
    // Writes the latest value above its point.
    showsLastValue: Boolean = false,
    pointRadius: Dp = 2.dp,
    lastPointRadius: Dp = 3.dp
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = ApexText.caption2.copy(color = ApexColors.secondaryLabel)
    val valueStyle = ApexText.caption2.copy(color = ApexColors.label, fontWeight = FontWeight.SemiBold)
    val accent = ApexColors.accent
    val gridColor = ApexColors.border
    val ticks = niceTicks(domain.start, domain.endInclusive, 4)

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        if (points.isEmpty()) return@Canvas

        val yLabels = ticks.map { it to textMeasurer.measure(yLabel(it), labelStyle) }
        val labelWidth = (yLabels.maxOfOrNull { it.second.size.width } ?: 0) + 6.dp.toPx()
        val bottomLabels = 18.dp.toPx()
        // Room so the first and last points and the value label aren't cut off.
        val inset = max(lastPointRadius.toPx(), 4.dp.toPx())
        val chartLeft = labelWidth
        val chartRight = size.width
        val chartTop = if (showsLastValue) 18.dp.toPx() else 6.dp.toPx()
        val chartBottom = size.height - bottomLabels

        val minX = points.first().date.epochSecond.toDouble()
        val maxX = points.last().date.epochSecond.toDouble()
        val lower = domain.start
        val upper = domain.endInclusive

        fun x(date: Instant): Float {
            val epoch = date.epochSecond.toDouble()
            return if (maxX == minX) (chartLeft + chartRight) / 2
            else chartLeft + inset + ((epoch - minX) / (maxX - minX) * (chartRight - chartLeft - 2 * inset)).toFloat()
        }

        fun y(value: Double): Float =
            if (upper == lower) (chartTop + chartBottom) / 2
            else chartBottom - ((value - lower) / (upper - lower) * (chartBottom - chartTop)).toFloat()

        // Horizontal grid lines with value labels.
        yLabels.forEach { (value, layout) ->
            val gy = y(value)
            drawLine(gridColor, Offset(chartLeft, gy), Offset(chartRight, gy), strokeWidth = 1.dp.toPx())
            drawText(layout, topLeft = Offset(0f, gy - layout.size.height / 2f))
        }

        // Date labels centered under their date, kept inside the chart.
        xLabels.forEach { (date, text) ->
            val layout = textMeasurer.measure(text, labelStyle)
            val lx = (x(date) - layout.size.width / 2f)
                .coerceIn(chartLeft, max(chartLeft, size.width - layout.size.width))
            drawText(layout, topLeft = Offset(lx, chartBottom + 4.dp.toPx()))
        }

        val offsets = points.map { Offset(x(it.date), y(it.value)) }
        val line = linePath(offsets, smooth)

        if (showsArea && offsets.size > 1) {
            val area = linePath(offsets, smooth).apply {
                lineTo(offsets.last().x, y(lower))
                lineTo(offsets.first().x, y(lower))
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.25f), accent.copy(alpha = 0f)),
                    startY = chartTop,
                    endY = y(lower)
                )
            )
        }

        drawPath(line, accent, style = Stroke(width = 2.dp.toPx()))
        offsets.forEachIndexed { index, offset ->
            val isLast = index == offsets.lastIndex
            drawCircle(accent, radius = (if (isLast) lastPointRadius else pointRadius).toPx(), center = offset)
        }

        if (showsLastValue) {
            drawValueLabel(textMeasurer, yLabel(points.last().value), valueStyle, offsets.last(), chartLeft)
        }
    }
}

// Latest value above its point, ending at the point like
// .annotation(position: .top, alignment: .trailing).
private fun DrawScope.drawValueLabel(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    point: Offset,
    minX: Float
) {
    val layout = textMeasurer.measure(text, style)
    val left = (point.x - layout.size.width).coerceAtLeast(minX)
    val top = (point.y - layout.size.height - 4.dp.toPx()).coerceAtLeast(0f)
    drawText(layout, topLeft = Offset(left, top))
}

private fun linePath(offsets: List<Offset>, smooth: Boolean): Path = Path().apply {
    moveTo(offsets[0].x, offsets[0].y)
    for (i in 0 until offsets.size - 1) {
        val p1 = offsets[i]
        val p2 = offsets[i + 1]
        if (smooth) {
            val p0 = offsets[max(i - 1, 0)]
            val p3 = offsets[minOf(i + 2, offsets.size - 1)]
            cubicTo(
                p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                p2.x, p2.y
            )
        } else {
            lineTo(p2.x, p2.y)
        }
    }
}

// Round values (1, 2 or 5 times a power of ten apart) inside the range,
// about `count` of them, like the automatic axis marks of Swift Charts.
fun niceTicks(min: Double, max: Double, count: Int): List<Double> {
    if (max <= min || count <= 0) return listOf(min)
    val rough = (max - min) / count
    val magnitude = 10.0.pow(floor(log10(rough)))
    val residual = rough / magnitude
    val step = magnitude * when {
        residual <= 1 -> 1.0
        residual <= 2 -> 2.0
        residual <= 5 -> 5.0
        else -> 10.0
    }
    val ticks = mutableListOf<Double>()
    var value = ceil(min / step) * step
    while (value <= max + step * 1e-9) {
        // Avoids "-0" and float noise like 80.00000001.
        ticks.add(if (abs(value) < step * 1e-9) 0.0 else Math.round(value / step) * step)
        value += step
    }
    return ticks
}
