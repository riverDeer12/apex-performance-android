package software.rdd.apexperformance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Inline navigation bar: back chevron on the left, title in the middle and
// actions (checkmark, plus...) on the right, as on iOS.
@Composable
fun TopBar(
    title: String? = null,
    showBack: Boolean = LocalNavigator.current.canPop,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val navigator = LocalNavigator.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ApexColors.groupedBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(48.dp)
            .padding(horizontal = 4.dp)
    ) {
        if (showBack) {
            IconButton(
                onClick = { onBack?.invoke() ?: navigator.pop() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = stringResource(R.string.back_button),
                    tint = ApexColors.main,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (title != null) {
            Text(
                title,
                style = ApexText.headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 96.dp)
            )
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

// Toolbar icon button tinted with the main color.
@Composable
fun ToolbarIcon(
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled && !isLoading) {
        if (isLoading) {
            SmallProgress()
        } else {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled) ApexColors.main else ApexColors.tertiaryLabel
            )
        }
    }
}

// Checkmark that saves a form, with a spinner while saving.
@Composable
fun SaveAction(enabled: Boolean, isSaving: Boolean, icon: ImageVector = Icons.Filled.Check, onClick: () -> Unit) {
    ToolbarIcon(icon, stringResource(R.string.save), enabled = enabled, isLoading = isSaving, onClick = onClick)
}

@Composable
fun SmallProgress(color: Color = ApexColors.main, size: Dp = 18.dp) {
    CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(size))
}

// Screen body on the grouped background: a vertically scrolling column with
// the iOS spacing, optional pull-to-refresh and a centered spinner overlay.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollScreen(
    topBar: @Composable () -> Unit = { TopBar() },
    showLoading: Boolean = false,
    onRefresh: (suspend () -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexColors.groupedBackground)
    ) {
        topBar()

        val body = @Composable {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (onRefresh != null) {
                var isRefreshing by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            try {
                                onRefresh()
                            } finally {
                                isRefreshing = false
                            }
                        }
                    }
                ) { body() }
            } else {
                body()
            }

            if (showLoading) {
                CircularProgressIndicator(color = ApexColors.main, modifier = Modifier.align(Alignment.Center))
            }
            overlay()
        }
    }
}

// Dimmed full-screen spinner shown while a form is being saved.
@Composable
fun BoxScope.SavingOverlay() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = ApexColors.main)
    }
}

// Confirmation before a destructive or important action, like
// confirmationDialog on iOS.
@Composable
fun ConfirmDialog(
    title: String,
    message: String?,
    confirmText: String,
    dismissText: String = stringResource(R.string.cancel),
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ApexColors.background,
        title = { Text(title, style = ApexText.headline) },
        text = message?.let { { Text(it, style = ApexText.subheadline, color = ApexColors.secondaryLabel) } },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) {
                Text(confirmText, color = if (destructive) ApexColors.red else ApexColors.main, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = ApexColors.main)
            }
        }
    )
}

// Grouped form section: header above a white rounded block, like Form/Section on iOS.
@Composable
fun FormSection(
    header: String? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                style = ApexText.footnote,
                color = ApexColors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ApexColors.secondaryGroupedBackground)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            content = content
        )
        if (footer != null) {
            Box(modifier = Modifier.padding(start = 16.dp, top = 6.dp)) { footer() }
        }
    }
}

// Picker shown as a menu with the selected value, like Picker inside a Form on iOS.
@Composable
fun <T> MenuPicker(
    label: String?,
    selectedText: String,
    options: List<T>,
    optionText: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { expanded = true }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (label != null) {
            Text(label, style = ApexText.body, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedText,
                    style = ApexText.body,
                    color = if (enabled) ApexColors.main else ApexColors.tertiaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Filled.UnfoldMore,
                    contentDescription = null,
                    tint = if (enabled) ApexColors.main else ApexColors.tertiaryLabel,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = ApexColors.background
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionText(option)) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = ApexText.body, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = ApexColors.green, checkedBorderColor = ApexColors.green)
        )
    }
}

@Composable
fun LoadingRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallProgress()
        Text(text, style = ApexText.body, color = ApexColors.secondaryLabel)
    }
}

// Placeholder text inside a card when a list is empty.
@Composable
fun EmptyText(text: String) {
    Text(
        text,
        style = ApexText.body,
        color = ApexColors.secondaryLabel,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}
