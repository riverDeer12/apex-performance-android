package software.rdd.apexperformance.core.util

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Newer version on Google Play, like AppUpdateChecker on iOS: the app asks
// once per launch, and "Update" opens Google Play's update screen, which
// downloads and installs it. Works only for installs from Google Play
// (testing tracks too); otherwise Google Play reports no update.
object AppUpdateChecker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Update to offer in the dialog, null when there is none.
    var available by mutableStateOf<AppUpdateInfo?>(null)
        private set

    private var hasAsked = false

    // On launch and when the app comes back. An update started from the
    // dialog and interrupted (app left) is resumed; a new one is offered
    // once per launch.
    fun check(context: Context, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val manager = AppUpdateManagerFactory.create(context)
        scope.launch {
            val info = try {
                manager.appUpdateInfo.await()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return@launch
            }
            when {
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                    start(context, info, launcher)
                !hasAsked && info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                    hasAsked = true
                    available = info
                }
            }
        }
    }

    fun start(context: Context, info: AppUpdateInfo, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        available = null
        AppUpdateManagerFactory.create(context).startUpdateFlowForResult(
            info,
            launcher,
            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
        )
    }

    fun dismiss() {
        available = null
    }
}
