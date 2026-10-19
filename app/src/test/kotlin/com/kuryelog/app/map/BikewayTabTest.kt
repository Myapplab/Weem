package com.kuryelog.app.map

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `bikeway_e13_test.dart` "Yollarım → Bisiklet: not, katman anahtarı, ağırlık, elle işaretler ve geri alma" (1; A6.2b, MAP.md 5.2, 16.3): not, anahtar, `×0.85`, "OSM: 3 ",
 * liste, çöp kutusu, anahtar kapatma, AZ not. Riverpod/graf yerine saf durum ve geri çağrılar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BikewayTabTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun notAnahtarAgirlikElleIsaretlerVeGeriAlma() {
        var state by mutableStateOf(BikewayTabState(osmWays = 3, marks = listOf(302L to "Batı küçəsi")))
        var lang by mutableStateOf(Lang.TR)
        var layer: Boolean? = null
        compose.setContent {
            WeemTheme(false) {
                BikewayTab(state, lang, onLayerChange = { layer = it; state = state.copy(layerOn = it) }, onRemoveMark = { id -> state = state.copy(marks = state.marks.filterNot { it.first == id }) })
            }
        }
        compose.onAllNodes(hasText("Bakü'de OSM bisiklet yolu kaydı az; eksikleri elle işaretleyebilirsin.")).assertCountEquals(1)
        compose.onAllNodes(hasText("Haritada bisiklet yolları")).assertCountEquals(1)
        compose.onAllNodes(hasText("×0.85")).assertCountEquals(1)
        compose.onAllNodes(hasText("OSM: 3 ", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("Batı küçəsi")).assertCountEquals(1)
        compose.onNodeWithContentDescription("İşareti kaldır").performClick()
        compose.waitForIdle()
        assertTrue(state.marks.isEmpty())
        compose.onAllNodes(hasText("Batı küçəsi")).assertCountEquals(0)
        compose.onNodeWithText("Haritada bisiklet yolları").performClick()
        compose.waitForIdle()
        assertEquals(false, layer)
        lang = Lang.AZ
        compose.waitForIdle()
        compose.onAllNodes(hasText("Bakıda OSM-də velosiped yolu qeydləri azdır; çatışmayanları əl ilə qeyd edə bilərsən.")).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("İşareti kaldır")).assertCountEquals(0)
    }
}
