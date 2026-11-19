package software.rdd.apexperformance

import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import software.rdd.apexperformance.ui.components.WebPlayer

// The player's fullscreen button (like YouTube's, a fullscreen request from
// the page) shows the player over the app in landscape; back closes it and
// leaves the screen as it was. Hosted by MainActivity, which keeps running
// when turned.
@RunWith(AndroidJUnit4::class)
class WebPlayerFullscreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun fullscreenButtonShowsThePlayerInLandscape() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    WebPlayer(html = HTML, baseUrl = "https://apex-performance.fit", modifier = Modifier.fillMaxSize().testTag("player"))
                }
            }
            fun orientation(): Int {
                var orientation = 0
                scenario.onActivity { orientation = it.requestedOrientation }
                return orientation
            }

            // Tapped until the page has loaded and takes the tap.
            val deadline = System.currentTimeMillis() + TIMEOUT
            while (orientation() != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                check(System.currentTimeMillis() < deadline) { "The player didn't go fullscreen" }
                compose.onNodeWithTag("player").performClick()
                Thread.sleep(1_000)
            }

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(TIMEOUT) { orientation() != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
            // Back closed the fullscreen video only, the screen is still there.
            compose.onNodeWithTag("player").assertExists()
        }
    }

    private companion object {
        const val TIMEOUT = 20_000L
        val HTML = """
            <!DOCTYPE html>
            <html><body style="margin:0">
            <div id="player" style="position:absolute;top:0;left:0;right:0;bottom:0;background:#246"
                 onclick="document.getElementById('player').requestFullscreen()"></div>
            </body></html>
        """.trimIndent()
    }
}
