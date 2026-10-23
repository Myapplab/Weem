package com.kuryelog.app.map

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.input.SearchView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S37 regresyonu: kabuk Harita sekmesini bir Column'a koyar; arama katmanı haritanın altına (ekran dışına) düşüyordu, yazılan aramanın sonucu
 * görünmüyordu. [MapScreenLayers] içinde arama haritanın üstünde görünür.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w380dp-h844dp-port-xxhdpi")
class MapScreenLayersTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aramaKatmaniKabukSutunundaHaritaninUstundeGorunur() {
        compose.setContent {
            WeemTheme(false) {
                Column(Modifier.fillMaxSize()) {
                    MapScreenLayers {
                        MapTab(MapTabState(DayView(DayState(), null), nowMs = MapTestFixtures.AT), Lang.TR)
                        SearchOverlay(SearchView(), Lang.TR, onQuery = {}, onSubmit = {}, onPick = {}, onClose = {})
                    }
                }
            }
        }
        compose.onNodeWithTag("search-overlay").assertIsDisplayed()
        compose.onNodeWithTag("search-field").assertIsDisplayed()
    }
}
