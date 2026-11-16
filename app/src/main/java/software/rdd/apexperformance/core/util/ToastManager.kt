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
    // Dark gray in both appearances: white text on it stays readable in dark mode.
    INFO(Color(0xFF48484A), Icons.Filled.Info, R.string.info)
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

// Button on a toast, e.g. "Poništi" after deleting a set.
class ToastAction(val label: UiText, val onClick: () -> Unit)

data class Toast(val message: UiText, val type: ToastType, val action: ToastAction? = null, val id: Long = System.nanoTime())

object ToastManager {

    var toast by mutableStateOf<Toast?>(null)
        private set

    private val scope = MainScope()
    private var hideJob: Job? = null

    fun show(@StringRes message: Int, type: ToastType, durationSeconds: Long = 5) =
        show(UiText.Res(message), type, durationSeconds)

    fun show(message: String, type: ToastType, durationSeconds: Long = 5) =
        show(UiText.Raw(message), type, durationSeconds)

    fun show(message: UiText, type: ToastType, durationSeconds: Long = 5, action: ToastAction? = null) {
        toast = Toast(message, type, action)
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(durationSeconds * 1000)
            toast = null
        }
    }

    // "Obrisano · Poništi": undo for about five seconds.
    fun showUndo(@StringRes message: Int, onUndo: () -> Unit) =
        show(UiText.Res(message), ToastType.INFO, 5, ToastAction(UiText.Res(R.string.undo), onUndo))

    // Runs the toast's action and hides it.
    fun performAction(toast: Toast) {
        val action = toast.action ?: return
        hide()
        action.onClick()
    }

    fun hide() {
        hideJob?.cancel()
        toast = null
    }
}
