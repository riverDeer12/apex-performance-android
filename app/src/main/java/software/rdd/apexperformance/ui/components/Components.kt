package software.rdd.apexperformance.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Flat card with a hairline border, same as CardView on iOS.
@Composable
fun CardView(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .apexCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (title != null) {
            ApexLabel(title, modifier = Modifier.padding(top = 2.dp))
        }
        content()
    }
}

// Title and subtitle at the top of tab screens.
@Composable
fun ScreenHeader(title: String, subtitle: String?, large: Boolean = true) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = if (large) ApexText.title else ApexText.title2)
        if (subtitle != null) {
            Text(subtitle, style = ApexText.subheadline, color = ApexColors.secondaryLabel)
        }
    }
}

// Section title shown above a card ("Pending approvals", dates...).
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = ApexText.headline,
        color = ApexColors.secondaryLabel,
        modifier = modifier.padding(horizontal = 20.dp)
    )
}

@Composable
fun RowDivider(startIndent: Dp = 52.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent),
        thickness = 0.5.dp,
        color = ApexColors.separator
    )
}

// Rounded square with an icon, used at the start of list rows.
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 34.dp, backgroundAlpha: Float = 0.15f) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = backgroundAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

// Circle with an icon, used in appointment and request rows.
@Composable
fun IconCircle(icon: ImageVector, tint: Color, size: Dp = 40.dp, background: Color = tint.copy(alpha = 0.15f), iconSize: Dp = 20.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = ApexColors.tertiaryLabel,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showChevron: Boolean = false,
    titleColor: Color = ApexColors.label,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(icon, iconTint)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = titleColor,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (badge != null) {
                    Badge(badge, ApexColors.red, isPulsing = true)
                }
            }
            if (subtitle != null) {
                Text(subtitle, style = ApexText.caption, color = ApexColors.secondaryLabel)
            }
        }

        if (showChevron) Chevron()
    }
}

@Composable
fun Badge(text: String, color: Color, isPulsing: Boolean = false) {
    val pulse = if (isPulsing) {
        val transition = rememberInfiniteTransition(label = "badge")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulse"
        ).value
    } else 0f

    Text(
        text,
        style = ApexText.caption2.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .scale(1f + 0.06f * pulse)
            .alpha(1f - 0.15f * pulse)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .apexCard()
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(value, style = ApexText.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        ApexLabel(label)
    }
}

// Small tinted button content, same as ButtonContentView on iOS.
@Composable
fun TintedButton(
    text: String?,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .widthIn(min = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ApexColors.main.copy(alpha = 0.10f))
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = if (text != null) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        if (text != null) {
            Text(text, color = ApexColors.main, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (icon != null) {
            Icon(icon, contentDescription = contentDescription, tint = ApexColors.main, modifier = Modifier.size(18.dp))
        }
    }
}

// Green "Approve" / red "Reject" button under requests and pending appointments.
@Composable
fun RowScope.ActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            SmallProgress(color)
        } else {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(text, style = ApexText.subheadline.copy(fontWeight = FontWeight.Medium), color = color)
    }
}

// Multi-line text input with placeholder, same as TextAreaView on iOS.
@Composable
fun TextArea(placeholder: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ApexColors.secondaryGroupedBackground)
            .border(
                1.dp,
                if (isFocused) ApexColors.main else ApexColors.gray.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = ApexText.body, color = ApexColors.secondaryLabel)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = ApexText.body,
            cursorBrush = SolidColor(ApexColors.main),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
        )
    }
}

// Borderless single-line field used inside cards and forms, like a SwiftUI TextField.
@Composable
fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    textStyle: TextStyle = ApexText.body
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = textStyle.copy(textAlign = textAlign, color = ApexColors.label),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(ApexColors.main),
        decorationBox = { inner ->
            Box(contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = textStyle.copy(textAlign = textAlign),
                        color = ApexColors.tertiaryLabel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                inner()
            }
        }
    )
}

// "Title .... field" row used in client and measurement cards.
@Composable
fun EditableRow(title: String, field: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = ApexText.body, color = ApexColors.secondaryLabel)
        Spacer(Modifier.weight(1f))
        field()
    }
}

@Composable
fun noRippleClickable(onClick: () -> Unit): Modifier =
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
