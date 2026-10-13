package software.rdd.apexperformance.ui.trainings

import androidx.annotation.StringRes
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.Collator
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.model.Client
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import software.rdd.apexperformance.ui.components.ApexScreenHeader
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Trainings tab of coaches and administrators, like the web's trainings
// menu: trainings of all their clients (administrators see all) with
// search and filters, new trainings, templates and personal records.
// Trainings can still be made and opened in the client's details too.
class StaffTrainingsScreen : Screen() {

    private enum class StatusFilter(@StringRes val title: Int) {
        ALL(R.string.all),
        PLANNED(R.string.planned),
        COMPLETED(R.string.completed)
    }

    private var trainings by mutableStateOf<List<Training>>(emptyList())
    private var searchText by mutableStateOf("")
    private var status by mutableStateOf(StatusFilter.ALL)
    private var clientId by mutableStateOf<String?>(null)
    private var isLoading by mutableStateOf(false)
    private var hasLoaded = false

    // Clients that have trainings, by name.
    private val clients: List<Training.Person>
        get() {
            val collator = Collator.getInstance()
            return trainings.map { it.client }
                .distinctBy { it.id }
                .sortedWith { a, b -> collator.compare(a.fullName, b.fullName) }
        }

    private val filteredTrainings: List<Training>
        get() {
            val query = searchText.trim()
            return trainings.filter { training ->
                when (status) {
                    StatusFilter.ALL -> Unit
                    StatusFilter.PLANNED -> if (training.isCompleted) return@filter false
                    StatusFilter.COMPLETED -> if (!training.isCompleted) return@filter false
                }
                val clientId = clientId
                if (clientId != null && training.client.id != clientId) return@filter false
                if (query.isEmpty()) return@filter true
                training.name.contains(query, ignoreCase = true) ||
                    training.client.fullName.contains(query, ignoreCase = true) ||
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
            ApexScreenHeader(
                stringResource(R.string.trainings),
                stringResource(R.string.all_trainings_subtitle)
            )

            // New training: the client is picked first, then the form opens.
            ApexPrimaryButton(
                stringResource(R.string.new_training),
                modifier = Modifier.padding(horizontal = 20.dp),
                icon = Icons.Filled.Add
            ) {
                navigator.push(
                    ClientListScreen(R.string.new_training) { listNavigator, client ->
                        listNavigator.pop()
                        listNavigator.push(
                            TrainingFormScreen(
                                clientId = client.id,
                                history = trainings.filter { it.client.id == client.id },
                                onSaved = { upsert(it) }
                            )
                        )
                    }
                )
            }

            LinksCard()

            StatusPicker()

            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrainingsSearchField(
                    text = searchText,
                    onTextChange = { searchText = it },
                    placeholder = stringResource(R.string.search_trainings),
                    modifier = Modifier.weight(1f)
                )
                ClientMenu()
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                val list = filteredTrainings
                if (list.isEmpty() && !isLoading) {
                    EmptyText(stringResource(if (trainings.isEmpty()) R.string.no_trainings else R.string.no_trainings_found))
                } else {
                    Column {
                        list.forEach { training ->
                            SettingsRow(
                                icon = if (training.isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.CalendarMonth,
                                iconTint = if (training.isCompleted) ApexColors.green else ApexColors.orange,
                                title = training.name,
                                subtitle = "${training.client.fullName} · ${DateFormats.date(training.date)}",
                                showChevron = true,
                                onClick = {
                                    navigator.push(
                                        TrainingDetailScreen(
                                            training = training,
                                            history = trainings.filter { it.client.id == training.client.id },
                                            onSaved = { upsert(it) },
                                            onDeleted = { id -> trainings = trainings.filter { it.id != id } }
                                        )
                                    )
                                }
                            )
                            if (training.id != list.last().id) RowDivider()
                        }
                    }
                }
            }
        }
    }

    // Templates and personal records, like the web's trainings menu.
    @Composable
    private fun LinksCard() {
        val navigator = LocalNavigator.current

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .apexCard()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            SettingsRow(
                icon = Icons.Outlined.ContentPaste,
                iconTint = ApexColors.accent,
                title = stringResource(R.string.training_templates),
                subtitle = stringResource(R.string.training_templates_subtitle),
                showChevron = true,
                onClick = { navigator.push(TrainingTemplatesScreen()) }
            )

            RowDivider(startIndent = 46.dp)

            SettingsRow(
                icon = Icons.Outlined.EmojiEvents,
                iconTint = ApexColors.accent,
                title = stringResource(R.string.personal_records),
                subtitle = stringResource(R.string.personal_records_hint),
                showChevron = true,
                onClick = {
                    navigator.push(
                        ClientListScreen(R.string.personal_records) { listNavigator, client ->
                            listNavigator.push(PersonalRecordsScreen(client.id, client.fullName))
                        }
                    )
                }
            )
        }
    }

    // Segmented control of the training state, like the iOS picker.
    @Composable
    private fun StatusPicker() {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(ApexColors.systemGray5)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            StatusFilter.entries.forEach { filter ->
                val isSelected = filter == status
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isSelected) ApexColors.card else ApexColors.systemGray5)
                        .clickable { status = filter },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(filter.title),
                        style = ApexText.footnote.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                        color = ApexColors.label,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }

    @Composable
    private fun ClientMenu() {
        var expanded by remember { mutableStateOf(false) }

        Box {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .apexCard(cornerRadius = 10.dp)
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (clientId == null) Icons.Outlined.FilterAlt else Icons.Filled.FilterAlt,
                    contentDescription = stringResource(R.string.filter_by_client),
                    tint = ApexColors.main,
                    modifier = Modifier.size(22.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = ApexColors.card
            ) {
                ClientMenuItem(stringResource(R.string.all_clients), isSelected = clientId == null) {
                    expanded = false
                    clientId = null
                }
                clients.forEach { client ->
                    ClientMenuItem(client.fullName, isSelected = clientId == client.id) {
                        expanded = false
                        clientId = client.id
                    }
                }
            }
        }
    }

    @Composable
    private fun ClientMenuItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
        DropdownMenuItem(
            text = { Text(text) },
            trailingIcon = {
                if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = ApexColors.main)
            },
            onClick = onClick
        )
    }

    private fun upsert(training: Training) {
        trainings = (trainings.filter { it.id != training.id } + training).sortedByDescending { it.date }
    }

    private suspend fun load() {
        isLoading = true
        try {
            trainings = Training.loadAll()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }
    }
}

// Searchable list of the coach's clients (administrators see all) by name.
// Tapping a client calls onPick, e.g. to start a new training for them.
class ClientListScreen(
    @StringRes private val title: Int,
    private val onPick: (Navigator, Client) -> Unit
) : Screen() {

    private var clients by mutableStateOf<List<Client>>(emptyList())
    private var searchText by mutableStateOf("")
    private var isLoading by mutableStateOf(false)

    private val filteredClients: List<Client>
        get() {
            val collator = Collator.getInstance()
            val sorted = clients.sortedWith { a, b -> collator.compare(a.fullName, b.fullName) }
            val query = searchText.trim()
            if (query.isEmpty()) return sorted
            return sorted.filter { it.fullName.contains(query, ignoreCase = true) }
        }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        LaunchedEffect(Unit) {
            if (clients.isEmpty()) load()
        }

        ScrollScreen(
            topBar = { TopBar(title = stringResource(title)) },
            showLoading = isLoading && clients.isEmpty()
        ) {
            TrainingsSearchField(
                text = searchText,
                onTextChange = { searchText = it },
                placeholder = stringResource(R.string.search_clients),
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)
            )

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                val list = filteredClients
                if (list.isEmpty() && !isLoading) {
                    EmptyText(stringResource(R.string.no_clients))
                } else {
                    Column {
                        list.forEach { client ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(navigator, client) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(client.fullName, style = ApexText.body, modifier = Modifier.weight(1f))
                                Chevron()
                            }
                            if (client.id != list.last().id) RowDivider(startIndent = 0.dp)
                        }
                    }
                }
            }
        }
    }

    private suspend fun load() {
        isLoading = true
        try {
            clients = ApiClient.get("clients")
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }
    }
}
