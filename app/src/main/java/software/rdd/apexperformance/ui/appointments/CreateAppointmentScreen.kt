package software.rdd.apexperformance.ui.appointments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.core.util.localized
import software.rdd.apexperformance.model.CatalogData
import software.rdd.apexperformance.model.Client
import software.rdd.apexperformance.model.Coach
import software.rdd.apexperformance.model.CreateAppointmentRequest
import software.rdd.apexperformance.model.GetTimeSlotsRequest
import software.rdd.apexperformance.model.TimeSlot
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import software.rdd.apexperformance.ui.components.FormSection
import software.rdd.apexperformance.ui.components.LoadingRow
import software.rdd.apexperformance.ui.components.MenuPicker
import software.rdd.apexperformance.ui.components.SaveAction
import software.rdd.apexperformance.ui.components.SavingOverlay
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.ToggleRow
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class CreateAppointmentScreen : Screen() {

    // Form values
    private var selectedDay by mutableStateOf(startDate())
    private var selectedTimeSlot by mutableStateOf<String?>(null)
    private var selectedAppointmentType by mutableStateOf<String?>(null)
    private var selectedClients by mutableStateOf<List<String>>(emptyList())
    private var selectedCoachId by mutableStateOf<String?>(null)

    // Data
    private var timeSlots by mutableStateOf<List<TimeSlot>>(emptyList())
    private var clients by mutableStateOf<List<Client>>(emptyList())
    private var coaches by mutableStateOf<List<Coach>>(emptyList())
    private var appointmentTypes by mutableStateOf<List<CatalogData>>(emptyList())

    // Loading flags
    private var isSaving by mutableStateOf(false)
    private var isLoadingTimeSlots by mutableStateOf(false)
    private var isLoadingAppointmentTypes by mutableStateOf(false)
    private var isLoadingClients by mutableStateOf(false)
    private var isLoadingCoaches by mutableStateOf(false)
    private var showDatePicker by mutableStateOf(false)
    private var hasLoaded = false

    private val isCoach get() = AuthManager.hasRole(Roles.COACH)
    private val isClient get() = AuthManager.hasRole(Roles.CLIENT)

    private val selectedCoaches: List<String> get() = listOfNotNull(selectedCoachId)

    private val canCreateAppointment: Boolean
        get() = selectedTimeSlot != null && selectedClients.isNotEmpty() && selectedCoaches.isNotEmpty() && !isSaving

    private val selectedSlot: TimeSlot? get() = timeSlots.firstOrNull { it.id == selectedTimeSlot }

    private val isSelectedTimeSlotTaken: Boolean get() = selectedSlot?.isTaken ?: false

    // Only clients ask to join a taken slot. Staff create the appointment
    // as usual and the API adds the clients to the existing one.
    private val sendsJoinRequest: Boolean get() = isSelectedTimeSlotTaken && isClient

    private val confirmButtonTitle: Int
        get() = when {
            sendsJoinRequest -> R.string.join_appointment
            isSelectedTimeSlotTaken -> R.string.add_to_appointment
            else -> R.string.confirm_booking
        }

    // Clients can book from tomorrow on, staff from today.
    private fun startDate(): LocalDate =
        if (AuthManager.hasRole(Roles.CLIENT)) LocalDate.now().plusDays(1) else LocalDate.now()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        LaunchedEffect(Unit) {
            if (!hasLoaded) {
                hasLoaded = true
                loadInitialData()
            }
        }

        val selectValue = stringResource(R.string.select_value)

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(R.string.new_appointment)) {
                    SaveAction(
                        enabled = canCreateAppointment,
                        isSaving = isSaving,
                        icon = if (sendsJoinRequest) Icons.Outlined.GroupAdd else Icons.Filled.Check
                    ) { launch { createAppointment(navigator) } }
                }
            },
            overlay = { if (isSaving) SavingOverlay() }
        ) {
            FormSection {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.select_day), style = ApexText.body, modifier = Modifier.weight(1f))
                    Text(DateFormats.date(selectedDay), style = ApexText.body, color = ApexColors.main)
                }
            }

            if (!isCoach) {
                FormSection {
                    if (isLoadingCoaches) LoadingRow(stringResource(R.string.loading_coaches))
                    MenuPicker(
                        label = stringResource(R.string.select_coaches),
                        selectedText = coaches.firstOrNull { it.id == selectedCoachId }?.let { coachName(it) } ?: selectValue,
                        options = listOf<Coach?>(null) + coaches,
                        optionText = { it?.let { coach -> coachName(coach) } ?: selectValue },
                        onSelect = { coach ->
                            selectedCoachId = coach?.id
                            launch { loadTimeSlots() }
                        },
                        enabled = !isLoadingCoaches && coaches.isNotEmpty()
                    )
                }
            }

            FormSection(header = stringResource(R.string.select_time_slot)) {
                if (isLoadingTimeSlots) LoadingRow(stringResource(R.string.loading_time_slots))
                // Time slots as chips, taken ones can be joined.
                TimeSlotChips(enabled = !isLoadingTimeSlots)
            }

            FormSection {
                if (isLoadingAppointmentTypes) LoadingRow(stringResource(R.string.loading_appointment_types))
                MenuPicker(
                    label = stringResource(R.string.select_appointment_type),
                    selectedText = appointmentTypes.firstOrNull { it.id == selectedAppointmentType }
                        ?.let { localized(it.description.lowercase()) } ?: selectValue,
                    options = listOf<CatalogData?>(null) + appointmentTypes,
                    optionText = { type -> type?.let { localized(it.description.lowercase()) } ?: selectValue },
                    onSelect = { selectedAppointmentType = it?.id },
                    enabled = !isLoadingAppointmentTypes
                )
            }

            if (!isClient) {
                FormSection(header = stringResource(R.string.select_clients)) {
                    if (isLoadingClients) LoadingRow(stringResource(R.string.loading_clients))
                    clients.forEach { client ->
                        ToggleRow(
                            title = client.fullName,
                            checked = client.id in selectedClients,
                            enabled = !isLoadingClients
                        ) { isOn ->
                            selectedClients = if (isOn) selectedClients + client.id else selectedClients - client.id
                        }
                    }
                }
            }

            ApexPrimaryButton(
                text = stringResource(confirmButtonTitle),
                enabled = canCreateAppointment,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) { launch { createAppointment(navigator) } }
        }

        if (showDatePicker) {
            val minDate = startDate()
            val state = rememberDatePickerState(
                initialSelectedDateMillis = selectedDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(minDate)

                    override fun isSelectableYear(year: Int): Boolean = year >= minDate.year
                }
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        showDatePicker = false
                        state.selectedDateMillis?.let { millis ->
                            val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            if (day != selectedDay) {
                                selectedDay = day
                                launch { loadTimeSlots() }
                            }
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
    }

    @Composable
    private fun TimeSlotChips(enabled: Boolean) {
        if (timeSlots.isEmpty()) {
            Text(
                stringResource(R.string.no_time_slots),
                style = ApexText.body,
                color = ApexColors.secondaryLabel,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            return
        }
        // Free slots two in a row; a taken slot takes the whole row
        // so the names of clients already in it fit under the time.
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            timeSlotRows().forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { slot -> TimeSlotChip(slot, enabled) }
                    if (row.size == 1 && row[0].isTaken != true) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // Slots in rows, keeping their order: taken slots alone, free ones in pairs.
    private fun timeSlotRows(): List<List<TimeSlot>> {
        val rows = mutableListOf<List<TimeSlot>>()
        var pending = mutableListOf<TimeSlot>()
        for (slot in timeSlots) {
            if (slot.isTaken == true) {
                if (pending.isNotEmpty()) {
                    rows.add(pending)
                    pending = mutableListOf()
                }
                rows.add(listOf(slot))
            } else {
                pending.add(slot)
                if (pending.size == 2) {
                    rows.add(pending)
                    pending = mutableListOf()
                }
            }
        }
        if (pending.isNotEmpty()) rows.add(pending)
        return rows
    }

    @Composable
    private fun RowScope.TimeSlotChip(slot: TimeSlot, enabled: Boolean) {
        val isSelected = selectedTimeSlot == slot.id
        val (time, slotClients) = splitTimeSlotName(slot.name ?: slot.description ?: "—")
        val contentColor = if (isSelected) ApexColors.onAccent else ApexColors.label

        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) ApexColors.accent else ApexColors.label.copy(alpha = 0.06f))
                .clickable(enabled = enabled) { selectedTimeSlot = if (isSelected) null else slot.id }
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
        ) {
            Text(
                time,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (slot.isTaken == true && slotClients != null) {
                // Clients already in the appointment, it can be joined.
                val clientsColor = if (isSelected) ApexColors.onAccent.copy(alpha = 0.8f) else ApexColors.secondaryLabel
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.People, contentDescription = null, tint = clientsColor, modifier = Modifier.size(14.dp))
                    Text(
                        slotClients,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = clientsColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    @Composable
    private fun coachName(coach: Coach): String = coach.fullName ?: stringResource(R.string.unknown_coach)

    private suspend fun loadInitialData() = coroutineScope {
        val coachJob = async { if (isCoach) setCurrentCoach() else loadCoaches() }
        val clientJob = async { if (isClient) setCurrentClient() else loadClients() }
        val typesJob = async { loadAppointmentTypes() }
        coachJob.await()
        clientJob.await()
        typesJob.await()
    }

    private suspend fun loadTimeSlots() {
        isLoadingTimeSlots = true
        try {
            val request = GetTimeSlotsRequest(coaches = selectedCoaches, day = DateFormats.apiDate(selectedDay))
            val slots: List<TimeSlot> = ApiClient.request("time-slots/available", HttpMethod.POST, request)
            timeSlots = slots
            // One slot is picked right away, none clears the selection.
            selectedTimeSlot = when {
                slots.size == 1 -> slots.first().id
                slots.none { it.id == selectedTimeSlot } -> null
                else -> selectedTimeSlot
            }
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoadingTimeSlots = false
        }
    }

    private suspend fun setCurrentCoach() {
        isLoadingCoaches = true
        try {
            val coach: Coach = ApiClient.get("coaches/current-coach")
            selectedCoachId = coach.id
            coaches = listOf(coach)
            loadTimeSlots()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoadingCoaches = false
        }
    }

    private suspend fun setCurrentClient() {
        isLoadingClients = true
        try {
            val client: Client = ApiClient.get("clients/current-client")
            selectedClients = listOf(client.id)
            clients = listOf(client)
            loadTimeSlots()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoadingClients = false
        }
    }

    private suspend fun loadCoaches() {
        isLoadingCoaches = true
        try {
            val path = if (isClient) "coaches/client" else "coaches/all"
            coaches = ApiClient.get(path)
            if (selectedCoachId == null) {
                coaches.firstOrNull()?.let {
                    selectedCoachId = it.id
                    loadTimeSlots()
                }
            }
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoadingCoaches = false
        }
    }

    private suspend fun loadClients() {
        isLoadingClients = true
        try {
            clients = ApiClient.get("clients")
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoadingClients = false
        }
    }

    private suspend fun loadAppointmentTypes() {
        isLoadingAppointmentTypes = true
        try {
            appointmentTypes = ApiClient.get("appointment-types")
            if (selectedAppointmentType == null) {
                appointmentTypes.firstOrNull()?.let { selectedAppointmentType = it.id }
            }
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoadingAppointmentTypes = false
        }
    }

    private suspend fun createAppointment(navigator: Navigator) {
        isSaving = true
        try {
            if (sendsJoinRequest) {
                joinExistingAppointment()
                ToastManager.show(R.string.successfully_joined_appointment, ToastType.SUCCESS)
            } else {
                val addsToExisting = isSelectedTimeSlotTaken
                sendNewAppointment()
                ToastManager.show(
                    if (addsToExisting) R.string.clients_added_to_appointment else R.string.successfully_created_appointment,
                    ToastType.SUCCESS
                )
            }
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }

    private suspend fun sendNewAppointment() {
        val slot = selectedSlot ?: throw IllegalArgumentException("Invalid or missing time slot")
        val type = selectedAppointmentType ?: throw IllegalArgumentException("Missing appointment type")
        val start = DateFormats.dateTime(selectedDay, slot.startTime.orEmpty())
        val end = DateFormats.dateTime(selectedDay, slot.endTime.orEmpty())
        if (start == null || end == null) throw IllegalArgumentException("Failed to parse time slot start or end time")

        val request = CreateAppointmentRequest(
            type = type,
            timeSlot = slot.id,
            clients = selectedClients,
            coaches = selectedCoaches,
            startTime = start.toString(),
            endTime = end.toString()
        )
        ApiClient.send("appointments", HttpMethod.POST, request)
    }

    // "6:15 - 7:15 (Ana Horvat)" -> ("6:15 - 7:15", "Ana Horvat").
    private fun splitTimeSlotName(name: String): Pair<String, String?> {
        val open = name.indexOf('(')
        if (open < 0 || !name.endsWith(")")) return name to null
        val time = name.substring(0, open).trim()
        val slotClients = name.substring(open + 1, name.length - 1).trim()
        return (time.ifEmpty { name }) to slotClients.ifEmpty { null }
    }

    private suspend fun joinExistingAppointment() {
        val appointmentId = selectedSlot?.appointmentId ?: throw IllegalArgumentException("Invalid or missing appointment ID")
        ApiClient.requestData("appointment-requests/join/$appointmentId")
    }
}
