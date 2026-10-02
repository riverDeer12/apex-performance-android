package software.rdd.apexperformance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// iOS system colors used by the iPhone app, so both apps look the same.
object ApexColors {
    val main = Color(0xFF0034C4)
    val groupedBackground = Color(0xFFF2F2F7)
    val secondaryGroupedBackground = Color.White
    val background = Color.White
    val label = Color.Black
    val secondaryLabel = Color(0x993C3C43)
    val tertiaryLabel = Color(0x4D3C3C43)
    val separator = Color(0x4A3C3C43)
    val systemGray5 = Color(0xFFE5E5EA)
    val systemGray6 = Color(0xFFF2F2F7)
    val green = Color(0xFF34C759)
    val red = Color(0xFFFF3B30)
    val orange = Color(0xFFFF9500)
    val blue = Color(0xFF007AFF)
    val gray = Color(0xFF8E8E93)
}

// Text styles named after the iOS ones (title, headline, subheadline, caption...).
object ApexText {
    val title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
    val title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
    val title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val subheadline = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp)
}

private val colorScheme = lightColorScheme(
    primary = ApexColors.main,
    onPrimary = Color.White,
    secondary = ApexColors.main,
    background = ApexColors.groupedBackground,
    onBackground = ApexColors.label,
    surface = ApexColors.background,
    onSurface = ApexColors.label,
    surfaceVariant = ApexColors.systemGray6,
    onSurfaceVariant = ApexColors.secondaryLabel,
    surfaceContainer = ApexColors.background,
    surfaceContainerHigh = ApexColors.background,
    surfaceContainerHighest = ApexColors.background,
    surfaceContainerLow = ApexColors.background,
    outline = ApexColors.separator,
    outlineVariant = ApexColors.separator,
    error = ApexColors.red
)

@Composable
fun ApexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            bodyLarge = ApexText.body,
            bodyMedium = ApexText.subheadline,
            titleMedium = ApexText.headline
        ),
        content = content
    )
}
