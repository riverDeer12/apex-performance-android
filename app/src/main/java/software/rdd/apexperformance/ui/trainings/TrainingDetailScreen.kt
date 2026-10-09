package software.rdd.apexperformance.ui.trainings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.model.SupersetLabels
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.ui.components.ApexPictureBackground
import software.rdd.apexperformance.ui.components.ApexTitle
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.ToolbarIcon
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

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

            if (training.exercises.isEmpty()) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                    EmptyText(stringResource(R.string.no_exercises))
                }
            }

            val exercises = training.exercises.sortedBy { it.order }
            val labels = SupersetLabels.labels(exercises.map { it.isSupersetWithPrevious })
            exercises.forEachIndexed { index, exercise ->
                val isInSuperset = exercise.isSupersetWithPrevious ||
                    (index + 1 < exercises.size && exercises[index + 1].isSupersetWithPrevious)
                ExerciseCard(exercise, labels[index], isInSuperset)
            }

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
            } else {
                StatusLabel(Icons.Outlined.CalendarMonth, stringResource(R.string.planned), ApexColors.orange)
            }
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

                if (onDeleted != null) {
                    DeleteTrainingMenuItem {
                        showMenu = false
                        showDeleteDialog = true
                    }
                }
            }
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

    @Composable
    private fun ExerciseCard(exercise: Training.Exercise, label: String, isInSuperset: Boolean) {
        CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isInSuperset) {
                    // Exercises of a superset are done one after another without rest.
                    SupersetBadge(modifier = Modifier.padding(bottom = 6.dp))
                }

                Text(
                    "$label ${exercise.workoutName.localized}".uppercase(),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                val sets = exercise.sets.sortedBy { it.order }
                if (sets.isNotEmpty()) {
                    SetRow(
                        stringResource(R.string.set),
                        stringResource(R.string.reps),
                        stringResource(R.string.kg),
                        isHeader = true
                    )
                }
                sets.forEachIndexed { index, set ->
                    HorizontalDivider(thickness = 0.5.dp, color = ApexColors.border)
                    SetRow(
                        "${index + 1}",
                        set.reps?.trim()?.takeIf { it.isNotEmpty() } ?: "—",
                        set.weight?.let(::formatWeight) ?: "—"
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
    private fun SetRow(set: String, reps: String, weight: String, isHeader: Boolean = false) {
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
            Text(text(set), style = style, modifier = Modifier.width(56.dp))
            Text(text(reps), style = style, modifier = Modifier.weight(1f))
            Text(text(weight), style = style, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
        }
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
