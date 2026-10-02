package software.rdd.apexperformance.ui.bodymeasurements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
import software.rdd.apexperformance.model.BodyMeasurement
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.SaveAction
import software.rdd.apexperformance.ui.components.ScreenHeader
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors

// Staff can edit a client's measurement; clients only see their own.
class BodyMeasurementDetailsScreen(
    private val bodyMeasurement: BodyMeasurement,
    private val isEditable: Boolean = true,
    private val onSaved: (() -> Unit)? = null
) : Screen() {

    private val form = MeasurementForm(bodyMeasurement)
    private var isSaving by mutableStateOf(false)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(R.string.measurements)) {
                    if (isEditable) {
                        SaveAction(enabled = !isSaving, isSaving = isSaving) { launch { update(navigator) } }
                    }
                }
            }
        ) {
            ScreenHeader(stringResource(R.string.body_measurements), DateFormats.dateAndTime(bodyMeasurement.measuredAt))

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.measurements)) {
                Column {
                    MeasurementField.entries.forEach { field ->
                        MeasurementRow(field, form, isEditable)
                        if (field != MeasurementField.entries.last()) {
                            HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    private suspend fun update(navigator: Navigator) {
        isSaving = true
        try {
            ApiClient.send("body-measurements/${bodyMeasurement.id}", HttpMethod.PUT, form.values())
            ToastManager.show(R.string.body_measurement_updated_successfully, ToastType.SUCCESS)
            onSaved?.invoke()
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }
}
