package software.rdd.apexperformance.core.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import software.rdd.apexperformance.core.network.isCancellation
import software.rdd.apexperformance.core.network.mapError
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType

// Runs work (saving, approving...) that should finish even if
// the user switches tabs, but stops when the screen is closed.
fun Screen.launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

fun showError(error: Throwable) {
    if (!error.isCancellation) ToastManager.show(mapError(error), ToastType.ERROR)
}
