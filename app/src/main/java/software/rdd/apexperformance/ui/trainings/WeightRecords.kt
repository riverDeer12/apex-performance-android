package software.rdd.apexperformance.ui.trainings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.model.ExerciseRecords
import software.rdd.apexperformance.model.PersonalRecord
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.model.TrainingHistory
import software.rdd.apexperformance.model.WeightRecord
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.EmptyStateView
import software.rdd.apexperformance.ui.components.IconCircle
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ShowMoreItems
import software.rdd.apexperformance.ui.components.SmallProgress
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// "Osobni rekordi" in training progress: the best weight of every exercise
// (heaviest set that isn't a drop set, with its repetitions) and when it was
// lifted, the most recent record first. Calculated from the trainings shown.
// Tapping one opens its record history when the API has it.
@Composable
fun WeightRecordsSection(trainings: List<Training>, clientId: String?) {
    val records = remember(trainings) { TrainingHistory.bestWeights(trainings) }
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var openingId by remember { mutableStateOf<String?>(null) }
    // Record history from the API, loaded on the first tap.
    var apiRecords by remember { mutableStateOf<List<ExerciseRecords>?>(null) }

    CardView(modifier = Modifier.testTag("weight-records"), title = stringResource(R.string.personal_records)) {
        if (records.isEmpty()) {
            EmptyStateView(Icons.Outlined.EmojiEvents, stringResource(R.string.no_weight_records))
        } else {
            Column {
                ShowMoreItems(records) { record ->
                    WeightRecordRow(record, isOpening = openingId == record.workoutId) {
                        if (openingId != null) return@WeightRecordRow
                        openingId = record.workoutId
                        scope.launch {
                            try {
                                val groups = apiRecords ?: ExerciseRecords.group(PersonalRecord.load(clientId)).also { apiRecords = it }
                                groups.firstOrNull { it.workoutId == record.workoutId }?.let {
                                    navigator.push(PersonalRecordHistoryScreen(it))
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                // Without the history the record is still shown here.
                            } finally {
                                openingId = null
                            }
                        }
                    }
                    if (record.workoutId != records.last().workoutId) RowDivider()
                }
            }
        }
    }
}

@Composable
private fun WeightRecordRow(record: WeightRecord, isOpening: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconCircle(Icons.Outlined.EmojiEvents, ApexColors.orange)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(record.workoutName.localized, style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold))
            Text(DateFormats.date(record.date), style = ApexText.footnote, color = ApexColors.secondaryLabel)
        }
        Text(recordValue(record), style = ApexText.headline, color = ApexColors.label)
        if (isOpening) SmallProgress(size = 14.dp) else Chevron()
    }
}

// "72,5 kg × 6", or only the weight without repetitions.
@Composable
private fun recordValue(record: WeightRecord): String {
    val weight = "${formatWeight(record.weight)} ${stringResource(R.string.kg)}"
    return record.reps?.let { "$weight × $it" } ?: weight
}

// "🏆 Novi rekord" next to an exercise (and its set) in the training details.
@Composable
fun NewRecordBadge(modifier: Modifier = Modifier) {
    Text(
        "🏆 " + stringResource(R.string.new_record),
        style = ApexText.caption.copy(fontWeight = FontWeight.Bold),
        color = ApexColors.orange,
        modifier = modifier
            .testTag("new-record-badge")
            .clip(RoundedCornerShape(50))
            .background(ApexColors.orange.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

// Shown to the client once, the first time a training with a new record is opened.
@Composable
fun RecordCelebrationBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .testTag("record-celebration")
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ApexColors.orange.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("🏆", style = ApexText.title3)
        Text(text, style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = ApexColors.label, modifier = Modifier.weight(1f))
    }
}

// Trainings whose records were already celebrated, on this phone.
object RecordCelebrations {
    private const val PREFERENCES = "record_celebrations"
    private const val KEY = "celebrated_training_ids"

    private val preferences get() = ApexApp.appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun isCelebrated(trainingId: String): Boolean =
        preferences.getStringSet(KEY, emptySet()).orEmpty().contains(trainingId)

    fun markCelebrated(trainingId: String) {
        val ids = preferences.getStringSet(KEY, emptySet()).orEmpty() + trainingId
        preferences.edit().putStringSet(KEY, ids).apply()
    }
}
