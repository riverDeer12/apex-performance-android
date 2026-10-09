package software.rdd.apexperformance.ui.trainings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.theme.ApexColors
import java.text.NumberFormat

// Rows of trainings that open their details, used on client details for
// coaches. With onSaved (staff) trainings can be edited, marked as
// completed or deleted. Long press a row for the quick actions.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrainingRows(
    trainings: List<Training>,
    onSaved: ((Training) -> Unit)? = null,
    onDeleted: ((String) -> Unit)? = null
) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var menuTrainingId by remember { mutableStateOf<String?>(null) }
    var trainingToDelete by remember { mutableStateOf<Training?>(null) }

    Column {
        trainings.forEach { training ->
            Box {
                SettingsRow(
                    icon = if (training.isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.CalendarMonth,
                    iconTint = if (training.isCompleted) ApexColors.green else ApexColors.orange,
                    title = training.name,
                    subtitle = "${DateFormats.date(training.date)} · " +
                        stringResource(R.string.exercises_count, training.exercises.size),
                    showChevron = true,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            navigator.push(
                                TrainingDetailScreen(
                                    training = training,
                                    history = trainings,
                                    onSaved = onSaved,
                                    onDeleted = onDeleted
                                )
                            )
                        },
                        onLongClick = if (onSaved != null) {
                            { menuTrainingId = training.id }
                        } else null
                    )
                )

                DropdownMenu(
                    expanded = menuTrainingId == training.id,
                    onDismissRequest = { menuTrainingId = null },
                    containerColor = ApexColors.card
                ) {
                    CompletionMenuItem(isCompleted = training.isCompleted) {
                        menuTrainingId = null
                        scope.launch {
                            toggleCompletion(training)?.let { onSaved?.invoke(it) }
                        }
                    }
                    DeleteTrainingMenuItem {
                        menuTrainingId = null
                        trainingToDelete = training
                    }
                }
            }

            if (training.id != trainings.last().id) RowDivider()
        }
    }

    trainingToDelete?.let { training ->
        ConfirmDialog(
            title = stringResource(R.string.delete_training_question),
            message = null,
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                scope.launch {
                    if (deleteTraining(training)) onDeleted?.invoke(training.id)
                }
            },
            onDismiss = { trainingToDelete = null }
        )
    }
}

// "Mark as completed" or "Mark as planned", depending on the current state.
@Composable
internal fun CompletionMenuItem(isCompleted: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(if (isCompleted) R.string.mark_as_planned else R.string.mark_as_completed)) },
        leadingIcon = {
            Icon(
                if (isCompleted) Icons.AutoMirrored.Filled.Undo else Icons.Outlined.CheckCircle,
                contentDescription = null
            )
        },
        onClick = onClick
    )
}

@Composable
internal fun DeleteTrainingMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.delete_training), color = ApexColors.red) },
        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = ApexColors.red) },
        onClick = onClick
    )
}

// Marks the training as completed or planned and shows the result.
// Returns the updated training, or null when it failed.
internal suspend fun toggleCompletion(training: Training): Training? = try {
    val updated = training.settingCompletion(!training.isCompleted)
    ToastManager.show(
        if (updated.isCompleted) R.string.training_marked_completed else R.string.training_marked_planned,
        ToastType.SUCCESS
    )
    updated
} catch (e: Exception) {
    showError(e)
    null
}

// Deletes the training and shows the result. Returns true when deleted.
internal suspend fun deleteTraining(training: Training): Boolean = try {
    Training.delete(training.id)
    ToastManager.show(R.string.training_deleted_successfully, ToastType.SUCCESS)
    true
} catch (e: Exception) {
    showError(e)
    false
}

// Weight with up to two decimals in the device's format, e.g. "42,5".
internal fun formatWeight(weight: Double): String =
    NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(weight)

// For example "10 × 40 kg", "10" or "40 kg".
internal fun setDescription(set: Training.ExerciseSet): String {
    val reps = set.reps?.trim()?.takeIf { it.isNotEmpty() }
    val weight = set.weight?.let { "${formatWeight(it)} kg" }
    return when {
        reps != null && weight != null -> "$reps × $weight"
        reps != null -> reps
        weight != null -> weight
        else -> "—"
    }
}
