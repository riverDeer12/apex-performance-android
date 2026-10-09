package software.rdd.apexperformance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.util.AppAppearance
import software.rdd.apexperformance.core.util.AppearanceSettings

// Colors of the iOS app: its asset catalog colors (ApexMainColor, ApexAccent,
// ApexBackground, ApexCard) and the iOS system colors, light and dark. They
// are read from `isDark`, so screens recompose when the appearance changes.
object ApexColors {
    var isDark by mutableStateOf(false)
        internal set

    private fun pick(light: Color, dark: Color) = if (isDark) dark else light

    // ApexMainColor: deep blue, white in dark mode.
    val main get() = pick(Color(0xFF0034C4), Color.White)

    // ApexAccent: sky blue of buttons, progress and highlights.
    val accent get() = pick(Color(0xFF3A98E4), Color(0xFF5CB5F5))

    // Text and icons on top of the accent color, dark in both appearances.
    val onAccent = Color(0xFF081421)

    // ApexBackground behind screens and ApexCard behind cards.
    val groupedBackground get() = pick(Color(0xFFF2F1EE), Color(0xFF0B0C0E))
    val secondaryGroupedBackground get() = card
    val card get() = pick(Color.White, Color(0xFF17191D))
    val background get() = card

    // Hairline around cards and table rows.
    val border get() = label.copy(alpha = 0.08f)

    val label get() = pick(Color.Black, Color.White)
    val secondaryLabel get() = pick(Color(0x993C3C43), Color(0x99EBEBF5))
    val tertiaryLabel get() = pick(Color(0x4D3C3C43), Color(0x4DEBEBF5))
    val separator get() = pick(Color(0x4A3C3C43), Color(0x99545458))
    val systemGray5 get() = pick(Color(0xFFE5E5EA), Color(0xFF2C2C2E))
    val systemGray6 get() = pick(Color(0xFFF2F2F7), Color(0xFF1C1C1E))
    val green get() = pick(Color(0xFF34C759), Color(0xFF30D158))
    val red get() = pick(Color(0xFFFF3B30), Color(0xFFFF453A))
    val orange get() = pick(Color(0xFFFF9500), Color(0xFFFF9F0A))
    val blue get() = pick(Color(0xFF007AFF), Color(0xFF0A84FF))
    val gray = Color(0xFF8E8E93)
}

// Michroma, the font of titles and headings. It is wide, so it is
// used a bit smaller than the system font would be.
val MichromaFamily = FontFamily(Font(R.font.michroma_regular))

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

    // Michroma heading of the given size, like Font.apexHeading on iOS.
    fun heading(size: Int) = TextStyle(fontFamily = MichromaFamily, fontSize = size.sp, lineHeight = (size * 1.35f).sp)

    // Big uppercase screen title, e.g. "MOJI TRENINZI" (apexTitle). Uppercase the text.
    val screenTitle = heading(19).copy(letterSpacing = 1.sp)

    // Uppercase heading of a card or section (apexSectionTitle). Uppercase the text.
    val sectionTitle = heading(11).copy(letterSpacing = 0.5.sp)

    // Small uppercase label above values and sections (apexLabel). Uppercase the
    // text and use ApexColors.secondaryLabel.
    val label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, lineHeight = 14.sp)

    // Inline navigation bar title in Michroma.
    val navigationTitle = heading(15)
}

@Composable
fun ApexTheme(content: @Composable () -> Unit) {
    val isDark = when (AppearanceSettings.appearance) {
        AppAppearance.SYSTEM -> isSystemInDarkTheme()
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }
    // Set before the content reads the colors, so the first frame is right.
    if (ApexColors.isDark != isDark) ApexColors.isDark = isDark
    SideEffect { ApexColors.isDark = isDark }

    val base = if (isDark) darkColorScheme() else lightColorScheme()
    val colorScheme = base.copy(
        primary = ApexColors.main,
        onPrimary = if (isDark) Color.Black else Color.White,
        secondary = ApexColors.accent,
        background = ApexColors.groupedBackground,
        onBackground = ApexColors.label,
        surface = ApexColors.card,
        onSurface = ApexColors.label,
        surfaceVariant = ApexColors.systemGray6,
        onSurfaceVariant = ApexColors.secondaryLabel,
        surfaceContainer = ApexColors.card,
        surfaceContainerHigh = ApexColors.card,
        surfaceContainerHighest = ApexColors.card,
        surfaceContainerLow = ApexColors.card,
        outline = ApexColors.separator,
        outlineVariant = ApexColors.separator,
        error = ApexColors.red
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            bodyLarge = ApexText.body,
            bodyMedium = ApexText.subheadline,
            titleMedium = ApexText.headline
        )
    ) {
        // Text without a color follows the appearance.
        CompositionLocalProvider(LocalContentColor provides ApexColors.label, content = content)
    }
}
