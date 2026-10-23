package software.rdd.apexperformance.ui.profile

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.util.AppLanguage
import software.rdd.apexperformance.ui.theme.ApexColors

// Menu with the app's languages, opened from [anchor], e.g. on login and in the profile.
@Composable
fun LanguageMenu(modifier: Modifier = Modifier, anchor: @Composable (open: () -> Unit) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val selected = remember { AppLanguage.selected(context) }

    Box(modifier) {
        anchor { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = ApexColors.background
        ) {
            AppLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(if (option == AppLanguage.SYSTEM) stringResource(R.string.device_language) else option.nativeName)
                    },
                    trailingIcon = {
                        if (option == selected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = ApexColors.main, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        (context as? Activity)?.let { AppLanguage.select(it, option) }
                    }
                )
            }
        }
    }
}
