package software.rdd.apexperformance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.text.DecimalFormatSymbols
import kotlin.math.floor
import kotlin.math.roundToInt

// Number shown like a field; tapping it opens wheel pickers to choose
// the value instead of typing it: whole numbers on one wheel and,
// for decimals, the fraction (e.g. ,0 ,5 or ,1 ... ,9) on the other.
// Same as NumberWheelField on iOS.
@Composable
fun NumberWheelField(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    // 1 for whole numbers, 0.5 or 0.1 for decimals.
    step: Double = 1.0,
    unit: String? = null,
    // Value the wheels start at when there is no value yet.
    defaultValue: Double? = null,
    // Shows "Clear" so the value can be removed (e.g. a set without weight).
    allowsEmpty: Boolean = false,
    title: String? = null,
    // Text shown instead of the number, e.g. reps written as "8-10".
    displayText: String? = null
) {
    var isPresented by remember { mutableStateOf(false) }

    Text(
        displayText ?: value?.let { formatWheelNumber(it, step) } ?: "—",
        style = ApexText.body,
        color = if (value == null && displayText == null) ApexColors.secondaryLabel else ApexColors.label,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(ApexColors.label.copy(alpha = 0.06f))
            .clickable { isPresented = true }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )

    if (isPresented) {
        NumberWheelSheet(
            value = value,
            range = range,
            step = step,
            unit = unit,
            defaultValue = defaultValue,
            allowsEmpty = allowsEmpty,
            title = title,
            onDone = {
                onValueChange(it)
                isPresented = false
            },
            onDismiss = { isPresented = false }
        )
    }
}

fun formatWheelNumber(value: Double, step: Double): String {
    if (step >= 1) return value.roundToInt().toString()
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == floor(rounded)) rounded.toLong().toString()
    else rounded.toString().replace('.', DecimalFormatSymbols.getInstance().decimalSeparator)
}

// Bottom sheet with the wheels.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberWheelSheet(
    value: Double?,
    range: IntRange,
    step: Double,
    unit: String?,
    defaultValue: Double?,
    allowsEmpty: Boolean,
    title: String?,
    onDone: (Double?) -> Unit,
    onDismiss: () -> Unit
) {
    val start = (value ?: defaultValue ?: range.first.toDouble())
        .coerceIn(range.first.toDouble(), range.last.toDouble())
    val wholeStart = floor(start).toInt()
    val fractionCount = if (step >= 1) 1 else (1 / step).roundToInt()
    val fractionStart = if (step < 1) ((start - wholeStart) / step).roundToInt().coerceIn(0, fractionCount - 1) else 0

    var whole by remember { mutableIntStateOf(wholeStart) }
    var fraction by remember { mutableIntStateOf(fractionStart) }
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ApexColors.card
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (allowsEmpty) {
                    TextButton(onClick = { onDone(null) }) {
                        Text(stringResource(R.string.clear), color = ApexColors.red)
                    }
                } else {
                    Spacer(Modifier.width(64.dp))
                }
                Text(
                    title.orEmpty(),
                    style = ApexText.headline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onDone(whole + fraction * step) }) {
                    Text(stringResource(R.string.done), color = ApexColors.main, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelPicker(
                    items = range.map { it.toString() },
                    selectedIndex = whole - range.first,
                    onSelect = { whole = range.first + it },
                    modifier = Modifier.weight(1f)
                )
                if (fractionCount > 1) {
                    WheelPicker(
                        items = (0 until fractionCount).map { "$separator${((it * step) * 10).roundToInt()}" },
                        selectedIndex = fraction,
                        onSelect = { fraction = it },
                        modifier = Modifier.width(90.dp)
                    )
                }
                if (unit != null) {
                    Text(unit, style = ApexText.headline, color = ApexColors.secondaryLabel, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

// Scrolling wheel of values that snaps to the middle row, like a
// UIPickerView wheel.
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 40.dp,
    visibleRows: Int = 5
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    val flingBehavior = rememberSnapFlingBehavior(state)
    val padding = rowHeight * (visibleRows / 2)

    // Row closest to the middle of the wheel.
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }?.index
                ?: state.firstVisibleItemIndex
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to centered }
            .collect { (scrolling, index) -> if (!scrolling) onSelect(index) }
    }

    Box(modifier = modifier.height(rowHeight * visibleRows), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(ApexColors.label.copy(alpha = 0.06f))
        )
        LazyColumn(
            state = state,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items.size) { index ->
                Box(modifier = Modifier.height(rowHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        items[index],
                        style = ApexText.title3.copy(fontWeight = if (index == centered) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (index == centered) ApexColors.label else ApexColors.secondaryLabel
                    )
                }
            }
        }
    }
}
