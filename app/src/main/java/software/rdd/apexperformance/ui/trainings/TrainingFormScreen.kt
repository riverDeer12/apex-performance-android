package software.rdd.apexperformance.ui.trainings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.PaceType
import software.rdd.apexperformance.model.SaveTrainingRequest
import software.rdd.apexperformance.model.SaveTrainingTemplateRequest
import software.rdd.apexperformance.model.SupersetLabels
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.model.TrainingTemplate
import software.rdd.apexperformance.model.Workout
import software.rdd.apexperformance.model.formatPace
import software.rdd.apexperformance.model.setNumbers
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.MenuPicker
import software.rdd.apexperformance.ui.components.NotifyClientRow
import software.rdd.apexperformance.ui.components.DurationWheelField
import software.rdd.apexperformance.ui.components.NumberWheelField
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.SaveAction
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

// Creates or edits a client's training (staff only). Next to every
// exercise it shows the sets the client did in the same exercise on
// the previous training, same as the web form. The same form creates
// and edits training templates, which have no client, date or state.
class TrainingFormScreen private constructor(
    private val clientId: String?,
    // Training being edited, null for a new one.
    private val training: Training?,
    // Client's other trainings, used for the "last time" sets.
    private val history: List<Training>,
    private val onSaved: ((Training) -> Unit)?,
    // Template form: the template being edited, null for a new one.
    private val isTemplate: Boolean,
    private val template: TrainingTemplate?,
    private val onTemplateSaved: ((TrainingTemplate) -> Unit)?,
    // Training whose exercises and sets a new training starts with.
    copying: Training?
) : Screen() {

    constructor(
        clientId: String,
        training: Training? = null,
        history: List<Training>,
        onSaved: ((Training) -> Unit)? = null
    ) : this(clientId, training, history, onSaved, false, null, null, null)

    // New training for the same client with the exercises and sets of
    // the given one ("Copy training"), planned for today.
    constructor(
        copying: Training,
        history: List<Training>,
        onSaved: ((Training) -> Unit)? = null
    ) : this(copying.client.id, null, history, onSaved, false, null, null, copying)

    // Form of a new template, or of the given one to edit it.
    constructor(
        template: TrainingTemplate?,
        onSaved: ((TrainingTemplate) -> Unit)? = null
    ) : this(null, null, emptyList(), null, true, template, onSaved, null)

    private var name by mutableStateOf(training?.name ?: copying?.name ?: template?.name.orEmpty())
    private var date by mutableStateOf(training?.date ?: Instant.now())
    private var note by mutableStateOf(training?.note ?: copying?.note ?: template?.note.orEmpty())
    private var isCompleted by mutableStateOf(training?.isCompleted ?: false)
    // The client gets a push about a new training, about an edit only when asked.
    private var notifyClient by mutableStateOf(training == null)
    private var exercises by mutableStateOf(
        (training?.exercises ?: copying?.exercises ?: template?.exercises).orEmpty()
            .sortedBy { it.order }
            .map { ExerciseDraft.from(it) }
    )

    // What the form saves, as it was when it opened.
    private val initialState = formState()

    private fun formState(): List<Any?> = listOf(name, date, note, isCompleted, exercises)

    override val hasUnsavedChanges: Boolean get() = formState() != initialState

    private var workouts by mutableStateOf<List<Workout>>(emptyList())
    private var isSaving by mutableStateOf(false)
    private var showDatePicker by mutableStateOf(false)

    // Templates a new training can be filled from.
    private var templates by mutableStateOf<List<TrainingTemplate>>(emptyList())
    private var selectedTemplateId by mutableStateOf<String?>(null)

    // Only a new training can be filled from a template.
    private val canUseTemplates: Boolean
        get() = !isTemplate && training == null

    @get:StringRes
    private val title: Int
        get() = if (isTemplate) {
            if (template == null) R.string.new_template else R.string.edit_template
        } else {
            if (training == null) R.string.new_training else R.string.edit_training
        }

    private val isValid: Boolean
        get() = name.trim().isNotEmpty() && exercises.all { it.isValid }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        LaunchedEffect(Unit) { loadWorkouts() }
        LaunchedEffect(Unit) { loadTemplates() }

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(title)) {
                    SaveAction(enabled = isValid, isSaving = isSaving) { launch { save(navigator) } }
                }
            }
        ) {
            if (canUseTemplates && templates.isNotEmpty()) {
                Section(
                    footer = { Text(stringResource(R.string.from_template_hint), style = ApexText.footnote, color = ApexColors.secondaryLabel) }
                ) {
                    val noTemplate = stringResource(R.string.no_template)
                    MenuPicker(
                        label = stringResource(R.string.from_template),
                        selectedText = templates.firstOrNull { it.id == selectedTemplateId }?.name ?: noTemplate,
                        options = listOf<TrainingTemplate?>(null) + templates,
                        optionText = { it?.name ?: noTemplate },
                        onSelect = { selected ->
                            selectedTemplateId = selected?.id
                            if (selected != null) applyTemplate(selected)
                        }
                    )
                }
            }

            Section {
                PlainTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.name),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
                Divider()
                if (!isTemplate) {
                    DateRow()
                    Divider()
                    SwitchRow(stringResource(R.string.completed), isCompleted) { isCompleted = it }
                    Divider()
                    NotifyClientRow(notifyClient) { notifyClient = it }
                    Divider()
                }
                PlainTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = stringResource(R.string.note),
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }

            val labels = SupersetLabels.labels(exercises.map { it.isSupersetWithPrevious })
            exercises.forEachIndexed { index, exercise ->
                key(exercise.id) {
                    ExerciseSection(index, exercise, labels.getOrElse(index) { "${index + 1}." }, navigator)
                }
            }

            Section(
                footer = if (exercises.isEmpty()) {
                    { Text(stringResource(R.string.no_exercises_yet), style = ApexText.footnote, color = ApexColors.secondaryLabel) }
                } else null
            ) {
                ButtonRow(Icons.Filled.Add, stringResource(R.string.add_exercise)) {
                    exercises = exercises + ExerciseDraft()
                }
            }
        }

        if (showDatePicker) TrainingDatePicker()
    }

    // MARK: - Form rows

    @Composable
    private fun DateRow() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.date), style = ApexText.body, modifier = Modifier.weight(1f))
            Text(
                DateFormats.date(date),
                style = ApexText.body,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(ApexColors.label.copy(alpha = 0.06f))
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }

    // Only the day changes, the time of the training is kept.
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TrainingDatePicker() {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = DateFormats.localDate(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    state.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = date.atZone(DateFormats.zone).toLocalTime()
                        date = day.atTime(time).atZone(DateFormats.zone).toInstant()
                    }
                }) { Text(stringResource(android.R.string.ok), color = ApexColors.main) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel), color = ApexColors.main)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = ApexColors.background)
        ) {
            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = ApexColors.background,
                    selectedDayContainerColor = ApexColors.main,
                    todayDateBorderColor = ApexColors.main,
                    todayContentColor = ApexColors.main
                )
            )
        }
    }

    @Composable
    private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = ApexText.body, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = ApexColors.main,
                    checkedBorderColor = ApexColors.main,
                    checkedThumbColor = ApexColors.card
                )
            )
        }
    }

    @Composable
    private fun ButtonRow(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        text: String,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        val color = if (enabled) ApexColors.main else ApexColors.tertiaryLabel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(text, style = ApexText.body, color = color)
        }
    }

    @Composable
    private fun Divider() = HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)

    // Grouped form section like FormSection, with any header content.
    @Composable
    private fun Section(
        header: (@Composable () -> Unit)? = null,
        footer: (@Composable () -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            if (header != null) {
                Box(modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)) { header() }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexColors.secondaryGroupedBackground)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                content = content
            )
            if (footer != null) {
                Box(modifier = Modifier.padding(start = 16.dp, top = 6.dp)) { footer() }
            }
        }
    }

    // MARK: - Exercise

    @Composable
    private fun ExerciseSection(index: Int, exercise: ExerciseDraft, label: String, navigator: Navigator) {
        val previous = previousSets(exercise.workoutId)
        // Done over a distance (e.g. rowing): a distance instead of the weight, with the pace.
        val paceType = paceTypeOf(exercise)
        // Done for a time (e.g. plank): a duration instead of repetitions.
        val isTimed = workouts.firstOrNull { it.id == exercise.workoutId }?.isTimed == true || paceType != null

        Section(header = { ExerciseHeader(index, exercise, label) }) {
            // Exercise picker.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navigator.push(
                            WorkoutPickerScreen(
                                workouts = { workouts },
                                selection = exercise.workoutId
                            ) { id -> updateExercise(exercise.id) { it.copy(workoutId = id, paceType = null) } }
                        )
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.exercise), style = ApexText.body)
                Spacer(Modifier.weight(1f))
                val workout = workouts.firstOrNull { it.id == exercise.workoutId }
                Text(
                    workout?.name?.localized ?: stringResource(R.string.select_exercise),
                    style = ApexText.body,
                    color = ApexColors.secondaryLabel,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(2f, fill = false)
                )
                Chevron()
            }

            if (exercise.sets.isNotEmpty()) {
                Divider()
                SetsHeader(previous, isTimed, paceType)
            }

            val setNumbers = setNumbers(exercise.sets.map { it.isDropSet })
            exercise.sets.forEachIndexed { setIndex, set ->
                key(set.id) {
                    SetRow(exercise.id, setIndex, setNumbers[setIndex], set, previous, isTimed, paceType)
                }
            }

            Divider()
            ButtonRow(
                Icons.Filled.Add,
                stringResource(R.string.add_set),
                enabled = exercise.sets.size < ExerciseDraft.MAX_SETS
            ) {
                updateExercise(exercise.id) { it.addingSet() }
            }

            // Same exercise right after the last set, with less weight.
            Divider()
            ButtonRow(
                Icons.Filled.SubdirectoryArrowRight,
                stringResource(R.string.add_drop_set),
                enabled = exercise.sets.isNotEmpty() && exercise.sets.size < ExerciseDraft.MAX_SETS
            ) {
                updateExercise(exercise.id) { it.addingDropSet(after = it.sets.lastIndex) }
            }

            if (previous != null) {
                Divider()
                PreviousSetsView(previous, currentCount = exercise.sets.size, paceType = paceType) {
                    updateExercise(exercise.id) { draft -> draft.copy(sets = previous.sets.map { SetDraft.from(it) }) }
                }
            }

            Divider()
            PlainTextField(
                value = exercise.note,
                onValueChange = { value -> updateExercise(exercise.id) { it.copy(note = value) } },
                placeholder = stringResource(R.string.note),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
    }

    @Composable
    private fun ExerciseHeader(index: Int, exercise: ExerciseDraft, label: String) {
        var showMenu by remember { mutableStateOf(false) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$label ${stringResource(R.string.exercise)}".uppercase(),
                style = ApexText.footnote,
                color = ApexColors.secondaryLabel
            )
            if (isInSuperset(index)) SupersetBadge()
            Spacer(Modifier.weight(1f))

            Box {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = null,
                    tint = ApexColors.main,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showMenu = true }
                        .padding(4.dp)
                        .size(22.dp)
                )

                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = ApexColors.card) {
                    if (index > 0) {
                        // Done right after the previous exercise without rest.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.superset_with_previous)) },
                            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                            trailingIcon = {
                                if (exercise.isSupersetWithPrevious) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = ApexColors.main)
                                }
                            },
                            onClick = {
                                showMenu = false
                                updateExercise(exercise.id) { it.copy(isSupersetWithPrevious = !it.isSupersetWithPrevious) }
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_superset_exercise)) },
                        leadingIcon = { Icon(Icons.Outlined.LibraryAdd, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            addSupersetExercise(after = index)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_up)) },
                        leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                        enabled = index > 0,
                        onClick = {
                            showMenu = false
                            moveExercise(index, -1)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_down)) },
                        leadingIcon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null) },
                        enabled = index < exercises.size - 1,
                        onClick = {
                            showMenu = false
                            moveExercise(index, 1)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_exercise), color = ApexColors.red) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = ApexColors.red) },
                        onClick = {
                            showMenu = false
                            removeExercise(index)
                        }
                    )
                }
            }
        }
    }

    // Column titles of the sets, with the previous training's date.
    @Composable
    private fun SetsHeader(previous: PreviousSets?, isTimed: Boolean, paceType: String?) {
        val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ApexColors.secondaryLabel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("#", style = style, modifier = Modifier.width(22.dp))
            Text(
                stringResource(if (isTimed) R.string.time else R.string.reps).uppercase(),
                style = style,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text("×", style = style.copy(color = Color.Transparent))
            Text(
                stringResource(if (paceType != null) R.string.distance_meters else R.string.kg).uppercase(),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            if (previous != null) {
                Text(
                    stringResource(R.string.last_time).uppercase(),
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(92.dp)
                )
            }
        }
    }

    // One set with wheels for the repetitions and the weight. Swipe it
    // to the left to delete it.
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SetRow(
        exerciseId: String,
        setIndex: Int,
        number: Int?,
        set: SetDraft,
        previous: PreviousSets?,
        isTimed: Boolean,
        paceType: String?
    ) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        updateExercise(exerciseId) { it.removingSet(set.id) }
                        true
                    }
                    // Swiping right adds a drop set after this set; the row stays.
                    SwipeToDismissBoxValue.StartToEnd -> {
                        updateExercise(exerciseId) { draft ->
                            draft.addingDropSet(after = draft.sets.indexOfFirst { it.id == set.id })
                        }
                        false
                    }
                    else -> false
                }
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val isAddingDrop = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isAddingDrop) ApexColors.main else ApexColors.red)
                        .padding(horizontal = 16.dp),
                    contentAlignment = if (isAddingDrop) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    if (isAddingDrop) {
                        Icon(
                            Icons.Filled.SubdirectoryArrowRight,
                            contentDescription = stringResource(R.string.add_drop_set),
                            tint = Color.White
                        )
                    } else {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete), tint = Color.White)
                    }
                }
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ApexColors.secondaryGroupedBackground)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (number != null) {
                    Text("$number", style = ApexText.body, color = ApexColors.secondaryLabel, modifier = Modifier.width(22.dp))
                } else {
                    // Drop set of the set above.
                    Icon(
                        Icons.Filled.SubdirectoryArrowRight,
                        contentDescription = stringResource(R.string.drop_set),
                        tint = ApexColors.main,
                        modifier = Modifier
                            .width(22.dp)
                            .size(18.dp)
                    )
                }
                if (isTimed) {
                    DurationWheelField(
                        seconds = set.durationSeconds,
                        onSecondsChange = { value -> updateSet(exerciseId, set.id) { it.copy(durationSeconds = value) } },
                        title = stringResource(R.string.time),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    NumberWheelField(
                        value = set.repsValue,
                        onValueChange = { value -> updateSet(exerciseId, set.id) { it.withRepsValue(value) } },
                        range = 1..100,
                        defaultValue = 10.0,
                        allowsEmpty = true,
                        title = stringResource(R.string.reps),
                        // Reps written as text earlier (e.g. "8-10") are kept as they are.
                        displayText = if (set.repsValue == null && set.reps.isNotEmpty()) set.reps else null,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (paceType != null) {
                    // Distance in metres instead of the weight, like the web,
                    // with the pace shown as it is typed.
                    Text("·", style = ApexText.body, color = ApexColors.secondaryLabel)
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        PlainTextField(
                            value = set.distance,
                            onValueChange = { value ->
                                updateSet(exerciseId, set.id) { it.copy(distance = value.filter(Char::isDigit).take(6)) }
                            },
                            placeholder = "m",
                            textAlign = TextAlign.Center,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        val pace = formatPace(paceType, set.distanceMeters, set.durationSeconds)
                        if (!set.isDistanceValid) {
                            // Same limits as the API.
                            Text(
                                "1–${SetDraft.MAX_DISTANCE_METERS} m",
                                style = ApexText.caption2,
                                color = ApexColors.red,
                                maxLines = 1
                            )
                        } else if (pace.isNotEmpty()) {
                            Text(
                                pace,
                                style = ApexText.caption2.copy(fontWeight = FontWeight.SemiBold),
                                color = ApexColors.main,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Text("×", style = ApexText.body, color = ApexColors.secondaryLabel)
                    NumberWheelField(
                        value = set.weightValue,
                        onValueChange = { value -> updateSet(exerciseId, set.id) { it.withWeightValue(value) } },
                        range = 0..300,
                        step = 0.5,
                        unit = stringResource(R.string.kg),
                        defaultValue = 20.0,
                        allowsEmpty = true,
                        title = stringResource(R.string.weight),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (previous != null) {
                    // Same set on the previous training, shown on the side.
                    Text(
                        previous.sets.getOrNull(setIndex)?.let { setDescription(it, paceType) } ?: "—",
                        style = ApexText.footnote,
                        color = ApexColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(92.dp)
                    )
                }
            }
        }
    }

    // Copies the previous training's sets. Sets the current exercise
    // doesn't have yet are listed so they aren't missed.
    @Composable
    private fun PreviousSetsView(previous: PreviousSets, currentCount: Int, paceType: String?, onUse: () -> Unit) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            previous.sets.forEachIndexed { index, set ->
                if (index >= currentCount) {
                    Text("${index + 1}. ${setDescription(set, paceType)}", style = ApexText.footnote, color = ApexColors.secondaryLabel)
                }
            }

            Row(
                modifier = Modifier
                    .clickable(onClick = onUse)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = ApexColors.main, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.use_previous_sets_from, DateFormats.date(previous.date)),
                    style = ApexText.subheadline,
                    color = ApexColors.main
                )
            }
        }
    }

    // MARK: - Editing

    // Fills the name, note and exercises from the chosen template, like the web.
    private fun applyTemplate(template: TrainingTemplate) {
        name = template.name
        note = template.note.orEmpty()
        exercises = template.exercises.sortedBy { it.order }.map { ExerciseDraft.from(it) }
    }

    // Pace type of the exercise's workout: its sets record a distance and show
    // a pace. Sets that already have a distance keep it shown (also when the
    // workout was changed since), like the web.
    private fun paceTypeOf(exercise: ExerciseDraft): String? =
        workouts.firstOrNull { it.id == exercise.workoutId }?.paceType
            ?: exercise.paceType
            ?: if (exercise.sets.any { it.distance.isNotBlank() }) PaceType.PER_KM else null

    private fun updateExercise(id: String, transform: (ExerciseDraft) -> ExerciseDraft) {
        exercises = exercises.map { if (it.id == id) transform(it) else it }
    }

    private fun updateSet(exerciseId: String, setId: String, transform: (SetDraft) -> SetDraft) {
        updateExercise(exerciseId) { draft ->
            draft.copy(sets = draft.sets.map { if (it.id == setId) transform(it) else it })
        }
    }

    private fun moveExercise(index: Int, offset: Int) {
        val target = index + offset
        if (index !in exercises.indices || target !in exercises.indices) return
        val list = exercises.toMutableList()
        val moved = list[index]
        list[index] = list[target]
        list[target] = moved
        // The first exercise can't be in a superset with a previous one.
        list[0] = list[0].copy(isSupersetWithPrevious = false)
        exercises = list
    }

    // "1.", "2a", "2b"... same as the web form.
    private fun isLinked(index: Int): Boolean =
        index > 0 && index in exercises.indices && exercises[index].isSupersetWithPrevious

    private fun isInSuperset(index: Int): Boolean = isLinked(index) || isLinked(index + 1)

    // Adds an exercise done right after this one without rest. It goes after
    // the last exercise of the superset and gets the same number of sets.
    private fun addSupersetExercise(after: Int) {
        if (after !in exercises.indices) return
        var last = after
        while (isLinked(last + 1)) last += 1

        val draft = ExerciseDraft(
            isSupersetWithPrevious = true,
            sets = List(maxOf(exercises[after].sets.size, 1)) { SetDraft() }
        )
        exercises = exercises.toMutableList().apply { add(last + 1, draft) }
    }

    private fun removeExercise(index: Int) {
        if (index !in exercises.indices) return
        val list = exercises.toMutableList()
        // When the first exercise of a superset is removed,
        // the next one starts the superset instead.
        if (!isLinked(index) && isLinked(index + 1)) {
            list[index + 1] = list[index + 1].copy(isSupersetWithPrevious = false)
        }
        list.removeAt(index)
        exercises = list
    }

    // MARK: - Previous training

    private class PreviousSets(val date: Instant, val sets: List<Training.ExerciseSet>)

    // Sets of the same exercise from the client's latest training
    // before this one, or null when the client didn't do it yet.
    private fun previousSets(workoutId: String?): PreviousSets? {
        if (workoutId == null) return null
        val clientId = clientId ?: return null

        val earlier = history
            .filter { it.client.id == clientId && it.id != training?.id && !it.date.isAfter(date) }
            .sortedByDescending { it.date }

        for (previous in earlier) {
            val exercise = previous.exercises.firstOrNull { it.workoutId == workoutId && it.sets.isNotEmpty() }
            if (exercise != null) return PreviousSets(previous.date, exercise.sets.sortedBy { it.order })
        }
        return null
    }

    // MARK: - API

    private suspend fun loadWorkouts() {
        if (workouts.isNotEmpty()) return

        try {
            val response: List<Workout> = ApiClient.get("workouts")
            workouts = response.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.localized })
        } catch (e: Exception) {
            showError(e)
        }
    }

    private suspend fun loadTemplates() {
        if (!canUseTemplates || templates.isNotEmpty()) return
        // Optional, the form works without templates.
        templates = try {
            TrainingTemplate.loadAll()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun save(navigator: Navigator) {
        if (isTemplate) {
            saveTemplate(navigator)
            return
        }
        val clientId = clientId ?: return

        isSaving = true
        try {
            val trimmedNote = note.trim()
            val request = SaveTrainingRequest(
                client = clientId,
                name = name.trim(),
                // ISO 8601 without fractions, like ISO8601DateFormatter on iOS.
                date = date.truncatedTo(ChronoUnit.SECONDS).toString(),
                note = trimmedNote.ifEmpty { null },
                isCompleted = isCompleted,
                exercises = exercises.mapNotNull { it.request },
                notifyClient = notifyClient
            )

            val saved = Training.save(request, training?.id)
            onSaved?.invoke(saved)
            ToastManager.show(R.string.training_saved_successfully, ToastType.SUCCESS)
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }

    private suspend fun saveTemplate(navigator: Navigator) {
        isSaving = true
        try {
            val trimmedNote = note.trim()
            val request = SaveTrainingTemplateRequest(
                name = name.trim(),
                note = trimmedNote.ifEmpty { null },
                exercises = exercises.mapNotNull { it.request }
            )

            val saved = TrainingTemplate.save(request, template?.id)
            onTemplateSaved?.invoke(saved)
            ToastManager.show(if (template == null) R.string.template_created else R.string.template_updated, ToastType.SUCCESS)
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }
}

// MARK: - Drafts

private data class ExerciseDraft(
    val id: String = UUID.randomUUID().toString(),
    val workoutId: String? = null,
    val note: String = "",
    // New exercise starts with one empty set.
    val sets: List<SetDraft> = listOf(SetDraft()),
    val isSupersetWithPrevious: Boolean = false,
    // Workout's pace type from the saved training, until the workouts are loaded.
    val paceType: String? = null
) {
    val isValid: Boolean
        get() = workoutId != null && note.length <= 500 && sets.size <= MAX_SETS && sets.all { it.isValid }

    // Adds a set copying the last one that isn't a drop set, as sets
    // usually repeat or change only a little.
    fun addingSet(): ExerciseDraft {
        if (sets.size >= MAX_SETS) return this
        val last = sets.lastOrNull { !it.isDropSet } ?: sets.lastOrNull()
        return copy(
            sets = sets + SetDraft(
                reps = last?.reps.orEmpty(),
                weight = last?.weight.orEmpty(),
                durationSeconds = last?.durationSeconds,
                distance = last?.distance.orEmpty()
            )
        )
    }

    // Adds a drop set after the set at the index and its drop sets:
    // the same reps with about a fifth less weight.
    fun addingDropSet(after: Int): ExerciseDraft {
        if (after !in sets.indices || sets.size >= MAX_SETS) return this
        var end = after + 1
        while (end < sets.size && sets[end].isDropSet) end += 1
        val previous = sets[end - 1]
        val drop = SetDraft(
            reps = previous.reps,
            isDropSet = true,
            durationSeconds = previous.durationSeconds,
            distance = previous.distance
        )
            // Rounded to half a kilogram, like the weight wheel.
            .withWeightValue(previous.weightValue?.let { Math.round(it * 0.8 * 2) / 2.0 })
        return copy(sets = sets.toMutableList().apply { add(end, drop) })
    }

    // Removes a set; the first one left can't be a drop set.
    fun removingSet(id: String): ExerciseDraft {
        val left = sets.filter { it.id != id }
        return copy(sets = left.mapIndexed { index, set -> if (index == 0) set.copy(isDropSet = false) else set })
    }

    val request: SaveTrainingRequest.Exercise?
        get() {
            val workoutId = workoutId ?: return null
            val note = note.trim()
            return SaveTrainingRequest.Exercise(
                workout = workoutId,
                note = note.ifEmpty { null },
                // Sets without repetitions, weight, duration and distance are not saved.
                sets = sets.mapNotNull { it.request },
                isSupersetWithPrevious = isSupersetWithPrevious
            )
        }

    companion object {
        const val MAX_SETS = 50

        fun from(exercise: Training.Exercise) = ExerciseDraft(
            workoutId = exercise.workoutId,
            note = exercise.note.orEmpty(),
            isSupersetWithPrevious = exercise.isSupersetWithPrevious,
            paceType = exercise.paceType,
            sets = exercise.sets.sortedBy { it.order }.map { SetDraft.from(it) }
        )
    }
}

private data class SetDraft(
    val id: String = UUID.randomUUID().toString(),
    val reps: String = "",
    val weight: String = "",
    // Done right after the previous set with less weight.
    val isDropSet: Boolean = false,
    // How long the set lasted, for timed workouts (e.g. plank).
    val durationSeconds: Int? = null,
    // Metres as typed, for workouts with a pace type (e.g. rowing).
    val distance: String = ""
) {
    private val trimmedReps: String get() = reps.trim()

    // Reps for the wheel, null when empty or not a single number.
    val repsValue: Double? get() = trimmedReps.toIntOrNull()?.toDouble()

    fun withRepsValue(value: Double?) = copy(reps = value?.toInt()?.toString().orEmpty())

    // Weight for the wheel.
    val weightValue: Double? get() = parsedWeight

    fun withWeightValue(value: Double?) = copy(weight = value?.let { plainNumber(it) }.orEmpty())

    // Accepts both "40,5" and "40.5".
    private val parsedWeight: Double?
        get() {
            val value = weight.trim().replace(',', '.')
            return if (value.isEmpty()) null else value.toDoubleOrNull()
        }

    private val isWeightValid: Boolean
        get() {
            if (weight.isBlank()) return true
            val parsed = parsedWeight ?: return false
            return parsed in 0.0..9999.0
        }

    // Distance in metres, null when empty or not a number.
    val distanceMeters: Int? get() = distance.trim().toIntOrNull()

    // Same limits as the API.
    val isDistanceValid: Boolean
        get() = distance.isBlank() || distanceMeters?.let { it in 1..MAX_DISTANCE_METERS } == true

    val isValid: Boolean get() = trimmedReps.length <= 50 && isWeightValid && isDistanceValid

    val request: SaveTrainingRequest.ExerciseSet?
        get() {
            val reps = trimmedReps
            val meters = distanceMeters
            if (reps.isEmpty() && parsedWeight == null && durationSeconds == null && meters == null) return null
            return SaveTrainingRequest.ExerciseSet(
                reps = reps.ifEmpty { null },
                weight = parsedWeight,
                isDropSet = isDropSet,
                durationSeconds = durationSeconds,
                distanceMeters = meters
            )
        }

    companion object {
        const val MAX_DISTANCE_METERS = 500_000

        fun from(set: Training.ExerciseSet) = SetDraft(
            isDropSet = set.isDropSet,
            durationSeconds = set.durationSeconds,
            distance = set.distanceMeters?.toString().orEmpty(),
            reps = set.reps.orEmpty(),
            weight = set.weight?.let { plainNumber(it) }.orEmpty()
        )

        // "40" or "42.5", without a trailing ".0".
        private fun plainNumber(value: Double): String =
            if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
    }
}

// MARK: - Workout picker

// Searchable list of all workouts to choose a training exercise.
private class WorkoutPickerScreen(
    // Read when shown, as the form may still be loading them.
    private val workouts: () -> List<Workout>,
    private val selection: String?,
    private val onSelect: (String) -> Unit
) : Screen() {

    private var searchText by mutableStateOf("")

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val all = workouts()

        ScrollScreen(
            topBar = { TopBar(title = stringResource(R.string.select_exercise)) },
            showLoading = all.isEmpty()
        ) {
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
                    placeholder = stringResource(R.string.search_workouts),
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

            val filtered = all.filter { it.matches(searchText) }
            if (filtered.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ApexColors.secondaryGroupedBackground)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    filtered.forEach { workout ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(workout.id)
                                    navigator.pop()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(workout.name.localized, style = ApexText.body, modifier = Modifier.weight(1f))
                            if (workout.id == selection) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = ApexColors.main)
                            }
                        }
                        if (workout.id != filtered.last().id) {
                            HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
