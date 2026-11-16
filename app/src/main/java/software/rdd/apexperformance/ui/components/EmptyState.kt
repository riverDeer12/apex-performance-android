package software.rdd.apexperformance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Empty list: an icon, one sentence and, where it helps, a button for the
// first step (e.g. "Zatraži termin"), like ContentUnavailableView on iOS.
// Used inside a card or on its own; the text wraps at any font size.
@Composable
fun EmptyStateView(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .testTag("empty-state")
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ApexColors.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ApexColors.accent, modifier = Modifier.size(24.dp))
        }
        Text(
            text,
            style = ApexText.subheadline,
            color = ApexColors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (buttonText != null && onButtonClick != null) {
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexColors.accent.copy(alpha = 0.12f))
                    .clickable(onClick = onButtonClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buttonText,
                    style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = ApexColors.accent,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
