package com.kuryelog.app.map

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.roads.WwRowView
import com.kuryelog.logic.map.roads.WwTone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Yollarım (A6.11, MAP.md 9.8): sekmeler, kalite sayfası 👍, ters yön satır menüsü, eşik adımlayıcı, "Tümünü sil" onayı. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoadsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sekmelerKaliteSayfasiTersYonMenusuVeYolYok() {
        val calls = ArrayList<String>()
        val state = RoadsScreenState(
            quality = listOf(QualityRowView(7, "Nizami", "%25", Color.Red, "Elle girildi", 25, "Kalite %25 (tahmin)", false)),
            ww = listOf(WwRowView(9, "Ters küçə", "4 geçiş · 2.5 sorun · kaçınılıyor", "%63", WwTone.RED, true, true)),
            ghosts = listOf("Yox küçə" to "Bir parça · 01.10.2026"),
        )
        val actions = RoadsActions(
            rate = { w, p, _ -> calls += "rate $w $p" },
            wwAdd = { w, p, _ -> calls += "ww $w $p" },
            threshold = { calls += "thr $it" },
            clearGhosts = { calls += "clear" },
        )
        compose.setContent { WeemTheme(false) { RoadsScreen(state, Lang.TR, actions, onBack = {}) } }
        compose.onAllNodes(hasText("Elle girilen puanlar")).assertCountEquals(1)
        compose.onAllNodes(hasText("Trafik kayıtları")).assertCountEquals(1)
        compose.onNodeWithTag("quality-row-7").performClick()
        compose.onNodeWithText("👍 Kaliteli yüzey · kullanıcı puanı").performClick()
        compose.onNodeWithTag("roads-tab-1").performClick()
        compose.onAllNodes(hasText("4 geçiş · 2.5 sorun · kaçınılıyor")).assertCountEquals(1)
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithTag("ww-row-9").performClick()
        compose.onNodeWithText("Sorunlu geçiş ekle").performClick()
        compose.onNodeWithTag("roads-tab-2").performClick()
        compose.onAllNodes(hasText("Bir parça · 01.10.2026")).assertCountEquals(1)
        compose.onNodeWithText("Tümünü sil").performClick()
        compose.onNodeWithText("Sil").performClick()
        compose.waitForIdle()
        assertEquals(listOf("rate 7 90", "thr 55.0", "ww 9 true", "clear"), calls)
    }
}
