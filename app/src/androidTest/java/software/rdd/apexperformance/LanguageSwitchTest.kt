package software.rdd.apexperformance

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.network.DemoAPI

// The language picked on the login screen (the same menu as in the profile)
// changes the texts right away and is kept after the app is opened again.
@RunWith(AndroidJUnit4::class)
class LanguageSwitchTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    @Before
    fun setUp() {
        instrumentation.runOnMainSync { AuthManager.logout() }
        reset()
    }

    @After
    fun tearDown() {
        DemoAPI.isEnabled = false
        instrumentation.runOnMainSync { AuthManager.logout() }
        reset()
    }

    private fun reset() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        if (Build.VERSION.SDK_INT >= 33) setSystemAppLocales("")
        instrumentation.runOnMainSync { ApexApp.applyLanguage() }
    }

    private fun setSystemAppLocales(tags: String) {
        if (Build.VERSION.SDK_INT < 33) return
        instrumentation.runOnMainSync {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun pickedLanguageAppliesAndIsKept() {
        open {
            expectCode("EN")
            pick("Italiano")
            expectCode("IT")
            pick("Deutsch")
            expectCode("DE")
            pick("Hrvatski")
            expectCode("HR")
        }
        // Opened again: still Croatian.
        open { expectCode("HR") }
        open {
            pick("Jezik uređaja")
            expectCode("EN")
        }
        open { expectCode("EN") }
    }

    // Android 13+: a language set earlier in the system's app language settings.
    @Test
    fun pickedLanguageWinsOverOlderSystemAppLanguage() {
        if (Build.VERSION.SDK_INT < 33) return
        setSystemAppLocales("hr")
        open {
            expectCode("HR")
            pick("English")
            expectCode("EN")
            pick("Italiano")
            expectCode("IT")
        }
        open { expectCode("IT") }
    }

    // Android 13+: the language changed in the system's app language settings.
    @Test
    fun systemAppLanguageChangeIsFollowed() {
        if (Build.VERSION.SDK_INT < 33) return
        open {
            pick("Italiano")
            expectCode("IT")
            setSystemAppLocales("de")
            expectCode("DE")
        }
        open { expectCode("DE") }
    }

    // Logged in (demo client): the menu of the profile row, also after a
    // web view (YouTube player) was opened in the app.
    @Test
    fun profileLanguageApplies() {
        DemoAPI.isEnabled = true
        instrumentation.runOnMainSync { AuthManager.login(DemoAPI.token(DemoAPI.Role.CLIENT)) }
        open { scenario ->
            clickTag("tab-profile")
            pickInProfile("Italiano")
            expectProfileTab("Profilo")
            scenario.onActivity { android.webkit.WebView(it).destroy() }
            pickInProfile("Deutsch")
            expectProfileTab("Profil")
            pickInProfile("English")
            expectProfileTab("Profile")
        }
        open { expectProfileTab("Profile") }
    }

    private fun clickTag(tag: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val node = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)[0]
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    private fun pickInProfile(name: String) {
        clickTag("language-row")
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText(name))[0].performClick()
        instrumentation.waitForIdleSync()
    }

    private fun expectProfileTab(label: String) {
        try {
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodes(hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            val message = "API ${Build.VERSION.SDK_INT}: profile tab not \"$label\", app " +
                "${ApexApp.appContext.getString(R.string.app_language_code)}, picked " +
                "${context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("appLanguage", null)}"
            Log.e(TAG, message)
            runCatching {
                compose.onAllNodes(isRoot(), useUnmergedTree = true)[0].printToLog(TAG)
            }
            throw AssertionError(message, e)
        }
    }

    private fun open(steps: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodes(hasTestTag("splash"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
            steps(scenario)
        }
    }

    private fun pick(name: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag("login-language"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasTestTag("login-language"), useUnmergedTree = true)[0].performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText(name))[0].performClick()
        instrumentation.waitForIdleSync()
    }

    private fun expectCode(code: String) {
        try {
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodes(hasTestTag("login-language") and hasText(code, substring = false), useUnmergedTree = false)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            val system = if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            } else "-"
            val message = "API ${Build.VERSION.SDK_INT}: expected $code, app " +
                "${ApexApp.appContext.getString(R.string.app_language_code)}, system app locales [$system], " +
                "picked ${context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("appLanguage", null)}"
            Log.e(TAG, message)
            throw AssertionError(message, e)
        }
    }

    private companion object {
        const val TAG = "LanguageSwitchTest"
        const val TIMEOUT = 15_000L
    }
}
