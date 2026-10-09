package software.rdd.apexperformance.ui.clients

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.Client
import software.rdd.apexperformance.model.ClientGoal
import software.rdd.apexperformance.model.ClientPlan
import software.rdd.apexperformance.model.FunctionalMovementScreen
import software.rdd.apexperformance.model.MonthlyReview
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.model.UpdateClientRequest
import software.rdd.apexperformance.ui.bodymeasurements.BodyMeasurementDetailsScreen
import software.rdd.apexperformance.ui.bodymeasurements.CreateBodyMeasurementScreen
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.EditableRow
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.IconCircle
import software.rdd.apexperformance.ui.components.IconTile
import software.rdd.apexperformance.ui.components.MenuPicker
import software.rdd.apexperformance.ui.components.NumberWheelField
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.SaveAction
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.WeightProgressChart
import software.rdd.apexperformance.ui.fms.FunctionalMovementScreenScreen
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import software.rdd.apexperformance.ui.trainings.ClientTrainingProgressScreen
import software.rdd.apexperformance.ui.trainings.TrainingFormScreen
import software.rdd.apexperformance.ui.trainings.TrainingRows
import java.time.Instant
import kotlin.math.roundToInt

class ClientDetailsScreen(private val client: Client) : Screen() {

    private var firstName by mutableStateOf(client.firstName)
    private var lastName by mutableStateOf(client.lastName)
    private var email by mutableStateOf(client.email.orEmpty())
    private var phone by mutableStateOf(client.phone.orEmpty())
    private var credits by mutableStateOf(client.credits)
    private var plan by mutableStateOf(ClientPlan.from(client.plan) ?: ClientPlan.PRIVATE_COACHING)
    private var bodyMeasurements by mutableStateOf(client.bodyMeasurements.orEmpty())
    private var isSaving by mutableStateOf(false)
    private var functionalMovementScreens by mutableStateOf<List<FunctionalMovementScreen>>(emptyList())
    private var isLoadingFunctionalMovementScreens by mutableStateOf(false)
    private var trainings by mutableStateOf<List<Training>>(emptyList())
    private var goal by mutableStateOf(ClientGoal())
    private var monthlyReviews by mutableStateOf<List<MonthlyReview>>(emptyList())
    // Lists are loaded once, then kept up to date by the screens opened from here.
    private var hasLoaded = false

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            if (!hasLoaded) {
                hasLoaded = true
                launch { loadFunctionalMovementScreens() }
                launch { loadTrainings() }
                launch { loadGoalAndReviews() }
            }
        }

        ScrollScreen(
            topBar = {
                TopBar(title = client.fullName) {
                    SaveAction(enabled = !isSaving, isSaving = isSaving) { launch { save() } }
                }
            }
        ) {
            CardView(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Filled.Person, ApexColors.secondaryLabel, size = 56.dp, background = ApexColors.systemGray5, iconSize = 26.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${client.firstName} ${client.lastName}", style = ApexText.headline)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                email.ifEmpty { stringResource(R.string.no_email) },
                                style = ApexText.subheadline,
                                color = ApexColors.secondaryLabel
                            )
                            if (email.isNotEmpty()) CopyButton { copy(context, email, R.string.email_copied) }
                        }
                    }
                }
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.personal_info)) {
                Column {
                    EditableRow(stringResource(R.string.first_name)) {
                        RowField(firstName, { firstName = it }, stringResource(R.string.first_name))
                    }
                    HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                    EditableRow(stringResource(R.string.last_name)) {
                        RowField(lastName, { lastName = it }, stringResource(R.string.last_name))
                    }
                }
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.contact)) {
                Column {
                    EditableRow(stringResource(R.string.email)) {
                        RowField(email, { email = it }, stringResource(R.string.email), KeyboardType.Email)
                        if (email.isNotEmpty()) CopyButton { copy(context, email, R.string.email_copied) }
                    }
                    HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                    EditableRow(stringResource(R.string.mobile_phone)) {
                        RowField(phone, { phone = it }, stringResource(R.string.mobile_phone), KeyboardType.Phone)
                        if (phone.isNotEmpty()) CopyButton { copy(context, phone, R.string.phone_copied) }
                    }
                }
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.plan)) {
                MenuPicker(
                    label = stringResource(R.string.plan),
                    selectedText = stringResource(plan.title),
                    options = ClientPlan.entries,
                    optionText = { stringResource(it.title) },
                    onSelect = { plan = it }
                )
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.credits)) {
                Column {
                    EditableRow(stringResource(R.string.appointments_left)) {
                        NumberWheelField(
                            value = credits?.toDouble(),
                            onValueChange = { credits = it?.roundToInt() },
                            range = 0..200,
                            title = stringResource(R.string.appointments_left)
                        )
                    }
                    HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                    EditableRow(stringResource(R.string.last_payment)) {
                        Text(
                            DateFormats.dateAndTime(client.lastCreditsIncrease ?: Instant.now()),
                            style = ApexText.body
                        )
                    }
                }
            }

            if (bodyMeasurements.isNotEmpty()) {
                WeightProgressChart(bodyMeasurements, modifier = Modifier.padding(horizontal = 20.dp))
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                CardHeader(stringResource(R.string.body_measurements)) {
                    navigator.push(CreateBodyMeasurementScreen(client, onSuccess = { launch { refreshMeasurements() } }))
                }
                if (bodyMeasurements.isEmpty()) {
                    EmptyText(stringResource(R.string.no_measurements))
                } else {
                    val measurements = bodyMeasurements.sortedByDescending { it.measuredAt }
                    Column {
                        measurements.forEach { measurement ->
                            SettingsRow(
                                icon = Icons.Outlined.Straighten,
                                iconTint = ApexColors.blue,
                                title = DateFormats.dateAndTime(measurement.measuredAt),
                                showChevron = true,
                                onClick = {
                                    navigator.push(
                                        BodyMeasurementDetailsScreen(
                                            measurement,
                                            onSaved = { launch { refreshMeasurements() } },
                                            onDeleted = { id -> bodyMeasurements = bodyMeasurements.filterNot { it.id == id } }
                                        )
                                    )
                                }
                            )
                            if (measurement.id != measurements.last().id) RowDivider()
                        }
                    }
                }
            }

            FunctionalMovementScreensCard()

            GoalCard()

            MonthlyReviewsCard()

            TrainingsCard()
        }
    }

    @Composable
    private fun FunctionalMovementScreensCard() {
        val navigator = LocalNavigator.current

        CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
            CardHeader(stringResource(R.string.fms)) {
                navigator.push(FunctionalMovementScreenScreen(client.id, onSaved = { launch { loadFunctionalMovementScreens() } }))
            }
            when {
                isLoadingFunctionalMovementScreens && functionalMovementScreens.isEmpty() ->
                    CircularProgressIndicator(
                        color = ApexColors.main,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 6.dp)
                    )
                functionalMovementScreens.isEmpty() -> EmptyText(stringResource(R.string.no_fms))
                else -> Column {
                    functionalMovementScreens.forEach { screen ->
                        SettingsRow(
                            icon = Icons.Outlined.AccessibilityNew,
                            iconTint = ApexColors.blue,
                            title = DateFormats.dateAndTime(screen.createdAt),
                            showChevron = true,
                            onClick = {
                                navigator.push(
                                    FunctionalMovementScreenScreen(client.id, screen, onSaved = { launch { loadFunctionalMovementScreens() } })
                                )
                            }
                        )
                        if (screen.id != functionalMovementScreens.last().id) RowDivider()
                    }
                }
            }
        }
    }

    @Composable
    private fun GoalCard() {
        val navigator = LocalNavigator.current

        CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
            CardHeader(
                stringResource(R.string.goal_and_plan),
                icon = Icons.Filled.Edit,
                iconSize = 20.dp,
                contentDescription = stringResource(R.string.edit_goal_and_plan)
            ) {
                navigator.push(ClientGoalFormScreen(client.id, goal, onSaved = { saved -> goal = saved }))
            }

            if (goal.isEmpty) {
                EmptyText(stringResource(R.string.no_goal_and_plan))
            } else {
                ClientGoalContent(goal)
            }
        }
    }

    @Composable
    private fun MonthlyReviewsCard() {
        val navigator = LocalNavigator.current

        // A review opened for editing, or a new one when none is given.
        fun openReview(review: MonthlyReview?) {
            navigator.push(
                MonthlyReviewFormScreen(client.id, monthlyReviews, review, onChanged = { launch { loadGoalAndReviews() } })
            )
        }

        CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
            CardHeader(
                stringResource(R.string.monthly_reviews),
                contentDescription = stringResource(R.string.new_monthly_review)
            ) { openReview(null) }

            if (monthlyReviews.isEmpty()) {
                EmptyText(stringResource(R.string.no_monthly_reviews))
            } else {
                Column {
                    monthlyReviews.forEach { review ->
                        ReviewRow(review) { openReview(review) }
                        if (review.id != monthlyReviews.last().id) RowDivider()
                    }
                }
            }
        }
    }

    // Same as SettingsRow, with the review text cut to two lines.
    @Composable
    private fun ReviewRow(review: MonthlyReview, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(Icons.Outlined.Description, ApexColors.accent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    monthYearTitle(review.monthDate),
                    style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = ApexColors.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    review.content,
                    style = ApexText.caption,
                    color = ApexColors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Chevron()
        }
    }

    @Composable
    private fun TrainingsCard() {
        val navigator = LocalNavigator.current

        CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
            CardHeader(
                stringResource(R.string.trainings),
                contentDescription = stringResource(R.string.new_training)
            ) {
                navigator.push(TrainingFormScreen(client.id, history = trainings, onSaved = { upsertTraining(it) }))
            }

            if (trainings.isEmpty()) {
                EmptyText(stringResource(R.string.no_trainings))
            } else {
                if (trainings.any { it.isCompleted }) {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        iconTint = ApexColors.accent,
                        title = stringResource(R.string.training_progress_title),
                        showChevron = true,
                        onClick = { navigator.push(ClientTrainingProgressScreen(client.fullName, trainings)) }
                    )
                    RowDivider()
                }

                TrainingRows(
                    trainings = trainings,
                    onSaved = { upsertTraining(it) },
                    onDeleted = { id -> trainings = trainings.filterNot { it.id == id } }
                )
            }
        }
    }

    @Composable
    private fun RowScope.RowField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        keyboardType: KeyboardType = KeyboardType.Text
    ) {
        PlainTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            textAlign = TextAlign.End,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = keyboardType == KeyboardType.Text),
            modifier = Modifier.weight(1f)
        )
    }

    // Card title with a round button on the right ("+" by default).
    @Composable
    private fun CardHeader(
        title: String,
        icon: ImageVector = Icons.Filled.Add,
        iconSize: Dp = 24.dp,
        contentDescription: String? = null,
        onClick: () -> Unit
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = ApexText.headline, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ApexColors.systemGray6)
            ) {
                Icon(icon, contentDescription = contentDescription, tint = ApexColors.main, modifier = Modifier.size(iconSize))
            }
        }
    }

    @Composable
    private fun CopyButton(onClick: () -> Unit) {
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.copy),
                tint = ApexColors.secondaryLabel,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    private fun copy(context: Context, text: String, message: Int) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
        ToastManager.show(message, ToastType.SUCCESS)
    }

    private suspend fun loadGoalAndReviews() {
        try {
            goal = ClientGoal.load(client.id)
            monthlyReviews = MonthlyReview.load(client.id)
        } catch (e: Exception) {
            showError(e)
        }
    }

    // API returns the trainings of all the coach's clients,
    // so only this client's are kept, newest first.
    private suspend fun loadTrainings() {
        try {
            trainings = Training.loadAll().filter { it.client.id == client.id }
        } catch (e: Exception) {
            showError(e)
        }
    }

    private fun upsertTraining(training: Training) {
        trainings = (trainings.filterNot { it.id == training.id } + training).sortedByDescending { it.date }
    }

    // API returns all FMS the logged user can see,
    // so only this client's are kept, newest first.
    private suspend fun loadFunctionalMovementScreens() {
        isLoadingFunctionalMovementScreens = true
        try {
            val screens: List<FunctionalMovementScreen> = ApiClient.get("functional-movement-screens")
            functionalMovementScreens = screens
                .filter { it.client.id == client.id }
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoadingFunctionalMovementScreens = false
        }
    }

    // There is no endpoint for one client, so the list is reloaded to
    // show measurements added or changed from this screen.
    private suspend fun refreshMeasurements() {
        try {
            val clients: List<Client> = ApiClient.get("clients")
            clients.firstOrNull { it.id == client.id }?.let { bodyMeasurements = it.bodyMeasurements.orEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keeps the measurements already shown.
        }
    }

    private suspend fun save() {
        isSaving = true
        try {
            val request = UpdateClientRequest(
                firstName = firstName,
                lastName = lastName,
                email = email.ifEmpty { null },
                phone = phone.ifEmpty { null },
                credits = credits ?: 0,
                plan = plan.value
            )
            ApiClient.send("clients/${client.id}", HttpMethod.PUT, request)
            ToastManager.show(R.string.successfully_updated_user, ToastType.SUCCESS)
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }
}
