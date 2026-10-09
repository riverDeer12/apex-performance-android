package software.rdd.apexperformance.ui.fms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.FunctionalMovementScreen
import software.rdd.apexperformance.model.FunctionalMovementScreenRequest
import software.rdd.apexperformance.ui.components.FormSection
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.SaveAction
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Creates a new FMS for the client when `screen` is null,
// otherwise shows the existing one and lets staff edit it.
class FunctionalMovementScreenScreen(
    private val clientId: String,
    private val screen: FunctionalMovementScreen? = null,
    private val onSaved: (() -> Unit)? = null
) : Screen() {

    private var deepSquat by mutableStateOf(screen?.deepSquat.orEmpty())
    private var hurdleStep by mutableStateOf(screen?.hurdleStep.orEmpty())
    private var inLineLunge by mutableStateOf(screen?.inLineLunge.orEmpty())
    private var activeStraightLegRaise by mutableStateOf(screen?.activeStraightLegRaise.orEmpty())
    private var trunkStabilityPushUp by mutableStateOf(screen?.trunkStabilityPushUp.orEmpty())
    private var rotaryStability by mutableStateOf(screen?.rotaryStability.orEmpty())
    private var shoulderMobility by mutableStateOf(screen?.shoulderMobility.orEmpty())
    private var xTest by mutableStateOf(screen?.xTest.orEmpty())
    private var descriptionText by mutableStateOf(screen?.description.orEmpty())
    private var isSaving by mutableStateOf(false)

    private val isNew: Boolean get() = screen == null

    // API requires a result for every test, description is optional.
    private val isValid: Boolean
        get() = listOf(
            deepSquat, hurdleStep, inLineLunge, activeStraightLegRaise,
            trunkStabilityPushUp, rotaryStability, shoulderMobility, xTest
        ).all { it.isNotBlank() } &&
            xTest.trim().length <= X_TEST_MAX_LENGTH &&
            descriptionText.trim().length <= DESCRIPTION_MAX_LENGTH

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(if (isNew) R.string.new_fms else R.string.fms)) {
                    SaveAction(enabled = isValid && !isSaving, isSaving = isSaving) { launch { save(navigator) } }
                }
            }
        ) {
            screen?.let {
                FormSection {
                    Text(
                        DateFormats.dateAndTime(it.createdAt),
                        style = ApexText.body,
                        color = ApexColors.secondaryLabel,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }

            TestSection(stringResource(R.string.deep_squat), deepSquat) { deepSquat = it }
            TestSection(stringResource(R.string.hurdle_step), hurdleStep) { hurdleStep = it }
            TestSection(stringResource(R.string.in_line_lunge), inLineLunge) { inLineLunge = it }
            TestSection(stringResource(R.string.active_straight_leg_raise), activeStraightLegRaise) { activeStraightLegRaise = it }
            TestSection(stringResource(R.string.trunk_stability_push_up), trunkStabilityPushUp) { trunkStabilityPushUp = it }
            TestSection(stringResource(R.string.rotary_stability), rotaryStability) { rotaryStability = it }
            TestSection(stringResource(R.string.shoulder_mobility), shoulderMobility) { shoulderMobility = it }

            FormSection(
                header = stringResource(R.string.x_test),
                footer = if (xTest.trim().length > X_TEST_MAX_LENGTH) {
                    { Text(stringResource(R.string.fms_max_length_50), style = ApexText.footnote, color = ApexColors.red) }
                } else null
            ) {
                Field(xTest, { xTest = it }, stringResource(R.string.x_test), minLines = 1)
            }

            FormSection(header = stringResource(R.string.description)) {
                Field(descriptionText, { descriptionText = it }, stringResource(R.string.description), minLines = 3)
            }
        }
    }

    @Composable
    private fun TestSection(title: String, value: String, onValueChange: (String) -> Unit) {
        FormSection(header = title) {
            Field(value, onValueChange, title, minLines = 2)
        }
    }

    @Composable
    private fun Field(value: String, onValueChange: (String) -> Unit, placeholder: String, minLines: Int) {
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

    private suspend fun save(navigator: Navigator) {
        isSaving = true
        try {
            val request = FunctionalMovementScreenRequest(
                client = clientId,
                deepSquat = deepSquat.trim(),
                hurdleStep = hurdleStep.trim(),
                inLineLunge = inLineLunge.trim(),
                activeStraightLegRaise = activeStraightLegRaise.trim(),
                trunkStabilityPushUp = trunkStabilityPushUp.trim(),
                rotaryStability = rotaryStability.trim(),
                shoulderMobility = shoulderMobility.trim(),
                xTest = xTest.trim(),
                description = descriptionText.trim().ifEmpty { null }
            )

            // API answers with a status code only, so the body is not decoded.
            if (screen == null) {
                ApiClient.send("functional-movement-screens", HttpMethod.POST, request)
            } else {
                ApiClient.send("functional-movement-screens/${screen.id}", HttpMethod.PUT, request)
            }

            ToastManager.show(
                if (isNew) R.string.fms_created_successfully else R.string.fms_updated_successfully,
                ToastType.SUCCESS
            )
            onSaved?.invoke()
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }

    private companion object {
        // Same limits as the API.
        const val X_TEST_MAX_LENGTH = 50
        const val DESCRIPTION_MAX_LENGTH = 2000
    }
}
