package software.rdd.apexperformance.ui.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import software.rdd.apexperformance.model.ClientGoal
import software.rdd.apexperformance.model.MonthlyReview
import software.rdd.apexperformance.ui.components.ApexLabel
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import software.rdd.apexperformance.ui.components.ApexScreenHeader
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.FormSection
import software.rdd.apexperformance.ui.components.MenuPicker
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.SaveAction
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.ToggleRow
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

// MARK: - Goal and plan

// Sections of the client's goal and plan: goal, current block,
// focus and next assessment. Empty sections are hidden.
@Composable
fun ClientGoalContent(goal: ClientGoal, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GoalSection(stringResource(R.string.goal), Icons.Filled.GpsFixed, goal.goal)
        GoalSection(stringResource(R.string.current_block), Icons.Outlined.CalendarMonth, goal.currentBlock)
        GoalSection(stringResource(R.string.focus), Icons.Outlined.TrackChanges, goal.focus)

        if (!goal.nextAssessment.isNullOrEmpty() || goal.nextAssessmentDate != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GoalLabel(stringResource(R.string.next_assessment), Icons.Outlined.Verified)
                goal.nextAssessmentDate?.let {
                    Text(DateFormats.date(it), style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold))
                }
                goal.nextAssessment?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = ApexText.subheadline)
                }
            }
        }
    }
}

@Composable
private fun GoalSection(title: String, icon: ImageVector, text: String?) {
    if (text.isNullOrEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GoalLabel(title, icon)
        Text(text, style = ApexText.subheadline)
    }
}

// Icon and uppercase label, like Label(...).apexLabel() on iOS.
@Composable
private fun GoalLabel(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = ApexColors.secondaryLabel, modifier = Modifier.size(13.dp))
        ApexLabel(title)
    }
}

// Coach writes the client's goal and plan.
class ClientGoalFormScreen(
    private val clientId: String,
    goal: ClientGoal,
    private val onSaved: ((ClientGoal) -> Unit)? = null
) : Screen() {

    private var goalText by mutableStateOf(goal.goal.orEmpty())
    private var currentBlock by mutableStateOf(goal.currentBlock.orEmpty())
    private var focus by mutableStateOf(goal.focus.orEmpty())
    private var nextAssessment by mutableStateOf(goal.nextAssessment.orEmpty())
    private var hasNextAssessmentDate by mutableStateOf(goal.nextAssessmentDate != null)
    private var nextAssessmentDate by mutableStateOf(goal.nextAssessmentDate ?: Instant.now())
    private var isSaving by mutableStateOf(false)
    private var showDatePicker by mutableStateOf(false)

    private val isValid: Boolean
        get() = goalText.length <= 1000 && currentBlock.length <= 1000 &&
            focus.length <= 1000 && nextAssessment.length <= 500

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(R.string.goal_and_plan)) {
                    SaveAction(enabled = !isSaving && isValid, isSaving = isSaving) { launch { save(navigator) } }
                }
            }
        ) {
            FormSection(header = stringResource(R.string.goal)) {
                FormTextField(goalText, { goalText = it }, stringResource(R.string.goal_placeholder), minLines = 2)
            }
            FormSection(header = stringResource(R.string.current_block)) {
                FormTextField(currentBlock, { currentBlock = it }, stringResource(R.string.current_block_placeholder), minLines = 2)
            }
            FormSection(header = stringResource(R.string.focus)) {
                FormTextField(focus, { focus = it }, stringResource(R.string.focus_placeholder), minLines = 2)
            }
            FormSection(header = stringResource(R.string.next_assessment)) {
                ToggleRow(stringResource(R.string.next_assessment_date), hasNextAssessmentDate) {
                    hasNextAssessmentDate = it
                }
                if (hasNextAssessmentDate) {
                    FormDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.date), style = ApexText.body, modifier = Modifier.weight(1f))
                        // Compact date button, like DatePicker(displayedComponents: .date) on iOS.
                        Text(
                            DateFormats.date(nextAssessmentDate),
                            style = ApexText.body,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ApexColors.systemGray5)
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                FormDivider()
                FormTextField(nextAssessment, { nextAssessment = it }, stringResource(R.string.next_assessment_placeholder), minLines = 1)
            }
        }

        if (showDatePicker) {
            val zone = DateFormats.zone
            val current = nextAssessmentDate.atZone(zone)
            val state = rememberDatePickerState(
                initialSelectedDateMillis = current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        showDatePicker = false
                        state.selectedDateMillis?.let { millis ->
                            // Keeps the time of day, only the day changes.
                            val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            nextAssessmentDate = day.atTime(current.toLocalTime()).atZone(zone).toInstant()
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

    private suspend fun save(navigator: Navigator) {
        isSaving = true
        try {
            val request = ClientGoal(
                goal = clean(goalText),
                currentBlock = clean(currentBlock),
                focus = clean(focus),
                nextAssessment = clean(nextAssessment),
                nextAssessmentDate = if (hasNextAssessmentDate) nextAssessmentDate else null
            )
            val saved = request.save(clientId)
            onSaved?.invoke(saved)
            ToastManager.show(R.string.goal_saved_successfully, ToastType.SUCCESS)
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }

    private fun clean(value: String): String? = value.trim().ifEmpty { null }
}

// Client's goal and plan on its own screen, opened from home.
class ClientGoalDetailScreen(private val goal: ClientGoal) : Screen() {

    @Composable
    override fun Content() {
        ScrollScreen {
            ApexScreenHeader(title = stringResource(R.string.my_goal_and_plan), showsWordmark = false)

            ClientGoalContent(
                goal,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .apexCard()
                    .padding(16.dp)
            )

            goal.updatedAt?.let {
                Text(
                    stringResource(R.string.updated_on, DateFormats.date(it)),
                    style = ApexText.caption,
                    color = ApexColors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

// Shown to the client after login when the coach changed the goal and plan.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientGoalSheet(goal: ClientGoal, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ApexColors.groupedBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ApexScreenHeader(
                title = stringResource(R.string.my_goal_and_plan),
                subtitle = stringResource(R.string.my_goal_and_plan_subtitle)
            )

            ClientGoalContent(
                goal,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .apexCard()
                    .padding(16.dp)
            )

            ApexPrimaryButton(
                text = stringResource(R.string.got_it),
                icon = Icons.Filled.Check,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
        }
    }
}

// MARK: - Monthly reviews

// Month name and year of a review, e.g. "Listopad 2026".
fun monthYearTitle(date: LocalDate): String {
    val locale = Locale.getDefault()
    return "${monthName(date.monthValue, locale)} ${date.year}"
}

private fun monthName(month: Int, locale: Locale = Locale.getDefault()): String =
    Month.of(month).getDisplayName(DateTextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { it.titlecase(locale) }

// Month name and year in capitals, e.g. "LISTOPAD 2026".
@Composable
fun MonthTitle(date: LocalDate, modifier: Modifier = Modifier, style: TextStyle = ApexText.body) {
    Text(monthYearTitle(date).uppercase(), style = style, modifier = modifier)
}

// Client's monthly reviews, newest first.
class MonthlyReviewsScreen(private val reviews: List<MonthlyReview>) : Screen() {

    @Composable
    override fun Content() {
        ScrollScreen {
            ApexScreenHeader(
                title = stringResource(R.string.monthly_reviews),
                subtitle = stringResource(R.string.monthly_reviews_subtitle)
            )

            if (reviews.isEmpty()) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        stringResource(R.string.no_monthly_reviews),
                        style = ApexText.body,
                        color = ApexColors.secondaryLabel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            reviews.forEach { review ->
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .apexCard()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonthTitle(
                        review.monthDate,
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    )
                    Text(review.content, style = ApexText.subheadline)
                }
            }
        }
    }
}

// Coach writes or edits the review of a client's month.
class MonthlyReviewFormScreen(
    private val clientId: String,
    // Client's existing reviews, the form loads the one of the chosen month.
    private val reviews: List<MonthlyReview>,
    review: MonthlyReview? = null,
    private val onChanged: (() -> Unit)? = null
) : Screen() {

    // A new review is for the current month, written before the 1st.
    private var year by mutableIntStateOf(review?.year ?: LocalDate.now().year)
    private var month by mutableIntStateOf(review?.month ?: LocalDate.now().monthValue)
    private var content by mutableStateOf(review?.content.orEmpty())
    private var isSaving by mutableStateOf(false)
    private var showDeleteDialog by mutableStateOf(false)

    private val existingReview: MonthlyReview?
        get() = reviews.firstOrNull { it.year == year && it.month == month }

    private val trimmedContent: String get() = content.trim()

    private val years: List<Int>
        get() {
            val current = LocalDate.now().year
            return setOf(current - 1, current, current + 1, year).sorted()
        }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(R.string.monthly_review)) {
                    SaveAction(
                        enabled = !isSaving && trimmedContent.isNotEmpty() && content.length <= MAX_LENGTH,
                        isSaving = isSaving
                    ) { launch { save(navigator) } }
                }
            }
        ) {
            FormSection {
                MenuPicker(
                    label = stringResource(R.string.month),
                    selectedText = monthName(month),
                    options = (1..12).toList(),
                    optionText = { monthName(it) },
                    onSelect = { select(year, it) }
                )
                FormDivider()
                MenuPicker(
                    label = stringResource(R.string.year),
                    selectedText = year.toString(),
                    options = years,
                    optionText = { it.toString() },
                    onSelect = { select(it, month) }
                )
            }

            FormSection(
                footer = {
                    Text("${content.length} / $MAX_LENGTH", style = ApexText.footnote, color = ApexColors.secondaryLabel)
                }
            ) {
                FormTextField(content, { content = it }, stringResource(R.string.monthly_review_placeholder), minLines = 8)
            }

            existingReview?.let { existing ->
                FormSection(
                    footer = {
                        Text(
                            stringResource(R.string.monthly_review_exists, DateFormats.date(existing.updatedAt)),
                            style = ApexText.footnote,
                            color = ApexColors.secondaryLabel
                        )
                    }
                ) {
                    Text(
                        stringResource(R.string.delete_monthly_review),
                        style = ApexText.body,
                        color = if (isSaving) ApexColors.tertiaryLabel else ApexColors.red,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSaving) { showDeleteDialog = true }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }

        if (showDeleteDialog) {
            ConfirmDialog(
                title = stringResource(R.string.delete_monthly_review_question),
                message = null,
                confirmText = stringResource(R.string.delete),
                onConfirm = { launch { delete(navigator) } },
                onDismiss = { showDeleteDialog = false }
            )
        }
    }

    // Switching the month shows that month's review, if written.
    private fun select(newYear: Int, newMonth: Int) {
        if (newYear == year && newMonth == month) return
        year = newYear
        month = newMonth
        content = existingReview?.content.orEmpty()
    }

    private suspend fun save(navigator: Navigator) {
        isSaving = true
        try {
            MonthlyReview.save(clientId, year, month, trimmedContent)
            onChanged?.invoke()
            ToastManager.show(R.string.monthly_review_saved_successfully, ToastType.SUCCESS)
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }

    private suspend fun delete(navigator: Navigator) {
        val existing = existingReview ?: return
        isSaving = true
        try {
            MonthlyReview.delete(existing.id)
            onChanged?.invoke()
            ToastManager.show(R.string.monthly_review_deleted_successfully, ToastType.SUCCESS)
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }

    private companion object {
        // Same limit as the API.
        const val MAX_LENGTH = 4000
    }
}

// MARK: - Form helpers

// Multi-line field inside a form section, like TextField(axis: .vertical) on iOS.
@Composable
private fun FormTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, minLines: Int) {
    PlainTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        singleLine = false,
        minLines = minLines,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    )
}

@Composable
private fun FormDivider() {
    HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
}
