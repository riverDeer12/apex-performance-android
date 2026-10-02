package software.rdd.apexperformance.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.model.Workout
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.WorkoutThumbnail
import software.rdd.apexperformance.ui.components.YouTubePlayer
import software.rdd.apexperformance.ui.components.YouTubeVideo
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

class WorkoutDetailScreen(
    workout: Workout,
    private val canEdit: Boolean,
    private val onSaved: (Workout) -> Unit
) : Screen() {

    private var workout by mutableStateOf(workout)
    private var isPlayingVideo by mutableStateOf(false)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen(
            topBar = {
                TopBar(title = workout.name.localized) {
                    if (canEdit) {
                        TextButton(onClick = {
                            navigator.push(WorkoutEditScreen(workout) { updated ->
                                workout = updated
                                isPlayingVideo = false
                                onSaved(updated)
                            })
                        }) {
                            Text(stringResource(R.string.edit), color = ApexColors.main, style = ApexText.body)
                        }
                    }
                }
            }
        ) {
            VideoArea()

            Text(
                workout.name.localized,
                style = ApexText.title2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.workout_types)) {
                if (workout.workoutTypes.isEmpty()) {
                    Text("—", color = ApexColors.secondaryLabel)
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        workout.workoutTypes.forEach { type ->
                            Text(
                                type.name.localized,
                                style = ApexText.subheadline.copy(fontWeight = FontWeight.Medium),
                                color = ApexColors.main,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(ApexColors.main.copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.description)) {
                Text(workout.description.localized.ifEmpty { "—" }, style = ApexText.body, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // Shows the thumbnail until tapped, then plays the video in place.
    @Composable
    private fun VideoArea() {
        val videoId = YouTubeVideo.id(workout.videoUrl)

        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .then(if (videoId != null && !isPlayingVideo) Modifier.clickable { isPlayingVideo = true } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (isPlayingVideo && videoId != null) {
                YouTubePlayer(videoId, modifier = Modifier.fillMaxSize())
            } else {
                WorkoutThumbnail(workout.thumbnailUrl, modifier = Modifier.fillMaxSize())
                if (videoId != null) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.play_video),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }
    }
}
