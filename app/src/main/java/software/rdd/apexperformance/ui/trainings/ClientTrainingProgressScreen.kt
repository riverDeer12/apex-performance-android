package software.rdd.apexperformance.ui.trainings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.model.Workout
import software.rdd.apexperformance.ui.components.ApexLabel
import software.rdd.apexperformance.ui.components.ApexTitle
import software.rdd.apexperformance.ui.components.ProgressPeriod
import software.rdd.apexperformance.ui.components.ProgressPeriodMenu
import software.rdd.apexperformance.ui.components.ScrollScreen

// Training progress of one client, opened by staff from client details
// and by clients from their progress.
class ClientTrainingProgressScreen(
    private val clientName: String,
    // Client's trainings, only completed ones are used.
    private val trainings: List<Training>
) : Screen() {

    private var period by mutableStateOf(ProgressPeriod.SIX_MONTHS)
    private var workouts by mutableStateOf<List<Workout>>(emptyList())

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) {
            // Only used for muscle groups, so the charts work without it.
            if (workouts.isEmpty()) {
                runCatching { ApiClient.get<List<Workout>>("workouts") }.getOrNull()?.let { workouts = it }
            }
        }

        ScrollScreen {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (clientName.isNotBlank()) ApexLabel(clientName)
                        ApexTitle(stringResource(R.string.training_progress_title))
                    }

                    ProgressPeriodMenu(period) { period = it }
                }

                TrainingProgress(trainings = trainings, workouts = workouts, period = period)
            }
        }
    }
}
