package software.rdd.apexperformance.ui.trainings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.model.Workout
import software.rdd.apexperformance.ui.components.ApexLabel
import software.rdd.apexperformance.ui.components.ApexPictureBackground
import software.rdd.apexperformance.ui.components.ApexScreenHeader
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Client's tab with the trainings their coach marked as completed.
class CompletedTrainingsScreen : Screen() {

    private var trainings by mutableStateOf<List<Training>>(emptyList())
    // Used for the exercise pictures.
    private var workouts by mutableStateOf<Map<String, Workout>>(emptyMap())
    private var searchText by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var hasLoaded = false

    private val filteredTrainings: List<Training>
        get() {
            val query = searchText.trim()
            if (query.isEmpty()) return trainings
            return trainings.filter { training ->
                training.name.contains(query, ignoreCase = true) ||
                    training.exercises.any { exercise ->
                        exercise.workoutName.allValues.any { it.contains(query, ignoreCase = true) }
                    }
            }
        }

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
            showLoading = isLoading && trainings.isEmpty(),
            onRefresh = { load() }
        ) {
            ApexScreenHeader(stringResource(R.string.my_trainings))

            SearchField()

            val filtered = filteredTrainings
            if (filtered.isEmpty() && !isLoading) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                    EmptyText(stringResource(if (searchText.isEmpty()) R.string.no_client_trainings else R.string.no_trainings_found))
                }
            } else {
                // Planned trainings are for the coach, the API only returns completed ones.
                val completed = filtered.filter { it.isCompleted }
                if (completed.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ApexLabel(stringResource(R.string.completed_trainings))

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            completed.forEach { training ->
                                val imageUrl = imageUrl(training)
                                TrainingCard(training, imageUrl) {
                                    navigator.push(TrainingDetailScreen(training, heroImageUrl = imageUrl))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchField() {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(44.dp)
                .apexCard(cornerRadius = 10.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = ApexColors.secondaryLabel, modifier = Modifier.size(18.dp))
            PlainTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = stringResource(R.string.search_trainings),
                textStyle = ApexText.subheadline,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier.weight(1f)
            )
            if (searchText.isNotEmpty()) {
                Icon(
                    Icons.Filled.Cancel,
                    contentDescription = stringResource(R.string.clear),
                    tint = ApexColors.secondaryLabel,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { searchText = "" }
                )
            }
        }
    }

    // Picture of the training's first exercise, when the workout has one.
    private fun imageUrl(training: Training): String? =
        training.exercises
            .sortedBy { it.order }
            .firstNotNullOfOrNull { exercise -> workouts[exercise.workoutId]?.thumbnailUrl?.takeIf { it.isNotBlank() } }

    private suspend fun load() {
        isLoading = true
        try {
            trainings = Training.loadCompleted()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }

        // Only pictures, the list works without them.
        if (workouts.isEmpty()) {
            runCatching { ApiClient.get<List<Workout>>("workouts") }.getOrNull()?.let { loaded ->
                workouts = loaded.distinctBy { it.id }.associateBy { it.id }
            }
        }
    }
}

// Training card with a picture, date and its exercises.
@Composable
internal fun TrainingCard(training: Training, imageUrl: String? = null, onClick: () -> Unit) {
    val exerciseNames = training.exercises.sortedBy { it.order }.map { it.workoutName.localized }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .apexCard()
            .clickable(onClick = onClick)
            .padding(10.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ApexPictureBackground(
            imageUrl = imageUrl,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(width = 96.dp, height = 112.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    DateFormats.date(training.date),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                )

                if (!training.isCompleted) {
                    Text(
                        stringResource(R.string.planned).uppercase(),
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                        color = ApexColors.orange,
                        modifier = Modifier
                            .background(ApexColors.orange.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                training.name,
                style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
                exerciseNames.take(3).forEach { name ->
                    Text(
                        name.uppercase(),
                        style = style,
                        color = ApexColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (exerciseNames.size > 3) {
                    Text("+${exerciseNames.size - 3}", style = style, color = ApexColors.secondaryLabel)
                }
            }

            Spacer(Modifier.weight(1f))

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = ApexColors.accent,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
