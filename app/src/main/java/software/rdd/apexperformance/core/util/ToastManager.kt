package software.rdd.apexperformance.core.util

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.rdd.apexperformance.R
import software.rdd.apexperformance.ui.theme.ApexColors

enum class ToastType(val color: Color, val icon: ImageVector, @StringRes val title: Int) {
    SUCCESS(ApexColors.green, Icons.Filled.CheckCircle, R.string.success),
    WARNING(ApexColors.orange, Icons.Filled.Warning, R.string.warning),
    ERROR(ApexColors.red, Icons.Filled.Cancel, R.string.error),
    INFO(ApexColors.secondaryLabel, Icons.Filled.Info, R.string.info)
}

// Message is a string resource for app messages, or text from mapError().
sealed interface UiText {
    data class Res(@StringRes val id: Int) : UiText
    data class Raw(val text: String) : UiText

    @Composable
    fun resolve(): String = when (this) {
        is Res -> stringResource(id)
        is Raw -> text
    }
}

data class Toast(val message: UiText, val type: ToastType, val id: Long = System.nanoTime())

object ToastManager {

    var toast by mutableStateOf<Toast?>(null)
        private set

    private val scope = MainScope()
    private var hideJob: Job? = null

    fun show(@StringRes message: Int, type: ToastType, durationSeconds: Long = 5) =
        show(UiText.Res(message), type, durationSeconds)

    fun show(message: String, type: ToastType, durationSeconds: Long = 5) =
        show(UiText.Raw(message), type, durationSeconds)

    fun show(message: UiText, type: ToastType, durationSeconds: Long = 5) {
        toast = Toast(message, type)
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(durationSeconds * 1000)
            toast = null
        }
    }
}
