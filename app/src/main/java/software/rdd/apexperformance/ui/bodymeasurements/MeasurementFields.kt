package software.rdd.apexperformance.ui.bodymeasurements

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.model.BodyMeasurement
import software.rdd.apexperformance.model.BodyMeasurementValues
import software.rdd.apexperformance.model.measurementWheel
import software.rdd.apexperformance.ui.components.NumberWheelField
import software.rdd.apexperformance.ui.components.formatWheelNumber
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// The nine measurements in the order the iOS app shows them. The key
// picks the wheel range (measurementWheel), same as the iOS title key.
enum class MeasurementField(val key: String, @StringRes val title: Int, val unit: String) {
    HEIGHT("height", R.string.height, "cm"),
    WEIGHT("weight", R.string.weight, "kg"),
    SHOULDERS("shoulders", R.string.shoulders, "cm"),
    CHEST("chest", R.string.chest, "cm"),
    UPPER_ARM("upper_arm", R.string.upper_arm, "cm"),
    WAIST("waist", R.string.waist, "cm"),
    THIGH("thigh", R.string.thigh, "cm"),
    CALVES("calves", R.string.calves, "cm"),
    GLUTES("glutes", R.string.glutes, "cm");

    fun value(m: BodyMeasurement): Double = when (this) {
        HEIGHT -> m.height
        WEIGHT -> m.weight
        SHOULDERS -> m.shoulders
        CHEST -> m.chest
        UPPER_ARM -> m.upperArm
        WAIST -> m.waist
        THIGH -> m.thigh
        CALVES -> m.calves
        GLUTES -> m.glutes
    }
}

// Values of the fields; 0 means not measured and shows as empty on the wheel field.
class MeasurementForm(initial: BodyMeasurement? = null) {
    private val numbers = mutableStateMapOf<MeasurementField, Double>().apply {
        MeasurementField.entries.forEach { field ->
            put(field, initial?.let { field.value(it) } ?: 0.0)
        }
    }

    fun number(field: MeasurementField): Double = numbers[field] ?: 0.0

    fun update(field: MeasurementField, value: Double) {
        numbers[field] = value
    }

    fun values() = BodyMeasurementValues(
        height = number(MeasurementField.HEIGHT),
        weight = number(MeasurementField.WEIGHT),
        shoulders = number(MeasurementField.SHOULDERS),
        chest = number(MeasurementField.CHEST),
        upperArm = number(MeasurementField.UPPER_ARM),
        waist = number(MeasurementField.WAIST),
        thigh = number(MeasurementField.THIGH),
        calves = number(MeasurementField.CALVES),
        glutes = number(MeasurementField.GLUTES)
    )
}

@Composable
fun MeasurementRow(
    field: MeasurementField,
    form: MeasurementForm,
    isEditable: Boolean,
    verticalPadding: Dp = 10.dp,
    horizontalPadding: Dp = 0.dp
) {
    val title = stringResource(field.title)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding, horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = ApexText.body, color = ApexColors.secondaryLabel)
        Spacer(Modifier.weight(1f))
        if (isEditable) {
            val wheel = measurementWheel(field.key)
            // 0 is shown as empty, so the wheel starts at a typical value.
            NumberWheelField(
                value = form.number(field).takeIf { it != 0.0 },
                onValueChange = { form.update(field, it ?: 0.0) },
                range = wheel.range,
                step = 0.1,
                defaultValue = wheel.start,
                title = title
            )
        } else {
            Text(
                formatWheelNumber(form.number(field), 0.1),
                style = ApexText.body.copy(fontWeight = FontWeight.SemiBold)
            )
        }
        Text(field.unit, style = ApexText.subheadline, color = ApexColors.secondaryLabel)
    }
}
