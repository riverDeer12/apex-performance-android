package software.rdd.apexperformance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import software.rdd.apexperformance.core.util.AppLanguage

class AppLanguageTest {

    @Test
    fun languageTagsMapToTheAppsLanguages() {
        assertEquals(AppLanguage.HR, AppLanguage.fromTag("hr"))
        assertEquals(AppLanguage.HR, AppLanguage.fromTag("hr-HR"))
        assertEquals(AppLanguage.DE, AppLanguage.fromTag("de_AT"))
        assertEquals(AppLanguage.IT, AppLanguage.fromTag("IT"))
        assertEquals(AppLanguage.EN, AppLanguage.fromTag("en-US"))
        // Other languages and no language: the device's.
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("fr"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
    }

    // Android 13+: the language set in the system settings for the app.
    @Test
    fun aLanguageChangedInTheSystemSettingsIsTaken() {
        assertEquals(AppLanguage.DE, AppLanguage.afterSystemChange("de", seen = "it", hasPicked = true))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.afterSystemChange("", seen = "it", hasPicked = true))
        assertEquals(AppLanguage.HR, AppLanguage.afterSystemChange("hr", seen = "", hasPicked = false))
    }

    @Test
    fun anUnchangedSystemLanguageKeepsThePickedOne() {
        assertNull(AppLanguage.afterSystemChange("it", seen = "it", hasPicked = true))
        assertNull(AppLanguage.afterSystemChange("", seen = "", hasPicked = true))
    }

    // Updated from a version that didn't look at the system settings.
    @Test
    fun theFirstLookKeepsALanguagePickedInTheApp() {
        assertNull(AppLanguage.afterSystemChange("hr", seen = null, hasPicked = true))
        assertEquals(AppLanguage.HR, AppLanguage.afterSystemChange("hr", seen = null, hasPicked = false))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.afterSystemChange("", seen = null, hasPicked = false))
    }
}
