package software.rdd.apexperformance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.network.OfflineTrainingQueue
import software.rdd.apexperformance.core.network.PendingTrainingSave
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Small banner above the tabs while training saves wait for the connection:
// "Nema veze – spremit ću čim se veza vrati". A save the server rejected
// shows its error with Retry and Discard until the coach decides.
@Composable
fun OfflineTrainingBanner() {
    val entries = OfflineTrainingQueue.visibleEntries
    if (entries.isEmpty()) return

    Column(modifier = Modifier.testTag("offline-banner").fillMaxWidth()) {
        HorizontalDivider(thickness = 0.5.dp, color = ApexColors.separator)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ApexColors.card)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            entries.filter { it.error != null }.forEach { RejectedRow(it) }

            if (entries.any { it.error == null }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = ApexColors.orange, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.offline_banner), style = ApexText.footnote, color = ApexColors.label)
                }
            }
        }
    }
}

@Composable
private fun RejectedRow(entry: PendingTrainingSave) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = ApexColors.red, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.offline_save_rejected, entry.trainingName),
                    style = ApexText.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = ApexColors.label
                )
                entry.error?.let { Text(it, style = ApexText.footnote, color = ApexColors.secondaryLabel) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 26.dp)) {
            BannerButton(stringResource(R.string.retry), ApexColors.accent) { OfflineTrainingQueue.retry(entry.key) }
            BannerButton(stringResource(R.string.discard), ApexColors.red) { OfflineTrainingQueue.discard(entry.key) }
        }
    }
}

@Composable
private fun BannerButton(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        style = ApexText.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
