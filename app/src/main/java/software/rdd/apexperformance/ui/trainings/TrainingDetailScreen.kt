package software.rdd.apexperformance.ui.trainings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.SaveTrainingTemplateRequest
import software.rdd.apexperformance.model.SupersetLabels
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.model.loadWorkout
import software.rdd.apexperformance.model.distanceSetParts
import software.rdd.apexperformance.model.setDuration
import software.rdd.apexperformance.model.setNumbers
import software.rdd.apexperformance.ui.workouts.WorkoutDetailScreen
import software.rdd.apexperformance.model.TrainingTemplate
import software.rdd.apexperformance.ui.components.ApexPictureBackground
import software.rdd.apexperformance.ui.components.ApexTitle
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SmallProgress
import software.rdd.apexperformance.ui.components.ToolbarIcon
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import java.time.Instant
import androidx.compose.material.icons.outlined.Timer

// Exercises and sets of one training. Staff can edit it.
class TrainingDetailScreen(
    training: Training,
    private val heroImageUrl: String? = null,
    // Client's trainings, for the "last time" sets while editing.
    private val history: List<Training> = emptyList(),
    private val onSaved: ((Training) -> Unit)? = null,
    private val onDeleted: ((String) -> Unit)? = null
) : Screen() {

    private var training by mutableStateOf(training)
    private var showMenu by mutableStateOf(false)
    private var showDeleteDialog by mutableStateOf(false)
    private var isUpdating by mutableStateOf(false)
    // "Save as template" asks for the template's name.
    private var showSaveAsTemplate by mutableStateOf(false)
    private var templateName by mutableStateOf("")
    private var isSavingTimes by mutableStateOf(false)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen(
            topBar = {
                TopBar(title = training.name) {
                    if (onSaved != null) ActionsMenu(navigator)
                }
            }
        ) {
            ApexPictureBackground(
                imageUrl = heroImageUrl,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(170.dp)
            )

            Header()

            // Start and end marked by the coach, heart rate from the client's Apple Health.
            TimesCard()

            if (training.exercises.isEmpty()) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                    EmptyText(stringResource(R.string.no_exercises))
                }
            }

            TrainingExerciseCards(training.exercises)

            val note = training.note
            if (!note.isNullOrEmpty()) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.notes)) {
                    Text(note, style = ApexText.body, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (showDeleteDialog) {
            ConfirmDialog(
                title = stringResource(R.string.delete_training_question),
                message = null,
                confirmText = stringResource(R.string.delete),
                onConfirm = { launch { delete(navigator) } },
                onDismiss = { showDeleteDialog = false }
            )
        }

        if (showSaveAsTemplate) {
            TemplateNameDialog(
                name = templateName,
                onNameChange = { templateName = it },
                onSave = { launch { saveAsTemplate() } },
                onDismiss = { showSaveAsTemplate = false }
            )
        }
    }

    @Composable
    private fun Header() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                DateFormats.date(training.date),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            )
            ApexTitle(training.name)

            val completedAt = training.completedAt
            if (completedAt != null) {
                StatusLabel(
                    Icons.Filled.CheckCircle,
                    stringResource(R.string.completed_at, DateFormats.dateAndTime(completedAt)),
                    ApexColors.green
                )
            } else if (training.isInProgress) {
                StatusLabel(Icons.Outlined.Timer, stringResource(R.string.in_progress), ApexColors.blue)
            } else {
                StatusLabel(Icons.Outlined.CalendarMonth, stringResource(R.string.planned), ApexColors.orange)
            }
        }
    }

    @Composable
    private fun TimesCard() {
        val isStaff = !AuthManager.hasRole(Roles.CLIENT)
        val startedAt = training.startedAt
        val endedAt = training.endedAt
        val heartRate = training.heartRate
        // Clients see the card once the coach marked the training.
        if (!isStaff && startedAt == null && heartRate == null) return

        CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.training_time)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (startedAt != null) {
                    Row {
                        TimesValue(stringResource(R.string.training_started), DateFormats.time(startedAt))
                        if (endedAt != null) {
                            TimesValue(stringResource(R.string.training_ended), DateFormats.time(endedAt))
                            val minutes = Math.round(java.time.Duration.between(startedAt, endedAt).seconds / 60.0).toInt()
                            TimesValue(stringResource(R.string.training_duration), stringResource(R.string.duration_minutes, minutes))
                        } else {
                            Text(
                                stringResource(R.string.training_in_progress),
                                style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                                color = ApexColors.orange,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else if (isStaff) {
                    Text(stringResource(R.string.training_time_hint), style = ApexText.footnote, color = ApexColors.secondaryLabel)
                }

                if (isStaff) {
                    when {
                        startedAt == null -> ApexPrimaryButton(
                            stringResource(R.string.start_training),
                            icon = Icons.Filled.PlayArrow,
                            isLoading = isSavingTimes
                        ) { launch { saveTimes(Instant.now(), null) } }
                        endedAt == null -> ApexPrimaryButton(
                            stringResource(R.string.end_training),
                            icon = Icons.Filled.Stop,
                            isLoading = isSavingTimes
                        ) { launch { saveTimes(startedAt, Instant.now()) } }
                    }
                    if (startedAt != null) {
                        Text(
                            stringResource(R.string.clear_training_times),
                            style = ApexText.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = ApexColors.red,
                            modifier = Modifier.clickable(enabled = !isSavingTimes) { launch { saveTimes(null, null) } }
                        )
                    }
                }

                if (heartRate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = ApexColors.red, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.heart_rate), style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = ApexColors.red)
                    }
                    Row {
                        val bpm = stringResource(R.string.bpm)
                        TimesValue(stringResource(R.string.heart_rate_min), "${heartRate.min} $bpm")
                        TimesValue(stringResource(R.string.heart_rate_average), "${heartRate.average} $bpm")
                        TimesValue(stringResource(R.string.heart_rate_max), "${heartRate.max} $bpm")
                    }
                } else if (endedAt != null && isStaff) {
                    Text(stringResource(R.string.heart_rate_from_client_hint), style = ApexText.footnote, color = ApexColors.secondaryLabel)
                }
            }
        }
    }

    @Composable
    private fun RowScope.TimesValue(title: String, value: String) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = ApexText.caption, color = ApexColors.secondaryLabel)
            Text(value, style = ApexText.headline)
        }
    }

    private suspend fun saveTimes(startedAt: Instant?, endedAt: Instant?) {
        isSavingTimes = true
        try {
            val response: Training.TimesResponse = ApiClient.request(
                "trainings/${training.id}/times",
                HttpMethod.PUT,
                Training.TimesRequest(startedAt?.toString(), endedAt?.toString())
            )
            training = training.copy(
                startedAt = response.startedAt,
                endedAt = response.endedAt,
                heartRate = response.heartRate,
                isCompleted = response.isCompleted,
                completedAt = response.completedAt
            )
            onSaved?.invoke(training)
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSavingTimes = false
        }
    }

    @Composable
    private fun StatusLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(text, style = ApexText.subheadline, color = color)
        }
    }

    // Edit, mark as completed or planned and delete (staff only).
    @Composable
    private fun ActionsMenu(navigator: Navigator) {
        Box {
            ToolbarIcon(
                Icons.Outlined.MoreHoriz,
                contentDescription = stringResource(R.string.training_actions),
                isLoading = isUpdating
            ) { showMenu = true }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = ApexColors.card
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_training)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        navigator.push(
                            TrainingFormScreen(
                                clientId = training.client.id,
                                training = training,
                                history = history
                            ) { saved ->
                                training = saved
                                onSaved?.invoke(saved)
                            }
                        )
                    }
                )

                CompletionMenuItem(isCompleted = training.isCompleted) {
                    showMenu = false
                    launch {
                        isUpdating = true
                        try {
                            toggleCompletion(training)?.let { updated ->
                                training = updated
                                onSaved?.invoke(updated)
                            }
                        } finally {
                            isUpdating = false
                        }
                    }
                }

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy_training)) },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        // The copy is a new training, so it is only added to the list.
                        navigator.push(
                            TrainingFormScreen(copying = training, history = history) { saved ->
                                onSaved?.invoke(saved)
                            }
                        )
                    }
                )

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.save_as_template)) },
                    leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        templateName = training.name
                        showSaveAsTemplate = true
                    }
                )

                if (onDeleted != null) {
                    DeleteTrainingMenuItem {
                        showMenu = false
                        showDeleteDialog = true
                    }
                }
            }
        }
    }

    private suspend fun saveAsTemplate() {
        isUpdating = true
        try {
            TrainingTemplate.save(SaveTrainingTemplateRequest.from(templateName.trim(), training))
            ToastManager.show(R.string.template_created, ToastType.SUCCESS)
        } catch (e: Exception) {
            showError(e)
        } finally {
            isUpdating = false
        }
    }

    private suspend fun delete(navigator: Navigator) {
        isUpdating = true
        try {
            if (deleteTraining(training)) {
                onDeleted?.invoke(training.id)
                navigator.pop()
            }
        } finally {
            isUpdating = false
        }
    }
}

// Cards of a training's or template's exercises with their sets,
// numbered "1.", "2a", "2b"... like the web.
@Composable
internal fun TrainingExerciseCards(exercises: List<Training.Exercise>) {
    val sorted = exercises.sortedBy { it.order }
    val labels = SupersetLabels.labels(sorted.map { it.isSupersetWithPrevious })
    sorted.forEachIndexed { index, exercise ->
        val isInSuperset = exercise.isSupersetWithPrevious ||
            (index + 1 < sorted.size && sorted[index + 1].isSupersetWithPrevious)
        key(exercise.id) {
            TrainingExerciseCard(exercise, labels[index], isInSuperset)
        }
    }
}

@Composable
private fun TrainingExerciseCard(exercise: Training.Exercise, label: String, isInSuperset: Boolean) {
    CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isInSuperset) {
                // Exercises of a superset are done one after another without rest.
                SupersetBadge(modifier = Modifier.padding(bottom = 6.dp))
            }

            // Opens the workout with its description, video and muscles.
            val navigator = LocalNavigator.current
            val scope = rememberCoroutineScope()
            var isOpening by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isOpening) {
                        isOpening = true
                        scope.launch {
                            try {
                                navigator.push(WorkoutDetailScreen(loadWorkout(exercise.workoutId), canEdit = false, onSaved = {}))
                            } catch (e: Exception) {
                                ToastManager.show(R.string.workout_not_available, ToastType.ERROR)
                            } finally {
                                isOpening = false
                            }
                        }
                    }
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$label ${exercise.workoutName.localized}".uppercase(),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    modifier = Modifier.weight(1f)
                )
                if (isOpening) {
                    SmallProgress(size = 14.dp)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ApexColors.tertiaryLabel,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            val sets = exercise.sets.sortedBy { it.order }
            if (sets.isNotEmpty()) {
                TrainingSetRow(
                    stringResource(R.string.set),
                    // Sets of a timed workout (e.g. plank) have a duration instead of reps,
                    // sets over a distance (e.g. rowing) the distance, duration and pace.
                    stringResource(
                        when {
                            sets.any { it.distanceMeters != null } -> R.string.distance
                            sets.any { it.durationSeconds != null } -> R.string.time
                            else -> R.string.reps
                        }
                    ),
                    stringResource(R.string.kg),
                    isHeader = true
                )
            }
            val numbers = sets.setNumbers
            sets.forEachIndexed { index, set ->
                val number = numbers[index]
                HorizontalDivider(thickness = 0.5.dp, color = ApexColors.border)
                TrainingSetRow(
                    // Drop set of the set above, done right after it.
                    number?.toString() ?: "↳ ${stringResource(R.string.drop_set_short)}",
                    distanceSetParts(set, exercise.paceType).takeIf { it.isNotEmpty() }?.joinToString(" · ")
                        ?: set.durationSeconds?.let { setDuration(it) }
                        ?: set.reps?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "—",
                    set.weight?.let(::formatWeight) ?: "—",
                    isDropSet = number == null
                )
            }

            val note = exercise.note
            if (!note.isNullOrEmpty()) {
                Text(
                    note,
                    style = ApexText.caption,
                    color = ApexColors.secondaryLabel,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

// Row of the sets table: set number, repetitions and weight.
@Composable
private fun TrainingSetRow(set: String, reps: String, weight: String, isHeader: Boolean = false, isDropSet: Boolean = false) {
    val style = if (isHeader) {
        TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = ApexColors.secondaryLabel)
    } else {
        ApexText.subheadline.copy(color = ApexColors.label)
    }
    fun text(value: String) = if (isHeader) value.uppercase() else value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isHeader) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text(set),
            style = if (isDropSet) ApexText.caption.copy(fontWeight = FontWeight.SemiBold, color = ApexColors.main) else style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width(64.dp)
                .padding(start = if (isDropSet) 8.dp else 0.dp)
        )
        Text(text(reps), style = style, modifier = Modifier.weight(1f))
        Text(text(weight), style = style, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
    }
}

// Accent capsule marking exercises done together without rest.
@Composable
internal fun SupersetBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.superset).uppercase(),
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = ApexColors.onAccent,
        modifier = modifier
            .background(ApexColors.accent, CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// Asks for the name of a template made from a training ("Save as template").
@Composable
internal fun TemplateNameDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val isValid = name.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ApexColors.background,
        title = { Text(stringResource(R.string.save_as_template), style = ApexText.headline) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.save_as_template_hint), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
                PlainTextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = stringResource(R.string.name),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ApexColors.groupedBackground)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onDismiss()
                    onSave()
                }
            ) {
                Text(
                    stringResource(R.string.save),
                    color = if (isValid) ApexColors.main else ApexColors.tertiaryLabel,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = ApexColors.main)
            }
        }
    )
}
