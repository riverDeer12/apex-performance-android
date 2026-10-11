package software.rdd.apexperformance.ui.trainings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.model.ExerciseRecords
import software.rdd.apexperformance.model.PersonalRecord
import software.rdd.apexperformance.ui.components.ApexLabel
import software.rdd.apexperformance.ui.components.ApexScreenHeader
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.FormSection
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.text.NumberFormat

// Client's personal records per exercise (staff only), calculated by
// the API from completed trainings, same as the web page.
class PersonalRecordsScreen(
    private val clientId: String,
    private val clientName: String
) : Screen() {

    private var exercises by mutableStateOf<List<ExerciseRecords>>(emptyList())
    private var searchText by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var hasLoaded = false

    private val filteredExercises: List<ExerciseRecords>
        get() {
            val query = searchText.trim()
            if (query.isEmpty()) return exercises
            return exercises.filter { it.name.contains(query, ignoreCase = true) }
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
            topBar = { TopBar(title = clientName) },
            showLoading = isLoading && exercises.isEmpty(),
            onRefresh = { load() }
        ) {
            ApexScreenHeader(
                stringResource(R.string.personal_records),
                stringResource(R.string.personal_records_hint),
                showsWordmark = false
            )

            if (exercises.size > 5) {
                TrainingsSearchField(
                    text = searchText,
                    onTextChange = { searchText = it },
                    placeholder = stringResource(R.string.search_workouts),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            val list = filteredExercises
            if (list.isEmpty() && !isLoading) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                    EmptyText(stringResource(if (searchText.isEmpty()) R.string.no_personal_records else R.string.no_workouts_found))
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                list.forEach { exercise ->
                    RecordsCard(exercise) {
                        navigator.push(PersonalRecordHistoryScreen(exercise))
                    }
                }
            }
        }
    }

    @Composable
    private fun RecordsCard(exercise: ExerciseRecords, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .apexCard()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    exercise.name.uppercase(),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (exercise.isRecent) {
                    Text(
                        stringResource(R.string.new_record).uppercase(),
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                        color = ApexColors.onAccent,
                        modifier = Modifier
                            .background(ApexColors.accent, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Chevron()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .border(1.dp, ApexColors.border, RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RecordStat(stringResource(R.string.max_weight), exercise.maxWeight, Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(ApexColors.border)
                )
                RecordStat(stringResource(R.string.estimated_one_rep_max), exercise.oneRepMax, Modifier.weight(1f))
            }

            ApexLabel(stringResource(R.string.last_record, DateFormats.date(exercise.lastAchievedAt)))
        }
    }

    @Composable
    private fun RecordStat(label: String, record: PersonalRecord?, modifier: Modifier) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                record?.let { PersonalRecordFormat.kilograms(it.value) } ?: "—",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                label.uppercase(),
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                color = ApexColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val reps = record?.reps
            if (record != null && record.isMaxWeight && reps != null) {
                Text(
                    "× ${PersonalRecordFormat.number(reps)}",
                    style = ApexText.caption2,
                    color = ApexColors.secondaryLabel
                )
            }
        }
    }

    private suspend fun load() {
        isLoading = true
        try {
            exercises = ExerciseRecords.group(PersonalRecord.load(clientId))
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }
    }
}

// All records of one exercise, newest first.
class PersonalRecordHistoryScreen(private val exercise: ExerciseRecords) : Screen() {

    @Composable
    override fun Content() {
        ScrollScreen(topBar = { TopBar(title = exercise.name) }) {
            FormSection(header = stringResource(R.string.record_history)) {
                exercise.history.forEach { record ->
                    RecordRow(record)
                    if (record.id != exercise.history.last().id) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 36.dp),
                            color = ApexColors.separator,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun RecordRow(record: PersonalRecord) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (record.isMaxWeight) Icons.Outlined.Scale else Icons.Outlined.Functions,
                    contentDescription = null,
                    tint = ApexColors.accent
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (record.isMaxWeight) R.string.max_weight else R.string.estimated_one_rep_max),
                    style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(DateFormats.date(record.achievedAt), style = ApexText.caption, color = ApexColors.secondaryLabel)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    PersonalRecordFormat.kilograms(record.value),
                    style = ApexText.subheadline.copy(fontWeight = FontWeight.Bold)
                )
                // The set the record comes from.
                Text(
                    PersonalRecordFormat.set(record.weight, record.reps),
                    style = ApexText.caption,
                    color = ApexColors.secondaryLabel
                )
            }
        }
    }
}

internal object PersonalRecordFormat {
    // Up to one decimal in the device's format, e.g. "102,5".
    fun number(value: Double): String =
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(value)

    fun kilograms(value: Double): String = "${number(value)} kg"

    // "10 × 40 kg" or "40 kg".
    fun set(weight: Double, reps: Double?): String =
        if (reps == null) kilograms(weight) else "${number(reps)} × ${kilograms(weight)}"
}
