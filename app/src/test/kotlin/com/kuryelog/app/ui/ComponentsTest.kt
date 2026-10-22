package com.kuryelog.app.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kuryelog.app.WeemTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A10.4d bileşen kitaplığı (`docs/TASARIM.md` 11, 14): dokunma hedefleri ≥ 48 dp (sürüşte 56), simge düğmelerinin içerik açıklaması,
 * satırın tamamının dokunulması, anahtar/radyo/sekme anlamları, geri tuşu; 360 dp ve 1,3 yazı ölçeğinde uzun Azerice metin kesilmez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h740dp-port-xxhdpi")
class ComponentsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { WeemTheme(dark = dark) { content() } }

    @Test fun buttonsMeetTouchTargets() {
        var taps = 0
        show {
            Column {
                WButton("Vardiyayı BAŞLAT", { taps++ }, Modifier.testTag("b48"))
                WButton("Vardiyayı BAŞLAT", { taps++ }, Modifier.testTag("b56"), ride = true, icon = WeemIcons.PlayArrow)
                WIconButton(WeemIcons.Settings, "Ayarlar", { taps++ })
                Fab(WeemIcons.MyLocation, "Konumuma dön", { taps++ })
            }
        }
        compose.onNodeWithTag("b48").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("b56").assertHeightIsAtLeast(56.dp).performClick()
        compose.onNodeWithContentDescription("Ayarlar").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Konumuma dön").assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(4, taps)
    }

    @Test fun wholeRowIsTheTargetAndSwitchRadioStatesAreExposed() {
        var opened = 0
        var on by mutableStateOf(false)
        var pick by mutableIntStateOf(0)
        show(dark = true) {
            Column {
                SettingsGroup("Ayarlar") {
                    ListRow("Ev konumu", subtitle = "Ev konumu ve otomatik vardiya", icon = WeemIcons.Home, chevron = true, onClick = { opened++ })
                    SwitchRow("Hava bildirimi", on, { on = it }, tag = "sw")
                }
                SettingsGroup("Dil", selectableGroup = true) {
                    RadioRow("Türkçe", pick == 0, { pick = 0 }, tag = "r0")
                    RadioRow("Azərbaycanca", pick == 1, { pick = 1 }, tag = "r1")
                }
            }
        }
        compose.onNodeWithText("Ev konumu").performClick()
        assertEquals(1, opened)
        compose.onNodeWithTag("sw").assertHeightIsAtLeast(56.dp).assertIsOff().performClick()
        compose.onNodeWithTag("sw").assertIsOn()
        compose.onNodeWithTag("r0").assertIsSelected()
        compose.onNodeWithTag("r1").assertIsNotSelected().performClick()
        compose.onNodeWithTag("r1").assertIsSelected()
    }

    @Test fun navBarSelectsTabsAndTopBarGoesBack() {
        var tab by mutableStateOf("map")
        var back = 0
        val items = listOf(
            NavItem("map", "Harita", WeemIcons.Map, WeemIcons.MapFilled, tag = "tab-map"),
            NavItem("orders", "Siparişler", WeemIcons.ReceiptLong, WeemIcons.ReceiptLongFilled, tag = "tab-orders"),
        )
        show {
            Column {
                WeemTopBar("Kayıtlar", onBack = { back++ }, backLabel = "Geri")
                WeemNavBar(items, tab, { tab = it })
            }
        }
        compose.onNodeWithTag("tab-map").assertIsSelected()
        compose.onNodeWithTag("tab-orders").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("tab-orders").assertIsSelected()
        compose.onNodeWithContentDescription("Geri").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(2, back) }
    }

    @Test fun segmentedAndChipsExposeSelection() {
        var seg by mutableIntStateOf(0)
        var chip by mutableStateOf(false)
        show {
            Column {
                Segmented(listOf("Siparişler", "Teklif puanı"), seg, { seg = it }, tags = listOf("s0", "s1"))
                WChip("Wolt", chip, { chip = !chip }, Modifier.testTag("chip"))
            }
        }
        compose.onNodeWithTag("s0").assertIsSelected()
        compose.onNodeWithTag("s1").assertHeightIsAtLeast(40.dp).performClick()
        compose.onNodeWithTag("s1").assertIsSelected()
        compose.onNodeWithTag("chip").assertHeightIsAtLeast(36.dp).performClick()
        compose.onNodeWithTag("chip").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test fun longAzerbaijaniTextWrapsAtLargeFontScale() {
        val long = "Vardiyanı BİTİR və günün bütün sifarişlərini hesabatda saxla"
        show {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.3f)) {
                Column(Modifier.width(360.dp)) {
                    WButton(long, {}, Modifier.testTag("long"), kind = BtnKind.DANGER, fill = true)
                    Banner("Ekran oxuma bağlıdır — sifarişlər yazılmır", tone = WTone.DANGER, onClick = {}, modifier = Modifier.testTag("ban"))
                    StatusPill("Restoranda", WTone.RESTAURANT, Modifier.testTag("pill"))
                }
            }
        }
        compose.onNodeWithText(long).assertIsDisplayed()
        compose.onNodeWithTag("long").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("ban").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("pill").assertIsDisplayed()
    }
}
