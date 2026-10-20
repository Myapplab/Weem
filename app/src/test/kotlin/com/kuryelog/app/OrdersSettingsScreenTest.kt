package com.kuryelog.app

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A4.2 (S20 (b)): Siparişler ayarları JVM'de çizilir; yazılan tercihler Rawstats/WEEM anahtarlarıyla `kuryelog` dosyasına gider. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-port-xxhdpi")
class OrdersSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val wolt = "com.wolt.courierapp"

    private fun show(lang: Lang = Lang.TR) {
        Store.prefs(ctx).edit().clear().putStringSet("targets", setOf(wolt)).commit()
        compose.setContent { WeemTheme(dark = false) { OrdersSettingsScreen(lang) {} } }
    }

    /** Odak vermeden metni kurar (Robolectric'te odaklı alanın imleç animasyonu Compose'u hiç boşa çıkarmaz). */
    private fun type(text: String) {
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }
        compose.waitForIdle()
    }

    @Test fun commissionIsWrittenToEveryPackageOfThePlatform() {
        show()
        compose.onNodeWithText("Wolt").assertIsDisplayed()
        compose.onNodeWithText("%0.0").assertIsDisplayed()
        compose.onNodeWithText("Wolt").performClick()
        type("12,5")
        compose.onNodeWithText("Kaydet").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("%12.5").assertIsDisplayed()
        assertEquals(12.5f, Store.prefs(ctx).getFloat("commission:$wolt", -1f), 0f)
    }

    @Test fun invalidCommissionKeepsTheDialogOpenWithTheRange() {
        show()
        compose.onNodeWithText("Wolt").performClick()
        type("150")
        compose.onNodeWithText("Kaydet").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Geçersiz değer: 0 ile 99.99 arasında bir sayı yaz.").assertIsDisplayed()
        assertFalse(Store.prefs(ctx).contains("commission:$wolt"))
    }

    @Test fun netAndGoalSettingsWriteAndClear() {
        show()
        compose.onNodeWithText("kWh fiyatı (₼)").performClick(); type("0,12"); compose.onNodeWithText("Kaydet").performClick(); compose.waitForIdle()
        compose.onNodeWithText("0.12").assertIsDisplayed()
        assertEquals(0.12f, Store.prefs(ctx).getFloat("economics.kwhPrice", -1f), 0f)
        compose.onNodeWithText("Şarj verimi (yüzde)").performClick(); type("85"); compose.onNodeWithText("Kaydet").performClick(); compose.waitForIdle()
        assertEquals(85f, Store.prefs(ctx).getFloat("economics.chargeEfficiencyPct", -1f), 0f)
        compose.onNodeWithText("Günlük net hedef (₼)").performClick(); type("120"); compose.onNodeWithText("Kaydet").performClick(); compose.waitForIdle()
        assertEquals(120f, Store.prefs(ctx).getFloat("economics.dailyGoalAzn", -1f), 0f)
        // Boş kaydetmek değeri siler.
        compose.onNodeWithText("Günlük net hedef (₼)").performClick(); type(""); compose.onNodeWithText("Kaydet").performClick(); compose.waitForIdle()
        assertFalse(Store.prefs(ctx).contains("economics.dailyGoalAzn"))
        compose.onNodeWithText("kWh fiyatı (₼)").performClick(); type(""); compose.onNodeWithText("Kaydet").performClick(); compose.waitForIdle()
        assertFalse(Store.prefs(ctx).contains("economics.kwhPrice"))
    }

    @Test fun screenTogglesAndChoicesUseRawstatsKeys() {
        show()
        compose.onNodeWithText("Ekran üstü panel görünür").performClick()
        compose.waitForIdle()
        assertFalse(Store.prefs(ctx).getBoolean("overlay", true))
        compose.onNodeWithText("Restoran balonu genişliği").performClick()
        compose.onNodeWithText("%100").performClick()
        compose.waitForIdle()
        assertEquals(100, Store.prefs(ctx).getInt("restaurantBubbleWidthPct", -1))
        compose.onNodeWithText("Müşteri kartı boyutu").performClick()
        compose.onNodeWithText("%70").performClick()
        compose.waitForIdle()
        assertEquals(70, Store.prefs(ctx).getInt("customerCardSizePct", -1))
        assertTrue(Store.prefs(ctx).getBoolean("ocr", true))
    }

    @Test fun azerbaijaniTextsAndNoPlatformHint() {
        Store.prefs(ctx).edit().clear().commit()
        compose.setContent { WeemTheme(dark = false) { OrdersSettingsScreen(Lang.AZ) {} } }
        compose.onNodeWithText("İzlənən Wolt və ya Bolt tətbiqi yoxdur").assertIsDisplayed()
        compose.onNodeWithText("Gündəlik xalis hədəf (₼)").assertIsDisplayed()
    }

    // ---- A3.11 (S16 (a)): gölge mod anahtarı yalnız .dev (hata ayıklanabilir) yapıda ----

    @Test fun shadowSwitchIsHiddenInReleaseBuilds() {
        ctx.applicationInfo.flags = ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE.inv()
        show()
        compose.onNodeWithText("Gölge mod (Rawstats ile yan yana)").assertDoesNotExist()
    }

    @Test fun shadowSwitchWritesTheShadowModePrefInDevBuilds() {
        ctx.applicationInfo.flags = ctx.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
        show()
        compose.onNodeWithText("Gölge mod (Rawstats ile yan yana)").assertIsDisplayed()
        assertFalse(Store.prefs(ctx).getBoolean("shadowMode", false))
        compose.onNodeWithText("Gölge mod (Rawstats ile yan yana)").performClick()
        compose.waitForIdle()
        assertTrue(Store.prefs(ctx).getBoolean("shadowMode", false))
        compose.onNodeWithText("Gölge mod (Rawstats ile yan yana)").performClick()
        compose.waitForIdle()
        assertFalse(Store.prefs(ctx).getBoolean("shadowMode", false))
    }
}
