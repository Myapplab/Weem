package com.kuryelog.app.map

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bölge bilgisi sayfası (MAP.md 5.4 "Yeni"): iki dilde başlıklar ve açıklamalar, km etiketleri, OSM bağlantı biçimi. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RegionInfoSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun baslikAciklamaKmEtiketiVeOsmBaglantisi() {
        val opened = ArrayList<String>()
        compose.setContent { WeemTheme(false) { RegionInfoContent(Lang.TR, onOpenUrl = { opened += it }) } }
        compose.onAllNodes(hasText("Harita bölgeleri")).assertCountEquals(1)
        compose.onAllNodes(hasText("Merkez Bakü · ~17 × 12 km")).assertCountEquals(1)
        compose.onAllNodes(hasText("Geniş Bakü · ~57 × 41 km")).assertCountEquals(1)
        compose.onAllNodes(hasText("Uzak siparişlere gidiyorsan bunu seç.", substring = true)).assertCountEquals(1)
        compose.onNodeWithText("Geniş").performClick()
        assertEquals("https://www.openstreetmap.org/?minlon=49.58&minlat=40.28&maxlon=50.25&maxlat=40.65&box=yes", opened.single())
    }

    @Test
    fun azericeBaslikVeAciklama() {
        compose.setContent { WeemTheme(false) { RegionInfoContent(Lang.AZ, onOpenUrl = {}) } }
        compose.onAllNodes(hasText("Xəritə əraziləri")).assertCountEquals(1)
        compose.onAllNodes(hasText("Mərkəzi Bakı · ~17 × 12 km")).assertCountEquals(1)
        compose.onAllNodes(hasText("Böyük Bakı · ~57 × 41 km")).assertCountEquals(1)
        compose.onAllNodes(hasText("Uzaq sifarişlərə gedirsənsə bunu seç.", substring = true)).assertCountEquals(1)
    }
}
