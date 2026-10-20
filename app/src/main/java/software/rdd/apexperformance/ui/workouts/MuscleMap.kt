package software.rdd.apexperformance.ui.workouts

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import software.rdd.apexperformance.ui.theme.ApexColors

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

private val silhouettePaths: List<Path> by lazy { BodyMapPaths.silhouette.map(::parsePath) }
private val musclePaths: List<Pair<String, List<Path>>> by lazy {
    BodyMapPaths.muscles.map { (code, paths) -> code to paths.map(::parsePath) }
}

private fun parsePath(data: String): Path = PathParser().parsePathString(data).toPath()

// Front and back of a body with the given muscles highlighted in the
// app's main color, like MuscleMapView on iOS.
@Composable
fun MuscleMap(muscles: Collection<String>, modifier: Modifier = Modifier) {
    val worked = ApexColors.main
    val base = ApexColors.label.copy(alpha = 0.08f)
    val idle = ApexColors.label.copy(alpha = 0.14f)

    Canvas(modifier = modifier.aspectRatio(BodyMapPaths.WIDTH / BodyMapPaths.HEIGHT)) {
        val factor = minOf(size.width / BodyMapPaths.WIDTH, size.height / BodyMapPaths.HEIGHT)
        val offsetX = (size.width - BodyMapPaths.WIDTH * factor) / 2
        translate(left = offsetX) {
            scale(factor, factor, pivot = Offset.Zero) {
                silhouettePaths.forEach { drawPath(it, base) }
                musclePaths.forEach { (code, paths) ->
                    val color = if (code in muscles) worked else idle
                    paths.forEach { drawPath(it, color) }
                }
            }
        }
    }
}
