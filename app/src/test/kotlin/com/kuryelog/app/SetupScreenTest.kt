package com.kuryelog.app

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.kuryelog.logic.app.Vendor
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** G5 (A7.2): ilk açılış kurulumu JVM'de çizilir; dil anında uygulanır, izinler kartı Android'in durumunu okur, atlama kurulumu bitirir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-port-xxhdpi")
class SetupScreenTest {
    @get:Rule val compose = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun show(onDone: () -> Unit = {}) {
        compose.setContent {
            var setting by androidx.compose.runtime.remember { mutableStateOf("") }
            val lang = Lang.resolve(setting, "tr")
            WeemTheme(dark = false) { SetupScreen(lang, setting, { setting = it }, onDone) }
        }
    }

    @Test fun languageComesFirstAndAppliesAtOnce() {
        show()
        compose.onNodeWithText("Adım 1/4").assertIsDisplayed()
        compose.onNodeWithText("WEEM'e hoş geldin").assertIsDisplayed()
        compose.onNodeWithText("Azərbaycanca").performClick()
        compose.onNodeWithText("WEEM-ə xoş gəldin").assertIsDisplayed()
        compose.onNodeWithText("Addım 1/4").assertIsDisplayed()
        compose.onNodeWithText("İrəli").assertIsDisplayed()
    }

    @Test fun permissionsStepShowsWhatIsMissing() {
        Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "${ctx.packageName}/com.kuryelog.app.WatchService")
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        show()
        compose.onNodeWithText("İleri").performClick()
        compose.onNodeWithText("Adım 2/4").assertIsDisplayed()
        compose.onNodeWithText("Ekran okuma (Erişilebilirlik)").assertIsDisplayed()
        // Erişilebilirlik ve konum var; bildirim okuma ve "her zaman" konumu yok.
        compose.onNodeWithText("! 2/4").assertIsDisplayed()
        compose.onNodeWithText("Konum: her zaman (isteğe bağlı)").assertIsDisplayed()
        compose.onNodeWithText("Pil optimizasyonu: AÇIK · Android uygulamayı kısıtlayabilir").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Geri").performClick()
        compose.onNodeWithText("Adım 1/4").assertIsDisplayed()
    }

    @Test fun skippingFinishesTheSetup() {
        var done = 0
        show { done++ }
        compose.onNodeWithText("Kurulumu atla").performClick()
        assertEquals(1, done)
    }

    @Test fun batteryGuideFollowsThePhone() {
        compose.setContent { WeemTheme(dark = false) { androidx.compose.foundation.layout.Column { BatteryGuideCard(Lang.TR, vendor = Vendor.XIAOMI); BatteryGuideCard(Lang.AZ, vendor = Vendor.PIXEL) } } }
        compose.onNodeWithText("Xiaomi · Redmi · POCO (MIUI / HyperOS)").assertIsDisplayed()
        compose.onNodeWithText("1. Uygulama bilgisi → Otomatik başlatma: AÇ.").assertIsDisplayed()
        compose.onNodeWithText("Otomatik başlatma ayarı").assertIsDisplayed()
        compose.onNodeWithText("Google Pixel").assertExists()
        compose.onNodeWithText("1. Tətbiq məlumatı → Tətbiqin batareya istifadəsi → Məhdudiyyətsiz (Unrestricted).").assertExists()
    }

    @Test fun setupFlagIsAKuryelogPreference() {
        val prefs = ShellPrefs(ctx)
        prefs.setupDone = false
        assertTrue(!prefs.setupDone)
        prefs.setupDone = true
        assertTrue(ctx.getSharedPreferences("kuryelog", Context.MODE_PRIVATE).getBoolean("weem.setupDone", false))
    }
}
