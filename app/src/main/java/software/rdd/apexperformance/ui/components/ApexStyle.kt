package software.rdd.apexperformance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Shared look of the iOS redesign (ApexStyle.swift): dark or warm light
// background, flat cards, uppercase spaced headings and a sky blue accent.

// Flat card background used across the app (apexCardBackground).
fun Modifier.apexCard(cornerRadius: Dp = 14.dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(ApexColors.card)
        .border(1.dp, ApexColors.border, shape)
}

// Big uppercase screen title, e.g. "MOJI TRENINZI".
@Composable
fun ApexTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = ApexText.screenTitle, modifier = modifier)
}

// Uppercase heading of a card or section, e.g. "NADOLAZEĆI TERMINI".
@Composable
fun ApexSectionTitle(text: String, modifier: Modifier = Modifier, color: Color = ApexColors.label) {
    Text(text.uppercase(), style = ApexText.sectionTitle, color = color, modifier = modifier)
}

// Small uppercase label above values and sections, e.g. "SLJEDEĆI TRENING".
@Composable
fun ApexLabel(text: String, modifier: Modifier = Modifier, color: Color = ApexColors.secondaryLabel) {
    Text(text.uppercase(), style = ApexText.label, color = color, modifier = modifier)
}

// "APEX" wordmark with an uppercase screen title and an optional subtitle.
@Composable
fun ApexScreenHeader(
    title: String,
    subtitle: String? = null,
    showsWordmark: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showsWordmark) {
            Text(
                "APEX",
                style = ApexText.heading(13).copy(letterSpacing = 3.sp),
                color = ApexColors.secondaryLabel
            )
        }
        ApexTitle(title)
        if (subtitle != null) {
            Text(subtitle, style = ApexText.subheadline, color = ApexColors.secondaryLabel)
        }
    }
}

// Full width accent button with an uppercase title and an arrow,
// e.g. "REZERVIRAJ TERMIN  →".
@Composable
fun ApexPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.AutoMirrored.Filled.ArrowForward,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ApexColors.accent.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled && !isLoading, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text.uppercase(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = ApexColors.onAccent,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        if (isLoading) {
            SmallProgress(ApexColors.onAccent)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = ApexColors.onAccent, modifier = Modifier.size(18.dp))
        }
    }
}

// Outlined red button for destructive actions, e.g. "OTKAŽI TERMIN".
@Composable
fun ApexDestructiveButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(shape)
            .border(1.dp, ApexColors.red.copy(alpha = 0.8f), shape)
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            SmallProgress(ApexColors.red)
        } else {
            Text(
                text.uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = ApexColors.red
            )
        }
    }
}

// Thin progress bar in the accent color. `value` is 0...1.
@Composable
fun ApexProgressBar(value: Double, modifier: Modifier = Modifier) {
    val fraction = value.coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(ApexColors.label.copy(alpha = 0.1f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(50))
                .background(ApexColors.accent)
        )
    }
}

// Dark picture used on tiles and heroes. Shows a remote picture when
// there is one, otherwise a gradient with a big faint symbol.
@Composable
fun ApexPictureBackground(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    icon: ImageVector = Icons.Filled.FitnessCenter,
    shape: Shape? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .background(
                Brush.linearGradient(listOf(Color(0xFF292F3B), Color(0xFF0D0F14)))
            )
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.12f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(18.dp)
                .size(100.dp)
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        content()
    }
}
