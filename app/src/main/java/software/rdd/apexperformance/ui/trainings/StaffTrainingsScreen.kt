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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
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
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.ui.components.ApexScreenHeader
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.text.Collator

// Trainings of all the coach's clients (administrators see all), with
// search and filters, same as the web's trainings page. New trainings
// are still made in the client's details.
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
                stringResource(R.string.all_trainings_subtitle),
                showsWordmark = false
            )

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
