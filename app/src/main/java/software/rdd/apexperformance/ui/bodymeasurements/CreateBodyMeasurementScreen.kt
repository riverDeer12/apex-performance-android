package software.rdd.apexperformance.ui.bodymeasurements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.Client
import software.rdd.apexperformance.model.CreateBodyMeasurementRequest
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.SaveAction
import software.rdd.apexperformance.ui.components.SavingOverlay
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors

class CreateBodyMeasurementScreen(
    private val client: Client,
    private val onSuccess: (() -> Unit)? = null
) : Screen() {

    private val form = MeasurementForm()
    private var isSaving by mutableStateOf(false)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(R.string.new_body_measurement)) {
                    SaveAction(enabled = !isSaving, isSaving = isSaving, icon = Icons.Filled.Add) {
                        launch { save(navigator) }
                    }
                }
            },
            overlay = { if (isSaving) SavingOverlay() }
        ) {
            CardView(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                Column {
                    MeasurementField.entries.forEach { field ->
                        MeasurementRow(field, form, isEditable = true, verticalPadding = 16.dp)
                        if (field != MeasurementField.entries.last()) {
                            HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    private suspend fun save(navigator: Navigator) {
        isSaving = true
        try {
            val v = form.values()
            val request = CreateBodyMeasurementRequest(
                client = client.id,
                height = v.height, weight = v.weight, shoulders = v.shoulders, chest = v.chest,
                upperArm = v.upperArm, waist = v.waist, thigh = v.thigh, calves = v.calves, glutes = v.glutes
            )
            ApiClient.send("body-measurements", HttpMethod.POST, request)
            ToastManager.show(R.string.body_measurement_created_successfully, ToastType.SUCCESS)
            onSuccess?.invoke()
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }
}
