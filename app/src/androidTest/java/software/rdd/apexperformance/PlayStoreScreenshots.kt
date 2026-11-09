package software.rdd.apexperformance

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.network.DemoAPI
import software.rdd.apexperformance.core.util.ScreenshotMode
import java.io.File
import java.util.Locale

// Google Play phone screenshots, in Croatian, English, Italian and German.
// The app runs with invented demo data (DemoAPI, debug builds only), so no
// server or accounts are needed and no real people are shown.
//
// PNGs are saved to <external files>/play-screenshots/<language>/NN_name.png
// (/sdcard/Android/data/software.rdd.apexperformance/files/...), numbered in
// the order they go to Google Play. The "languages" instrumentation argument
// (e.g. -e languages en) takes only some of them.
// Run by .github/workflows/play-screenshots.yml.
@RunWith(AndroidJUnit4::class)
class PlayStoreScreenshots {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    private var language = "en"

    @Test
    fun takeScreenshots() {
        DemoAPI.isEnabled = true
        // The app asks for it on the first launch, the dialog would cover the screenshots.
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val languages = InstrumentationRegistry.getArguments().getString("languages")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: listOf("hr", "en", "it", "de")

        for (language in languages) {
            this.language = language
            selectLanguage(language)
            clientScreens()
            coachScreens()
        }
    }

    @After
    fun tearDown() {
        DemoAPI.isEnabled = false
        instrumentation.runOnMainSync { AuthManager.logout() }
    }

    // MARK: - Screens

    // Ana Kovač, the demo client.
    private fun clientScreens() = launch(DemoAPI.Role.CLIENT) { scenario ->
        // Home with the next appointment, package and goal.
        waitForTag("home-greeting")
        waitForTag("next-appointment")
        screenshot(1, "home")

        // Progress: monthly review, body mass and circumferences.
        click("progress-row")
        waitForTag("body-mass-value")
        waitForTag("monthly-review")
        screenshot(8, "body_progress")

        // Training progress, scrolled to the muscle groups.
        node("training-progress-row").performScrollTo().performClick()
        waitForTag("muscle-groups-chart")
        scrollToEnd("muscle-groups-chart")
        screenshot(4, "training_progress")

        // The latest training with its sets.
        click("tab-trainings")
        click("training-row")
        waitForTag("exercise-card")
        scrollToTop("training-times")
        screenshot(2, "training_details")

        // Chat with the coach: a picture and a shared workout.
        click("tab-home")
        click("chat-button")
        click("chat-row")
        waitForTag("chat-image")
        screenshot(7, "chat")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    // Luka Marić, the demo coach.
    private fun coachScreens() = launch(DemoAPI.Role.COACH) {
        // Appointments with requests waiting for the coach (first tab).
        waitForTag("appointment-request-row")
        screenshot(6, "appointments")

        // Workouts of one category, picked with the buttons above the list.
        click("tab-workouts")
        click("category-MobilityFlexibility")
        waitForTag("workout-row")
        screenshot(5, "workouts")

        // Ana's details, trainings first.
        click("tab-clients")
        waitForTag("client-row")
        compose.onAllNodes(
            hasTestTag("client-row") and hasAnyDescendant(hasText(DemoAPI.clients[0].fullName, substring = true)),
            useUnmergedTree = true
        )[0].performClick()
        waitForTag("client-training-progress")
        screenshot(3, "client_details")
    }

    // MARK: - Helpers

    private fun selectLanguage(language: String) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("appLanguage", language).commit()
        instrumentation.runOnMainSync { ApexApp.applyLanguage() }
    }

    // Logs in with the demo token and opens the app; it is closed after.
    private fun launch(role: DemoAPI.Role, steps: (ActivityScenario<MainActivity>) -> Unit) {
        instrumentation.runOnMainSync {
            AuthManager.logout()
            AuthManager.login(DemoAPI.token(role))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // The intro video plays over the app first.
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodes(hasTestTag("splash"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
            steps(scenario)
        }
    }

    private fun waitForTag(tag: String) {
        try {
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            // What was on screen, to see why: a picture in the workflow's
            // artifact and the semantics tree in its log (logcat).
            screenshot(0, "failed_waiting_for_$tag")
            runCatching {
                val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
                for (index in roots.fetchSemanticsNodes().indices) roots[index].printToLog(TAG)
            }
            throw AssertionError("$language: \"$tag\" not shown", e)
        }
    }

    private fun node(tag: String): SemanticsNodeInteraction {
        waitForTag(tag)
        // Unmerged: tags inside a clickable are merged away otherwise.
        return compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)[0]
    }

    // Scrolled into view first (when in a scrolling screen): a row below the
    // fold would be under the tab bar.
    private fun click(tag: String) {
        val node = node(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    // Scrolls the screen so the node is at its top, a little below the top bar.
    private fun scrollToTop(tag: String, marginDp: Float = 12f) {
        val target = node(tag).fetchSemanticsNode().boundsInRoot
        val containers = compose.onAllNodes(
            hasScrollAction() and hasAnyDescendant(hasTestTag(tag)),
            useUnmergedTree = true
        )
        // The innermost scrolling container.
        val index = containers.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.height }.index
        val top = containers[index].fetchSemanticsNode().boundsInRoot.top
        val margin = marginDp * context.resources.displayMetrics.density
        containers[index].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, target.top - top - margin) }
        compose.waitForIdle()
    }

    // Scrolls the screen holding the node to its end (the node is its last card).
    private fun scrollToEnd(tag: String) {
        waitForTag(tag)
        val containers = compose.onAllNodes(
            hasScrollAction() and hasAnyDescendant(hasTestTag(tag)),
            useUnmergedTree = true
        )
        val index = containers.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.height }.index
        repeat(3) {
            containers[index].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
            compose.waitForIdle()
            val viewport = containers[index].fetchSemanticsNode().boundsInRoot
            val bounds = node(tag).fetchSemanticsNode().boundsInRoot
            if (bounds.bottom <= viewport.bottom + 1) return
        }
        throw AssertionError("$language: couldn't scroll to \"$tag\"")
    }

    // Clean status bar: 9:41, full battery and signal (system UI demo mode,
    // allowed by the workflow). Sent before every screenshot, as the system
    // UI can leave demo mode while the app restarts.
    private fun cleanStatusBar() {
        val commands = listOf(
            "enter",
            "clock -e hhmm 0941",
            "battery -e level 100 -e plugged false",
            "network -e wifi show -e level 4 -e fully true",
            // Only Wi-Fi: with the mobile signal too the bar showed two Wi-Fi-like icons.
            "network -e mobile hide",
            "notifications -e visible false"
        )
        for (command in commands) {
            runCatching {
                instrumentation.uiAutomation
                    .executeShellCommand("am broadcast -a com.android.systemui.demo -e command $command")
                    .let { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }
                    .let { Log.d(TAG, "Demo mode $command: ${it.decodeToString().trim()}") }
            }.onFailure { Log.w(TAG, "Demo mode $command failed", it) }
        }
    }

    private fun screenshot(number: Int, name: String) {
        if (number > 0) cleanStatusBar()
        // The floating chat button would cover the bottom of the content.
        instrumentation.runOnMainSync { ScreenshotMode.hidesChatButton = true }
        try {
            capture(number, name)
        } finally {
            instrumentation.runOnMainSync { ScreenshotMode.hidesChatButton = false }
        }
    }

    private fun capture(number: Int, name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        // The settled frame is drawn on the next vsync.
        Thread.sleep(SETTLE_MILLIS)

        // Right after the app opens the screen can still be blank: taken again then.
        var bitmap = instrumentation.uiAutomation.takeScreenshot()
        var attempt = 0
        while ((bitmap == null || bitmap.isBlank()) && attempt < 20) {
            Thread.sleep(SETTLE_MILLIS)
            compose.waitForIdle()
            bitmap = instrumentation.uiAutomation.takeScreenshot()
            attempt++
        }
        if (bitmap == null || (bitmap.isBlank() && number > 0)) throw AssertionError("$language: blank screenshot for $name")
        val directory = File(context.getExternalFilesDir(null), "play-screenshots/$language").apply { mkdirs() }
        val file = File(directory, String.format(Locale.ROOT, "%02d_%s.png", number, name))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "Saved ${file.absolutePath} (${bitmap.width}×${bitmap.height})")
    }

    // One colour all over (a black or empty window), from a grid of pixels.
    private fun Bitmap.isBlank(): Boolean {
        val first = getPixel(width / 2, height / 2)
        for (x in 1..9) for (y in 1..9) {
            if (getPixel(width * x / 10, height * y / 10) != first) return false
        }
        return true
    }

    private companion object {
        const val TAG = "PlayStoreScreenshots"
        const val TIMEOUT = 20_000L
        const val SETTLE_MILLIS = 400L
    }
}
