package software.rdd.apexperformance.ui.bodymeasurements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.formatNumber
import software.rdd.apexperformance.model.BodyMeasurement
import software.rdd.apexperformance.model.BodyMeasurementResponse
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.IconCircle
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScreenHeader
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SectionTitle
import software.rdd.apexperformance.ui.components.StatTile
import software.rdd.apexperformance.ui.components.WeightProgressChart
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Client's own measurements, newest first.
class MyBodyMeasurementsScreen : Screen() {

    private var measurements by mutableStateOf<List<BodyMeasurement>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var hasLoaded = false

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        LaunchedEffect(Unit) {
            if (!hasLoaded) {
                hasLoaded = true
                loadMeasurements()
            }
        }

        ScrollScreen(
            showLoading = isLoading && measurements.isEmpty(),
            onRefresh = { loadMeasurements() }
        ) {
            ScreenHeader(stringResource(R.string.body_measurements), stringResource(R.string.my_measurements_subtitle))

            measurements.firstOrNull()?.let { latest ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.latest_measurement))
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile(DateFormats.date(latest.measuredAt), stringResource(R.string.date), Modifier.weight(1f))
                            StatTile(formatted(latest.weight, "kg"), stringResource(R.string.weight), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile(formatted(latest.waist, "cm"), stringResource(R.string.waist), Modifier.weight(1f))
                            StatTile(formatted(latest.chest, "cm"), stringResource(R.string.chest), Modifier.weight(1f))
                        }
                    }
                }
            }

            if (measurements.isNotEmpty()) {
                WeightProgressChart(measurements, modifier = Modifier.padding(horizontal = 20.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.measurement_history))
                CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                    if (measurements.isEmpty() && !isLoading) {
                        EmptyText(stringResource(R.string.no_measurements))
                    } else {
                        Column {
                            measurements.forEachIndexed { index, measurement ->
                                MeasurementHistoryRow(measurement, previous = measurements.getOrNull(index + 1)) {
                                    navigator.push(BodyMeasurementDetailsScreen(measurement, isEditable = false))
                                }
                                if (measurement.id != measurements.last().id) RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MeasurementHistoryRow(measurement: BodyMeasurement, previous: BodyMeasurement?, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconCircle(Icons.Outlined.Straighten, ApexColors.main, iconSize = 18.dp)
            Text(DateFormats.date(measurement.measuredAt), style = ApexText.subheadline, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatted(measurement.weight, "kg"), style = ApexText.body.copy(fontWeight = FontWeight.Bold))
                if (previous != null) {
                    Text(weightChange(previous.weight, measurement.weight), style = ApexText.caption, color = ApexColors.secondaryLabel)
                }
            }
            Chevron()
        }
    }

    private fun formatted(value: Double, unit: String) = "${formatNumber(value)} $unit"

    // "+0.5 kg" / "-1.2 kg", nothing in front of zero.
    private fun weightChange(previous: Double, current: Double): String {
        val change = current - previous
        val sign = if (change > 0.05) "+" else ""
        return "$sign${formatNumber(change)} kg"
    }

    private suspend fun loadMeasurements() {
        isLoading = true
        try {
            val response: List<BodyMeasurementResponse> = ApiClient.get("body-measurements")
            measurements = response.map { it.toBodyMeasurement() }.sortedByDescending { it.measuredAt }
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }
    }
}
