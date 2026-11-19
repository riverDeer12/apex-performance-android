package software.rdd.apexperformance.ui.trainings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.OfflineTrainingQueue
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.Client
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.model.TrainingSession
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.SaveAction
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SmallProgress
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.UserAvatar
import software.rdd.apexperformance.ui.components.UserNameLabel
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.Duration
import java.time.Instant
import java.util.Locale

// New training form of the coach, for one client or for several clients who
// train at the same time ("+ Client"): every client has their own training
// (the usual training form, one per client, kept while switching), all of
// them share one time. Save saves every training as a normal training of its
// client; one that fails shows its error and Save tries again.
class TrainingSessionScreen private constructor(
    first: TrainingSession.Participant,
    history: List<Training>,
    copying: Training?,
    private val onSaved: ((Training) -> Unit)?
) : Screen() {

    constructor(client: Training.Person, history: List<Training>, onSaved: ((Training) -> Unit)? = null) :
        this(TrainingSession.Participant(client.id, client.firstName, client.lastName, client.pictureUrl), history, null, onSaved)

    // New training for the same client with the exercises and sets of the given one ("Copy training").
    constructor(copying: Training, history: List<Training>, onSaved: ((Training) -> Unit)? = null) :
        this(
            TrainingSession.Participant(copying.client.id, copying.client.firstName, copying.client.lastName, copying.client.pictureUrl),
            history, copying, onSaved
        )

    private var session by mutableStateOf(TrainingSession(listOf(first)))

    // Every client's form; it keeps its state while another client is shown.
    private var forms by mutableStateOf(mapOf(first.id to makeForm(first, history, copying)))

    private var isSaving by mutableStateOf(false)
    private var isAddingClient by mutableStateOf(false)
    private var removing by mutableStateOf<TrainingSession.Participant?>(null)
    // All trainings, loaded once a client is added, for their "last time" sets.
    private var allTrainings: List<Training>? = null

    private fun makeForm(participant: TrainingSession.Participant, history: List<Training>, copying: Training?): TrainingFormScreen {
        val form = if (copying != null) {
            TrainingFormScreen(copying = copying, history = history, onSaved = onSaved)
        } else {
            TrainingFormScreen(participant.clientId, history = history, onSaved = onSaved)
        }
        form.session = TrainingFormScreen.SessionHooks(
            startedAt = { session.startedAt },
            isShared = { session.isShared },
            onStart = { session = session.starting(Instant.now()) }
        )
        return form
    }

    // Asked once for all trainings of the session.
    override val hasUnsavedChanges: Boolean get() = forms.values.any { it.hasUnsavedChanges }

    override fun onDispose() {
        forms.values.forEach { it.onDispose() }
        super.onDispose()
    }

    private val canSave: Boolean
        get() = TrainingSession.canSave(session.participants.map { forms[it.id]?.canBeSaved == true }, isSaving)

    private fun form(participant: TrainingSession.Participant): TrainingFormScreen? = forms[participant.id]

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        // Locking the phone or leaving the app saves every running training.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    forms.values.forEach { form -> form.launch { form.autosave() } }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        ScrollScreen(
            topBar = {
                Column(modifier = Modifier.background(ApexColors.groupedBackground)) {
                    TopBar(title = stringResource(if (session.isShared) R.string.training_session else R.string.new_training)) {
                        SaveAction(enabled = canSave, isSaving = isSaving) { launch { saveAll(navigator) } }
                    }
                    Header(navigator)
                }
            },
            overlay = {
                if (session.isShared) {
                    OthersCard(modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        ) {
            val active = forms[session.activeId]
            if (active != null) {
                key(session.activeId) {
                    active.FormSections(navigator)
                }
            }
            // Room for the lines about the other clients at the bottom.
            if (session.isShared) Spacer(Modifier.height((56 * session.others.size).dp))
        }

        removing?.let { participant ->
            ConfirmDialog(
                title = stringResource(R.string.remove_client_question),
                message = stringResource(R.string.remove_client_info, participant.fullName),
                confirmText = stringResource(R.string.remove_client),
                onConfirm = { launch { remove(participant) } },
                onDismiss = { removing = null }
            )
        }
    }

    // MARK: - Header

    @Composable
    private fun Header(navigator: Navigator) {
        if (session.isShared) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ApexColors.card)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.training_session_hint), style = ApexText.footnote, color = ApexColors.secondaryLabel)
                        Text(
                            session.headerNames,
                            style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    session.startedAt?.let { SharedTimer(it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switcher()
                    AddClientButton(navigator, compact = true)
                }
            }
        } else {
            // One client: whose training it is, and another client can join.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserNameLabel(session.leader.fullName, session.leader.pictureUrl, modifier = Modifier.weight(1f))
                AddClientButton(navigator, compact = false)
            }
        }
    }

    @Composable
    private fun SharedTimer(start: Instant) {
        // Ticks every second while the form is open.
        var now by remember { mutableStateOf(Instant.now()) }
        LaunchedEffect(start) {
            while (true) {
                now = Instant.now()
                delay(1_000)
            }
        }
        val seconds = maxOf(0L, now.epochSecond - start.epochSecond)
        val elapsed = if (seconds >= 3600) {
            String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        } else {
            String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
        }
        Text(elapsed, style = ApexText.title3.copy(fontWeight = FontWeight.Bold), color = ApexColors.orange)
    }

    // One segment per client: "Ana · Upper body"; held, it can remove the client.
    // Two share the width, more scroll.
    @Composable
    private fun RowScope.Switcher() {
        val fits = session.participants.size <= 2
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(ApexColors.label.copy(alpha = 0.08f))
                .then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                .padding(4.dp)
        ) {
            session.participants.forEach { participant ->
                key(participant.id) {
                    Segment(participant, if (fits) Modifier.weight(1f) else Modifier.widthIn(max = 220.dp))
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Segment(participant: TrainingSession.Participant, modifier: Modifier) {
        val isActive = participant.id == session.activeId
        var showMenu by remember { mutableStateOf(false) }
        Box(modifier = modifier) {
            Row(
                modifier = Modifier
                    .testTag("training-session-segment")
                    .heightIn(min = 36.dp)
                    .clip(CircleShape)
                    .background(if (isActive) ApexColors.card else ApexColors.label.copy(alpha = 0f))
                    .combinedClickable(
                        onClick = { session = session.selecting(participant.id) },
                        onLongClick = { if (session.canRemove(participant.id) && !isSaving) showMenu = true }
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UserAvatar(participant.fullName, participant.pictureUrl, size = 22.dp)
                Text(
                    session.segmentLabel(participant, form(participant)?.trainingName.orEmpty()),
                    style = ApexText.subheadline.copy(fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (isActive) ApexColors.main else ApexColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = ApexColors.card) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_client), color = ApexColors.red) },
                    leadingIcon = { Icon(Icons.Outlined.PersonRemove, contentDescription = null, tint = ApexColors.red) },
                    onClick = {
                        showMenu = false
                        requestRemoval(participant)
                    }
                )
            }
        }
    }

    @Composable
    private fun AddClientButton(navigator: Navigator, compact: Boolean) {
        val enabled = !isAddingClient && !isSaving
        Row(
            modifier = Modifier
                .testTag("training-session-add-client")
                .heightIn(min = 36.dp)
                .clip(CircleShape)
                .background(ApexColors.main.copy(alpha = 0.12f))
                .clickable(enabled = enabled) { pickClient(navigator) }
                .padding(horizontal = if (compact) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isAddingClient) {
                SmallProgress()
            } else if (compact) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = stringResource(R.string.add_client_to_session), tint = ApexColors.main, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Filled.Add, contentDescription = null, tint = ApexColors.main, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.add_client_short), style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = ApexColors.main)
            }
        }
    }

    // Under the shown client's exercises: what the others do, a tap shows their training.
    @Composable
    private fun OthersCard(modifier: Modifier) {
        var now by remember { mutableStateOf(Instant.now()) }
        LaunchedEffect(Unit) {
            while (true) {
                now = Instant.now()
                delay(30_000)
            }
        }
        Column(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ApexColors.card)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            session.others.forEach { other ->
                Row(
                    modifier = Modifier
                        .testTag("training-session-other")
                        .fillMaxWidth()
                        .clickable { session = session.selecting(other.id) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ApexColors.orange))
                    Text(
                        otherLine(other, now),
                        style = ApexText.subheadline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ApexColors.tertiaryLabel)
                }
            }
        }
    }

    // "Marko: last set 2 min ago (Squat 3/4)", "Marko: no sets yet".
    @Composable
    private fun otherLine(other: TrainingSession.Participant, now: Instant): String {
        val lastSet = form(other)?.lastSet ?: return stringResource(R.string.training_session_no_sets, other.shortName)
        val minutes = Duration.between(lastSet.at, now).toMinutes()
        val ago = when {
            minutes < 1 -> stringResource(R.string.time_just_now)
            minutes < 60 -> stringResource(R.string.time_minutes_ago, minutes.toInt())
            else -> stringResource(R.string.time_hours_ago, (minutes / 60).toInt())
        }
        return stringResource(R.string.training_session_last_set, other.shortName, ago, lastSet.summary)
    }

    // MARK: - Clients

    private fun pickClient(navigator: Navigator) {
        navigator.push(
            ClientListScreen(R.string.add_client_to_session) { listNavigator, client ->
                listNavigator.pop()
                launch { addClient(client) }
            }
        )
    }

    private suspend fun addClient(client: Client) {
        if (session.contains(client.id)) {
            ToastManager.show(R.string.client_already_in_session, ToastType.INFO)
            return
        }
        isAddingClient = true
        try {
            // Their earlier trainings give "last time" and "repeat last training";
            // the form works without them.
            val trainings = allTrainings ?: try {
                Training.loadAll().also { allTrainings = it }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyList()
            }
            val participant = TrainingSession.Participant(client.id, client.firstName, client.lastName, client.pictureUrl)
            val added = session.adding(participant) ?: return
            forms = forms + (participant.id to makeForm(participant, trainings.filter { it.client.id == client.id }, null))
            session = added
        } finally {
            isAddingClient = false
        }
    }

    private fun requestRemoval(participant: TrainingSession.Participant) {
        val form = form(participant) ?: return
        if (!session.canRemove(participant.id) || isSaving) return
        val isPersisted = form.savedTrainingId != null || form.isQueued
        if (TrainingSession.needsConfirmationToRemove(form.enteredSets, isPersisted)) {
            removing = participant
        } else {
            launch { remove(participant) }
        }
    }

    // A training saved already (e.g. autosaved in the background) is deleted;
    // one kept on the phone is dropped.
    private suspend fun remove(participant: TrainingSession.Participant) {
        removing = null
        val form = form(participant) ?: return
        if (!session.canRemove(participant.id)) return
        val savedId = form.savedTrainingId
        if (savedId != null) {
            try {
                Training.delete(savedId)
            } catch (e: Exception) {
                showError(e)
                return
            }
        }
        if (form.isQueued) OfflineTrainingQueue.discard(form.queueKey)
        session = session.removing(participant.id) ?: return
        forms = forms - participant.id
        form.onDispose()
    }

    // MARK: - Save

    // Saves every training (again, also those saved by an earlier try, so
    // later changes aren't lost). All saved: the form closes; otherwise the
    // first one not saved is shown with its error.
    private suspend fun saveAll(navigator: Navigator) {
        if (!canSave) return
        isSaving = true
        val starting = forms.values.any { it.startsWithSession }
        try {
            val participants = session.participants
            val outcomes = coroutineScope {
                participants.map { participant -> participant.id to async { forms.getValue(participant.id).saveInSession() } }
                    .associate { (id, outcome) -> id to outcome.await() }
            }
            when (val result = session.result(outcomes)) {
                is TrainingSession.SaveAllResult.Saved -> {
                    if (result.anyQueued) {
                        ToastManager.show(R.string.offline_will_save, ToastType.WARNING)
                    } else {
                        ToastManager.show(if (starting) R.string.training_started_toast else R.string.training_saved_successfully, ToastType.SUCCESS)
                    }
                    navigator.pop()
                }
                is TrainingSession.SaveAllResult.Failed -> {
                    val first = result.ids.first()
                    val participant = session.participant(first) ?: return
                    session = session.selecting(first)
                    val message = (outcomes[first] as? TrainingSession.SaveOutcome.Failed)?.message.orEmpty()
                    ToastManager.show(
                        ApexApp.appContext.getString(R.string.training_not_saved_for, participant.shortName, message),
                        ToastType.ERROR
                    )
                }
            }
        } finally {
            isSaving = false
        }
    }
}
