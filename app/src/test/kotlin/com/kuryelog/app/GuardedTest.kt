package com.kuryelog.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kuryelog.logic.core.CrashReport
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.kuryelog.logic.app.Text as Str

/** A10.4d: sayfa hata kartı ve çökme kartı yeni tasarımda da başlığı, Kopyala/Paylaş ve (çökmede) Gizle'yi gösterir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-port-xxhdpi")
class GuardedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failingPageShowsTheErrorCard() {
        compose.setContent { WeemTheme(dark = false) { GuardedPage<Unit>("test", Lang.TR, build = { error("kırık") }) { } } }
        compose.onNodeWithText(Str.PAGE_FAILED.get(Lang.TR)).assertIsDisplayed()
        compose.onNodeWithText(Str.CRASH_COPY.get(Lang.TR)).assertIsDisplayed()
        compose.onNodeWithText(Str.CRASH_SHARE.get(Lang.TR)).assertIsDisplayed()
        compose.onNodeWithText(Str.CRASH_HIDE.get(Lang.TR)).assertDoesNotExist()
    }

    @Test fun crashCardHides() {
        var hidden = false
        val j = CrashReport.json(1_760_000_000_000L, "1.0.0", "main", IllegalStateException("boom"))
        compose.setContent { WeemTheme(dark = true) { CrashCard(j, Lang.AZ) { hidden = true } } }
        compose.onNodeWithText(Str.CRASH_HINT.get(Lang.AZ)).assertIsDisplayed()
        compose.onNodeWithText(Str.CRASH_HIDE.get(Lang.AZ)).performClick()
        assertTrue(hidden)
    }
}
