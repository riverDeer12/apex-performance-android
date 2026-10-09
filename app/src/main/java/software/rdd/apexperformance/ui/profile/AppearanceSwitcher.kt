package software.rdd.apexperformance.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.util.AppAppearance
import software.rdd.apexperformance.core.util.AppearanceSettings
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.theme.ApexColors

// Light, dark or system appearance, picked from a menu in the profile.
@Composable
fun AppearanceSwitcher() {
    var expanded by remember { mutableStateOf(false) }
    val appearance = AppearanceSettings.appearance

    Box {
        SettingsRow(
            icon = Icons.Outlined.Contrast,
            iconTint = ApexColors.main,
            title = stringResource(R.string.appearance),
            subtitle = stringResource(appearance.title),
            showChevron = true,
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = ApexColors.background
        ) {
            AppAppearance.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.title)) },
                    trailingIcon = {
                        if (option == appearance) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = ApexColors.main, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        AppearanceSettings.select(option)
                    }
                )
            }
        }
    }
}
