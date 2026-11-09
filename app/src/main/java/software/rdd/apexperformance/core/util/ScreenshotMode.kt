package software.rdd.apexperformance.core.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Set only by the Google Play screenshot test (PlayStoreScreenshots) while
// it captures a screen, so the floating chat button doesn't cover content.
object ScreenshotMode {
    var hidesChatButton by mutableStateOf(false)
}
