package software.rdd.apexperformance.ui.workouts

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Muscles a workout works, as the API returns their codes.
object WorkoutMuscle {
    // Codes in the order they are listed.
    val all: List<String> = BodyMapPaths.muscles.map { it.first }

    // Localized name from the "muscle_<code>" string resource.
    @SuppressLint("DiscouragedApi")
    fun name(context: Context, code: String): String {
        val id = context.resources.getIdentifier("muscle_$code", "string", context.packageName)
        return if (id != 0) context.getString(id) else code
    }
}

// How a workout works a muscle.
enum class MuscleRole {
    // Does most of the work.
    PRIMARY,
    // Helps.
    SECONDARY
}

private val silhouettePaths: List<Path> by lazy { BodyMapPaths.silhouette.map(::parsePath) }
private val outlinePath: Path by lazy { Path().apply { silhouettePaths.forEach { addPath(it) } } }
private val detailPaths: List<Path> by lazy { BodyMapPaths.details.map(::parsePath) }
private val musclePaths: List<Pair<String, List<Path>>> by lazy {
    BodyMapPaths.muscles.map { (code, paths) -> code to paths.map(::parsePath) }
}

private fun parsePath(data: String): Path = PathParser().parsePathString(data).toPath()

const val SECONDARY_MUSCLE_ALPHA = 0.4f

// Front and back of a body with the muscles a workout works highlighted:
// primary ones in the app's main color, secondary ones lighter. Like
// MuscleMapView on iOS.
@Composable
fun MuscleMap(primary: Collection<String>, secondary: Collection<String> = emptyList(), modifier: Modifier = Modifier) {
    val main = ApexColors.main
    val label = ApexColors.label

    Canvas(modifier = modifier.aspectRatio(BodyMapPaths.WIDTH / BodyMapPaths.HEIGHT)) {
        val factor = minOf(size.width / BodyMapPaths.WIDTH, size.height / BodyMapPaths.HEIGHT)
        val offsetX = (size.width - BodyMapPaths.WIDTH * factor) / 2
        val line = Stroke(width = 0.3f, join = StrokeJoin.Round)
        translate(left = offsetX) {
            scale(factor, factor, pivot = Offset.Zero) {
                drawPath(outlinePath, label.copy(alpha = 0.08f))

                // Muscles reach over the body's edge, the outline cuts them.
                clipPath(outlinePath) {
                    detailPaths.forEach {
                        drawPath(it, label.copy(alpha = 0.13f))
                        drawPath(it, label.copy(alpha = 0.25f), style = line)
                    }
                    musclePaths.forEach { (code, paths) ->
                        val color = when (code) {
                            in primary -> main
                            in secondary -> main.copy(alpha = SECONDARY_MUSCLE_ALPHA)
                            else -> label.copy(alpha = 0.17f)
                        }
                        paths.forEach {
                            drawPath(it, color)
                            drawPath(it, label.copy(alpha = 0.3f), style = line)
                        }
                    }
                }

                drawPath(outlinePath, label.copy(alpha = 0.55f), style = Stroke(width = 0.5f, join = StrokeJoin.Round))
            }
        }
    }
}

// Primary and secondary muscles by name, with the colors of the body map.
@Composable
fun MuscleLegend(primary: List<String>, secondary: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (primary.isNotEmpty()) {
            MuscleLegendRow(stringResource(R.string.primary_muscles), primary.map { WorkoutMuscle.name(context, it) }, ApexColors.main)
        }
        if (secondary.isNotEmpty()) {
            MuscleLegendRow(
                stringResource(R.string.secondary_muscles),
                secondary.map { WorkoutMuscle.name(context, it) },
                ApexColors.main.copy(alpha = SECONDARY_MUSCLE_ALPHA)
            )
        }
    }
}

@Composable
private fun MuscleLegendRow(title: String, names: List<String>, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(10.dp)
                .background(color, CircleShape)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.uppercase(), style = ApexText.caption.copy(fontWeight = FontWeight.SemiBold), color = ApexColors.secondaryLabel)
            Text(names.joinToString(" · "), style = ApexText.subheadline, color = ApexColors.label)
        }
    }
}
