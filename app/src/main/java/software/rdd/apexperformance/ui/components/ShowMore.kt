package software.rdd.apexperformance.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Long lists show their first items and a "Show more" button that adds
// the next ones, instead of all of them at once.
const val SHOW_MORE_PAGE_SIZE = 10

// The first [pageSize] items, with "Show more (n)" below while there are more.
@Composable
fun <T> ShowMoreItems(items: List<T>, pageSize: Int = SHOW_MORE_PAGE_SIZE, content: @Composable (T) -> Unit) {
    var limit by rememberSaveable { mutableIntStateOf(pageSize) }
    items.take(limit).forEach { content(it) }
    if (items.size > limit) {
        ShowMoreButton(items.size - limit) { limit += pageSize }
    }
}

@Composable
fun ShowMoreButton(remaining: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.show_more, remaining),
            style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = ApexColors.main
        )
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = ApexColors.main, modifier = Modifier.size(18.dp))
    }
}
