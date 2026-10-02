package software.rdd.apexperformance.ui.bodymeasurements

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.util.formatNumber
import software.rdd.apexperformance.model.BodyMeasurement
import software.rdd.apexperformance.model.BodyMeasurementValues
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// The nine measurements in the order the iOS app shows them.
enum class MeasurementField(@StringRes val title: Int, val unit: String) {
    HEIGHT(R.string.height, "cm"),
    WEIGHT(R.string.weight, "kg"),
    SHOULDERS(R.string.shoulders, "cm"),
    CHEST(R.string.chest, "cm"),
    UPPER_ARM(R.string.upper_arm, "cm"),
    WAIST(R.string.waist, "cm"),
    THIGH(R.string.thigh, "cm"),
    CALVES(R.string.calves, "cm"),
    GLUTES(R.string.glutes, "cm");

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

// Text of each field while it is edited, so "80," can be typed on the way to "80,5".
class MeasurementForm(initial: BodyMeasurement? = null) {
    private val texts = mutableStateMapOf<MeasurementField, String>().apply {
        MeasurementField.entries.forEach { field ->
            put(field, initial?.let { formatNumber(field.value(it)) } ?: "0")
        }
    }

    fun text(field: MeasurementField): String = texts[field].orEmpty()

    fun update(field: MeasurementField, text: String) {
        texts[field] = text.filter { it.isDigit() || it == '.' || it == ',' }
    }

    // Both decimal separators are accepted (Croatian keyboards use a comma).
    fun number(field: MeasurementField): Double =
        text(field).replace(',', '.').toDoubleOrNull() ?: 0.0

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
    verticalPadding: Dp = 10.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(field.title), style = ApexText.body, color = ApexColors.secondaryLabel)
        Spacer(Modifier.weight(1f))
        if (isEditable) {
            PlainTextField(
                value = form.text(field),
                onValueChange = { form.update(field, it) },
                placeholder = "",
                textAlign = TextAlign.End,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.widthIn(min = 60.dp, max = 120.dp)
            )
        } else {
            Text(form.text(field), style = ApexText.body.copy(fontWeight = FontWeight.SemiBold))
        }
        Text(field.unit, style = ApexText.subheadline, color = ApexColors.secondaryLabel)
    }
}
