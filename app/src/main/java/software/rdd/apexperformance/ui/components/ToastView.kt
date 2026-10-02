package software.rdd.apexperformance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.core.util.Toast
import software.rdd.apexperformance.ui.theme.ApexText

@Composable
fun ToastView(toast: Toast, modifier: Modifier = Modifier) {
    val type = toast.type
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(type.color.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(vertical = 15.dp, horizontal = 20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(type.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(type.title),
                style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Text(
                toast.message.resolve(),
                style = ApexText.subheadline,
                color = Color.White.copy(alpha = 0.95f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
