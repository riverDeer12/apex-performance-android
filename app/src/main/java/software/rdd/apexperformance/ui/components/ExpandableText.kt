package software.rdd.apexperformance.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Long free text (notes, descriptions, reviews) showing its first
// `collapsedLines` lines, with "Show more" / "Show less" below only when it
// is longer (ExpandableText on iOS). With `onShowMore` the link opens
// something else (e.g. a detail screen) instead of expanding in place.
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ApexText.body,
    color: Color = ApexColors.label,
    collapsedLines: Int = 3,
    onShowMore: (() -> Unit)? = null
) {
    var isExpanded by rememberSaveable(text) { mutableStateOf(false) }
    var isTruncated by remember(text, collapsedLines) { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text,
            style = style,
            color = color,
            maxLines = if (isExpanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!isExpanded) isTruncated = it.hasVisualOverflow }
        )
        if (isTruncated || isExpanded) {
            ShowMoreLink(isExpanded = isExpanded) {
                if (onShowMore != null) onShowMore() else isExpanded = !isExpanded
            }
        }
    }
}

// "Show more" / "Show less" link in the accent color.
@Composable
fun ShowMoreLink(isExpanded: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        stringResource(if (isExpanded) R.string.show_less else R.string.show_more_text),
        style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
        color = ApexColors.accent,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}
