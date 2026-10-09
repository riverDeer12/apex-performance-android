package software.rdd.apexperformance.ui.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.ApiException
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.LocalizedText
import software.rdd.apexperformance.model.UpdateWorkoutRequest
import software.rdd.apexperformance.model.Workout
import software.rdd.apexperformance.model.WorkoutType
import software.rdd.apexperformance.ui.components.FormSection
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.SmallProgress
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Edits a workout, or creates a new one when no workout is given.
class WorkoutEditScreen(
    // null when creating a new workout.
    private val workout: Workout? = null,
    private val onSaved: (Workout) -> Unit
) : Screen() {

    private var nameHr by mutableStateOf(workout?.name?.value("HR").orEmpty())
    private var nameEn by mutableStateOf(workout?.name?.value("EN").orEmpty())
    private var descriptionHr by mutableStateOf(workout?.description?.value("HR").orEmpty())
    private var descriptionEn by mutableStateOf(workout?.description?.value("EN").orEmpty())
    private var videoUrl by mutableStateOf(workout?.videoUrl.orEmpty())
    private var thumbnailUrl by mutableStateOf(workout?.thumbnailUrl.orEmpty())
    private var selectedWorkoutTypeIds by mutableStateOf(workout?.workoutTypes.orEmpty().map { it.id }.toSet())
    private var workoutTypes by mutableStateOf<List<WorkoutType>>(emptyList())
    private var isSaving by mutableStateOf(false)

    private val isNew: Boolean get() = workout == null

    private val isVideoUrlValid: Boolean get() = YOUTUBE_PATTERN.containsMatchIn(videoUrl.trim())

    private val isValid: Boolean
        get() {
            val trimmedNameHr = nameHr.trim()
            return trimmedNameHr.isNotEmpty() &&
                trimmedNameHr.length <= MAX_NAME_LENGTH &&
                nameEn.trim().length <= MAX_NAME_LENGTH &&
                descriptionHr.isNotBlank() &&
                // The API accepts a new workout without a video, it can be added later.
                (isVideoUrlValid || (isNew && videoUrl.isBlank()))
        }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        LaunchedEffect(Unit) { if (workoutTypes.isEmpty()) loadWorkoutTypes() }

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(if (isNew) R.string.new_workout else R.string.edit_workout)) {
                    TextButton(onClick = { launch { save(navigator) } }, enabled = isValid && !isSaving) {
                        if (isSaving) {
                            SmallProgress()
                        } else {
                            Text(
                                stringResource(R.string.save),
                                color = if (isValid) ApexColors.main else ApexColors.tertiaryLabel,
                                style = ApexText.body.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        ) {
            FormSection(header = stringResource(R.string.name)) {
                Field(nameHr, { nameHr = it }, stringResource(R.string.croatian))
                Divider()
                Field(nameEn, { nameEn = it }, stringResource(R.string.english))
            }

            FormSection(header = stringResource(R.string.description)) {
                Field(descriptionHr, { descriptionHr = it }, stringResource(R.string.croatian), multiline = true)
                Divider()
                Field(descriptionEn, { descriptionEn = it }, stringResource(R.string.english), multiline = true)
            }

            FormSection(
                header = stringResource(R.string.video_url),
                footer = if (videoUrl.isNotEmpty() && !isVideoUrlValid) {
                    { Text(stringResource(R.string.invalid_youtube_url), style = ApexText.footnote, color = ApexColors.red) }
                } else null
            ) {
                Field(videoUrl, { videoUrl = it }, stringResource(R.string.video_url), keyboardType = KeyboardType.Uri)
            }

            FormSection(
                header = stringResource(R.string.thumbnail_url),
                footer = { Text(stringResource(R.string.thumbnail_url_hint), style = ApexText.footnote, color = ApexColors.secondaryLabel) }
            ) {
                Field(thumbnailUrl, { thumbnailUrl = it }, stringResource(R.string.thumbnail_url), keyboardType = KeyboardType.Uri)
            }

            FormSection(header = stringResource(R.string.workout_types)) {
                if (workoutTypes.isEmpty()) {
                    CircularProgressIndicator(
                        color = ApexColors.main,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 10.dp)
                    )
                } else {
                    workoutTypes.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggle(type.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(type.name.localized, style = ApexText.body, modifier = Modifier.weight(1f))
                            if (type.id in selectedWorkoutTypeIds) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = ApexColors.main)
                            }
                        }
                        if (type.id != workoutTypes.last().id) Divider()
                    }
                }
            }
        }
    }

    @Composable
    private fun Divider() = HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)

    @Composable
    private fun Field(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        multiline: Boolean = false,
        keyboardType: KeyboardType = KeyboardType.Text
    ) {
        PlainTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = keyboardType == KeyboardType.Text),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )
    }

    private fun toggle(id: String) {
        selectedWorkoutTypeIds = if (id in selectedWorkoutTypeIds) selectedWorkoutTypeIds - id else selectedWorkoutTypeIds + id
    }

    // Only HR and EN are editable; other translations (e.g. IT) are kept.
    private fun localizedText(existing: LocalizedText, hr: String, en: String): LocalizedText {
        val translations = existing.translations.toMutableMap()
        translations["HR"] = hr.trim()
        val trimmedEn = en.trim()
        if (trimmedEn.isEmpty()) translations.remove("EN") else translations["EN"] = trimmedEn
        return LocalizedText(translations)
    }

    private suspend fun loadWorkoutTypes() {
        try {
            val types: List<WorkoutType> = ApiClient.get("workout-types")
            workoutTypes = types.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.localized })
        } catch (e: Exception) {
            showError(e)
        }
    }

    private suspend fun save(navigator: Navigator) {
        isSaving = true
        try {
            val request = UpdateWorkoutRequest(
                name = localizedText(workout?.name ?: LocalizedText(), nameHr, nameEn),
                description = localizedText(workout?.description ?: LocalizedText(), descriptionHr, descriptionEn),
                // An empty thumbnail is generated from the YouTube video on the API.
                thumbnailUrl = thumbnailUrl.trim(),
                videoUrl = videoUrl.trim(),
                workoutTypes = selectedWorkoutTypeIds.toList()
            )
            val saved: Workout = if (workout == null) {
                ApiClient.request("workouts", HttpMethod.POST, request)
            } else {
                ApiClient.request("workouts/${workout.id}", HttpMethod.PUT, request)
            }
            onSaved(saved)
            ToastManager.show(
                if (isNew) R.string.workout_created_successfully else R.string.workout_updated_successfully,
                ToastType.SUCCESS
            )
            navigator.pop()
        } catch (e: ApiException.Validation) {
            if (e.response.errors?.get("generalErrors")?.contains(DUPLICATE_ERROR_CODE) == true) {
                ToastManager.show(R.string.workout_duplicate, ToastType.ERROR)
            } else {
                showError(e)
            }
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }

    private companion object {
        // Same limit and link formats the web form and API accept.
        const val MAX_NAME_LENGTH = 60
        val YOUTUBE_PATTERN = Regex(
            """^https?://((www|m)\.)?(youtube\.com/(watch\?(.*&)?v=|shorts/|embed/|live/)|youtu\.be/)[\w-]+""",
            RegexOption.IGNORE_CASE
        )

        // ErrorCodes.AlreadyExists on the API.
        const val DUPLICATE_ERROR_CODE = "1300"
    }
}
