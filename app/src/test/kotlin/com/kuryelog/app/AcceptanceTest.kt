package com.kuryelog.app

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.kuryelog.app.map.MapMetricsState
import com.kuryelog.app.map.MapTab
import com.kuryelog.app.map.MapTabState
import com.kuryelog.app.map.MapTestFixtures
import com.kuryelog.app.map.WeatherView
import com.kuryelog.logic.app.Tab
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.day.OrderPhase
import com.kuryelog.logic.map.day.ShiftEngine
import com.kuryelog.logic.map.nav.RouteProgress
import com.kuryelog.logic.map.ride.BatteryState
import com.kuryelog.logic.app.ThemeMode
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A10.4d kabul ölçütü (`docs/TASARIM.md` 14): kabuk 360 dp genişlik ve 1,3 yazı ölçeğinde açılır; her sekme (Harita, Siparişler,
 * Scooter, Uzaktan, Daha fazla) çizilir ve görünen her dokunulur öğe en az 48 dp yüksek (ve genişliktedir).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h740dp-port-xxhdpi")
class AcceptanceTest {
    @get:Rule val compose = createComposeRule()

    private fun small(lang: Lang, dark: Boolean): List<String> {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.3f)) {
                WeemTheme(dark = dark) { WeemApp(lang, lang.key, if (dark) ThemeMode.DARK else ThemeMode.LIGHT, "1.0.0", {}, {}) }
            }
        }
        val found = mutableListOf<String>()
        for (tab in listOf(Tab.ORDERS, Tab.SCOOTER, Tab.REMOTE, Tab.MORE, Tab.MAP)) {
            compose.onNodeWithTag("tab_${tab.key}").performClick()
            compose.waitForIdle()
            found += undersized(48f, tab.key)
        }
        return found
    }

    /** [iconMin]: metni olmayan (yalnız içerik açıklamalı) simge denetimleri için alt sınır. */
    private fun undersized(min: Float, where: String, iconMin: Float = min): List<String> {
        val density = compose.density.density
        val clickable = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = false)
            .fetchSemanticsNodes(atLeastOneRootRequired = true)
        return clickable.mapNotNull { n ->
            // Kaydırma alanının kenarında kırpılan öğe görünür kısmıyla değil, kendi boyuyla (ya da genişletilmiş dokunma alanıyla) ölçülür.
            val b = n.touchBoundsInRoot
            if (b.height <= 0f || b.width <= 0f) return@mapNotNull null
            val h = maxOf(n.size.height.toFloat(), b.height) / density
            val w = maxOf(n.size.width.toFloat(), b.width) / density
            val need = if (n.config.getOrNull(SemanticsProperties.Text) == null && n.config.getOrNull(SemanticsProperties.ContentDescription) != null) iconMin else min
            if (h < need - .5f || w < need - .5f) "$where: ${label(n)} ${w.toInt()}×${h.toInt()} dp" else null
        }
    }

    private fun label(n: SemanticsNode): String =
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: n.config.getOrNull(SemanticsProperties.TestTag) ?: "?"

    /** Sürüş sırasında harita: dokunulur her öğe ≥ 48 dp, simge denetimleri (konum, rota, katman…) ≥ 56 dp (`docs/TASARIM.md` 9). */
    @Test fun rideHudTargetsAreRideSized() {
        val p = MapTestFixtures.plan()
        val metrics = MapMetricsState(
            plan = p, measure = RouteProgress.measure(p, MapTestFixtures.fix(200.0)), battery = BatteryState(livePercent = 78.0),
            scooter = MapTestFixtures.scooterLive(), nowMs = MapTestFixtures.AT,
        )
        val e = ShiftEngine().also { it.startDay(MapTestFixtures.AT); it.startOrder(MapTestFixtures.AT); it.stageFromFeed(OrderPhase.TO_CUSTOMER, MapTestFixtures.AT) }
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.3f)) {
                WeemTheme(dark = true) {
                    MapTab(
                        MapTabState(DayView(e.s, null), metrics = metrics, weather = WeatherView(report = MapTestFixtures.report4()), nowMs = MapTestFixtures.AT),
                        Lang.TR, mapSurface = { Box(Modifier.fillMaxSize()) },
                    )
                }
            }
        }
        val small = undersized(48f, "ride", iconMin = 56f)
        assertTrue(small.joinToString("\n"), small.isEmpty())
    }

    @Test fun everyTabOpensAtLargeFontAndTargetsAreBigEnoughTr() {
        val small = small(Lang.TR, dark = false)
        assertTrue(small.joinToString("\n"), small.isEmpty())
    }

    @Test fun everyTabOpensAtLargeFontAndTargetsAreBigEnoughAzNight() {
        val small = small(Lang.AZ, dark = true)
        assertTrue(small.joinToString("\n"), small.isEmpty())
    }
}
