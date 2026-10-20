package com.kuryelog.app.map

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.NavState
import com.kuryelog.logic.map.nav.Stop
import com.kuryelog.logic.map.nav.StopConfidence
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteLeg
import com.kuryelog.logic.map.route.RoutePoint
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rota paneli (YolGo `BottomPanel`; MAP.md 7.11): bekleyen kart önce, etkin durak "dk · saat", varış kartı, hata + Tekrar, Bitir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-port-xxhdpi")
class RoutePanelTest {
    @get:Rule val compose = createComposeRule()

    private val p = GeoPoint(40.41, 49.86)
    private val leg = RouteLeg(
        listOf(RoutePoint(40.40, 49.86, 0.0), RoutePoint(40.41, 49.86, 0.0)), emptyList(), 1100.0, 0.0, 13 * 60.0, 10.0, 0.0, 0.0, 1.0,
        listOf("Nizami", "Füzuli", "Azadlıq", "Neftçilər"), p,
    )
    private val now = LocalDateTime.of(2026, 10, 20, 7, 32)

    private fun show(nav: NavState, lang: Lang = Lang.TR, actions: RoutePanelActions = RoutePanelActions()) =
        compose.setContent { WeemTheme(dark = false) { RoutePanel(nav, lang, dark = false, actions = actions, now = now) } }

    @Test
    fun etkinDurakSureSaatGuzergahVeDusukGuven() {
        val stop = Stop(p, "Nizami küçəsi 12", unitNote = "Mənzil 45", confidence = StopConfidence.LOW)
        show(NavState(stops = listOf(stop), plans = mapOf(RideMode.BATTERY to Plan(RideMode.BATTERY, listOf(0), listOf(leg)))))
        compose.onNodeWithTag("route-eta").assertIsDisplayed()
        compose.onNodeWithText("13 dk · 07:45").assertIsDisplayed()
        compose.onNodeWithText("Mənzil 45").assertIsDisplayed()
        compose.onNodeWithText("Güzergâh: Nizami → Füzuli → Azadlıq").assertIsDisplayed()
        compose.onNodeWithText("⚠ Adres emin değil — pini kontrol et").assertIsDisplayed()
        compose.onNodeWithTag("route-finish").assertIsDisplayed()
    }

    @Test
    fun bekleyenKartDurakEkleYeniRotaVeKapat() {
        val got = mutableListOf<String>()
        val actions = RoutePanelActions(acceptPending = { got += "accept-$it" }, dismissPending = { got += "dismiss" })
        show(NavState(stops = listOf(Stop(p, "eski")), pending = Stop(p, "Yeni adres")), Lang.AZ, actions)
        compose.onNodeWithText("Yeni ünvan gəldi").assertIsDisplayed()
        compose.onNodeWithText("Ünvan əlavə et").performClick()
        compose.onNodeWithText("Yeni marşrut").performClick()
        assertEquals(listOf("accept-true", "accept-false"), got)
    }

    @Test
    fun hataTekrarYalnizDurakVarken() {
        var retried = 0
        show(NavState(stops = listOf(Stop(p, "x")), error = "Yol tapılmadı"), actions = RoutePanelActions(retry = { retried++ }))
        compose.onNodeWithTag("route-error").assertIsDisplayed()
        compose.onNodeWithText("Tekrar").performClick()
        assertEquals(1, retried)
        assertTrue(NavState(error = "x").hasPanelInfo())
        assertFalse(NavState(busy = true).hasPanelInfo())
    }

    @Test
    fun varisKartiKapiNotuIleVeKapanir() {
        var closed = 0
        show(NavState(arrived = Stop(p, "Nizami 12", unitNote = "Kod 1234")), actions = RoutePanelActions(dismissArrived = { closed++ }))
        compose.onNodeWithText("Vardın: Nizami 12").assertIsDisplayed()
        compose.onNodeWithText("Kod 1234").assertIsDisplayed()
        compose.onNodeWithText("✕").performClick()
        assertEquals(1, closed)
    }
}
