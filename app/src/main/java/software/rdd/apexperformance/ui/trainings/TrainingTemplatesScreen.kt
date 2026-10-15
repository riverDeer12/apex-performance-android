package software.rdd.apexperformance.ui.trainings

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
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
import software.rdd.apexperformance.model.Client
import software.rdd.apexperformance.model.TrainingTemplate
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import software.rdd.apexperformance.ui.components.ApexScreenHeader
import software.rdd.apexperformance.ui.components.ApexTitle
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.FormSection
import software.rdd.apexperformance.ui.components.NotifyClientRow
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.components.SmallProgress
import software.rdd.apexperformance.ui.components.ToolbarIcon
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.text.Collator
import java.time.Instant
import java.time.ZoneOffset

// Training templates of the coach (administrators see all), opened
// from the workouts tab. Templates are assigned to clients as planned
// trainings, same as on the web.
class TrainingTemplatesScreen : Screen() {

    private var templates by mutableStateOf<List<TrainingTemplate>>(emptyList())
    private var searchText by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var hasLoaded = false
    // Template picked for deletion from the long-press menu.
    private var templateToDelete by mutableStateOf<TrainingTemplate?>(null)

    private val filteredTemplates: List<TrainingTemplate>
        get() {
            val query = searchText.trim()
            if (query.isEmpty()) return templates
            return templates.filter { template ->
                template.name.contains(query, ignoreCase = true) ||
                    template.exercises.any { exercise ->
                        exercise.workoutName.allValues.any { it.contains(query, ignoreCase = true) }
                    }
            }
        }

    // Administrators see templates of several coaches, so then the author is shown too.
    private val showsAuthors: Boolean
        get() = templates.mapNotNull { it.authorName }.toSet().size > 1

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
            showLoading = isLoading && templates.isEmpty(),
            onRefresh = { load() }
        ) {
            ApexScreenHeader(stringResource(R.string.training_templates), stringResource(R.string.training_templates_subtitle))

            ApexPrimaryButton(
                text = stringResource(R.string.new_template),
                icon = Icons.Filled.Add,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                navigator.push(TrainingFormScreen(template = null) { created -> replace(created) })
            }

            if (templates.isNotEmpty()) {
                TrainingsSearchField(
                    text = searchText,
                    onTextChange = { searchText = it },
                    placeholder = stringResource(R.string.search_templates),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                val list = filteredTemplates
                if (list.isEmpty() && !isLoading) {
                    EmptyText(stringResource(if (searchText.isEmpty()) R.string.no_templates else R.string.no_templates_found))
                } else {
                    Column {
                        list.forEach { template ->
                            TemplateRow(template) {
                                navigator.push(
                                    TrainingTemplateDetailScreen(
                                        template,
                                        onSaved = { updated -> replace(updated) },
                                        onDeleted = { templates = templates.filter { it.id != template.id } }
                                    )
                                )
                            }
                            if (template.id != list.last().id) RowDivider()
                        }
                    }
                }
            }
        }

        templateToDelete?.let { template ->
            ConfirmDialog(
                title = stringResource(R.string.delete_template_question),
                message = null,
                confirmText = stringResource(R.string.delete),
                onConfirm = { launch { delete(template) } },
                onDismiss = { templateToDelete = null }
            )
        }
    }

    // Long press for deleting, when the template can be changed.
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun TemplateRow(template: TrainingTemplate, onClick: () -> Unit) {
        var showMenu by remember { mutableStateOf(false) }

        Box {
            SettingsRow(
                icon = Icons.Outlined.ContentPaste,
                iconTint = ApexColors.accent,
                title = template.name,
                subtitle = subtitle(template),
                showChevron = true,
                modifier = Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = if (template.canEdit) ({ showMenu = true }) else null
                )
            )

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = ApexColors.card
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_template), color = ApexColors.red) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = ApexColors.red) },
                    onClick = {
                        showMenu = false
                        templateToDelete = template
                    }
                )
            }
        }
    }

    // "Exercises: 5", or "Exercises: 5 · Ana Horvat".
    @Composable
    private fun subtitle(template: TrainingTemplate): String {
        val count = stringResource(R.string.exercises_count, template.exercises.size)
        val author = template.authorName
        return if (showsAuthors && !author.isNullOrEmpty()) "$count · $author" else count
    }

    private fun replace(template: TrainingTemplate) {
        val collator = Collator.getInstance()
        templates = (templates.filter { it.id != template.id } + template)
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    private suspend fun load() {
        isLoading = true
        try {
            templates = TrainingTemplate.loadAll()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }
    }

    private suspend fun delete(template: TrainingTemplate) {
        try {
            TrainingTemplate.delete(template.id)
            templates = templates.filter { it.id != template.id }
            ToastManager.show(R.string.template_deleted, ToastType.SUCCESS)
        } catch (e: Exception) {
            showError(e)
        }
    }
}

// Exercises of a template, with assigning it to clients and, for its
// author or an administrator, editing and deleting.
class TrainingTemplateDetailScreen(
    template: TrainingTemplate,
    private val onSaved: (TrainingTemplate) -> Unit,
    private val onDeleted: () -> Unit
) : Screen() {

    private var template by mutableStateOf(template)
    private var showMenu by mutableStateOf(false)
    private var showDeleteDialog by mutableStateOf(false)
    private var isDeleting by mutableStateOf(false)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen(
            topBar = {
                TopBar(title = template.name) {
                    if (template.canEdit) ActionsMenu(navigator)
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ApexTitle(template.name)
                val author = template.authorName
                if (!author.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = ApexColors.secondaryLabel, modifier = Modifier.size(16.dp))
                        Text(author, style = ApexText.subheadline, color = ApexColors.secondaryLabel)
                    }
                }
            }

            val hasExercises = template.exercises.isNotEmpty()
            ApexPrimaryButton(
                text = stringResource(R.string.assign_to_clients),
                icon = Icons.AutoMirrored.Outlined.Send,
                enabled = hasExercises,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                navigator.push(AssignTrainingTemplateScreen(template))
            }

            if (!hasExercises) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                    EmptyText(stringResource(R.string.no_exercises))
                }
            }

            TrainingExerciseCards(template.exercises)

            val note = template.note
            if (!note.isNullOrEmpty()) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.notes)) {
                    Text(note, style = ApexText.body, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (showDeleteDialog) {
            ConfirmDialog(
                title = stringResource(R.string.delete_template_question),
                message = null,
                confirmText = stringResource(R.string.delete),
                onConfirm = { launch { delete(navigator) } },
                onDismiss = { showDeleteDialog = false }
            )
        }
    }

    @Composable
    private fun ActionsMenu(navigator: Navigator) {
        Box {
            ToolbarIcon(
                Icons.Outlined.MoreHoriz,
                contentDescription = stringResource(R.string.template_actions),
                enabled = !isDeleting,
                isLoading = isDeleting
            ) { showMenu = true }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = ApexColors.card
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_template)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        navigator.push(
                            TrainingFormScreen(template = template) { saved ->
                                template = saved
                                onSaved(saved)
                            }
                        )
                    }
                )

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_template), color = ApexColors.red) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = ApexColors.red) },
                    onClick = {
                        showMenu = false
                        showDeleteDialog = true
                    }
                )
            }
        }
    }

    private suspend fun delete(navigator: Navigator) {
        isDeleting = true
        try {
            TrainingTemplate.delete(template.id)
            onDeleted()
            ToastManager.show(R.string.template_deleted, ToastType.SUCCESS)
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isDeleting = false
        }
    }
}

// Picks clients and a date; each client gets a planned training
// with the template's exercises and sets.
class AssignTrainingTemplateScreen(private val template: TrainingTemplate) : Screen() {

    private var clients by mutableStateOf<List<Client>>(emptyList())
    private var selectedClients by mutableStateOf<Set<String>>(emptySet())
    private var date by mutableStateOf(Instant.now())
    private var notifyClient by mutableStateOf(true)
    private var searchText by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var isAssigning by mutableStateOf(false)
    private var showDatePicker by mutableStateOf(false)

    private val filteredClients: List<Client>
        get() {
            val query = searchText.trim()
            val collator = Collator.getInstance()
            val sorted = clients.sortedWith { a, b -> collator.compare(a.fullName, b.fullName) }
            if (query.isEmpty()) return sorted
            return sorted.filter { it.fullName.contains(query, ignoreCase = true) }
        }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val context = LocalContext.current

        LaunchedEffect(Unit) { loadClients() }

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(R.string.assign_to_clients)) {
                    if (isAssigning) {
                        Box(modifier = Modifier.padding(horizontal = 12.dp)) { SmallProgress() }
                    } else {
                        TextButton(
                            enabled = selectedClients.isNotEmpty(),
                            onClick = { launch { assign(context, navigator) } }
                        ) {
                            Text(
                                stringResource(R.string.assign),
                                style = ApexText.body.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selectedClients.isNotEmpty()) ApexColors.main else ApexColors.tertiaryLabel
                            )
                        }
                    }
                }
            },
            showLoading = isLoading && clients.isEmpty()
        ) {
            FormSection(
                footer = { Text(stringResource(R.string.assign_template_hint), style = ApexText.footnote, color = ApexColors.secondaryLabel) }
            ) {
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
                HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                NotifyClientRow(notifyClient) { notifyClient = it }
            }

            TrainingsSearchField(
                text = searchText,
                onTextChange = { searchText = it },
                placeholder = stringResource(R.string.search_clients),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Column {
                // Header with the number of selected clients on the right.
                Row(
                    modifier = Modifier.padding(start = 36.dp, end = 36.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.clients).uppercase(),
                        style = ApexText.footnote,
                        color = ApexColors.secondaryLabel,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedClients.isNotEmpty()) {
                        Text(
                            stringResource(R.string.selected_count, selectedClients.size).uppercase(),
                            style = ApexText.footnote,
                            color = ApexColors.secondaryLabel
                        )
                    }
                }

                FormSection {
                    if (clients.isEmpty() && !isLoading) {
                        Text(
                            stringResource(R.string.no_clients),
                            style = ApexText.body,
                            color = ApexColors.secondaryLabel,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    val list = filteredClients
                    list.forEach { client ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggle(client.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(client.fullName, style = ApexText.body, modifier = Modifier.weight(1f))
                            if (client.id in selectedClients) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = ApexColors.main)
                            }
                        }
                        if (client.id != list.last().id) {
                            HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        if (showDatePicker) {
            DayPickerDialog(
                date = date,
                onSelect = { date = it },
                onDismiss = { showDatePicker = false }
            )
        }
    }

    private fun toggle(id: String) {
        selectedClients = if (id in selectedClients) selectedClients - id else selectedClients + id
    }

    private suspend fun loadClients() {
        if (clients.isNotEmpty()) return
        isLoading = true
        try {
            clients = ApiClient.get("clients")
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }
    }

    private suspend fun assign(context: Context, navigator: Navigator) {
        isAssigning = true
        try {
            val count = template.assign(selectedClients.toList(), date, notifyClient)
            ToastManager.show(context.getString(R.string.trainings_created, count), ToastType.SUCCESS)
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isAssigning = false
        }
    }
}

// Search field of the trainings, templates and records lists.
@Composable
internal fun TrainingsSearchField(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .apexCard(cornerRadius = 10.dp)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = ApexColors.secondaryLabel, modifier = Modifier.size(18.dp))
        PlainTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = placeholder,
            textStyle = ApexText.subheadline,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.weight(1f)
        )
        if (text.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = stringResource(R.string.clear),
                tint = ApexColors.secondaryLabel,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onTextChange("") }
            )
        }
    }
}

// Picks a day; the time of the given date is kept.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayPickerDialog(date: Instant, onSelect: (Instant) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = DateFormats.localDate(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                state.selectedDateMillis?.let { millis ->
                    val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    val time = date.atZone(DateFormats.zone).toLocalTime()
                    onSelect(day.atTime(time).atZone(DateFormats.zone).toInstant())
                }
            }) { Text(stringResource(android.R.string.ok), color = ApexColors.main) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
