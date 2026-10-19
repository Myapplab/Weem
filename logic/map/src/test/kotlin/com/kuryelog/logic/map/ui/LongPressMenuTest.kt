package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.LongPressAction.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Y1 karakterizasyonu (MAP.md 6.7): uzun basış satırlarının koşulları ve sırası; yüzey türü satırı (Y13) yok. */
class LongPressMenuTest {
    private fun road(main: Boolean = false, oneway: Boolean = false, name: String = "Nizami", bike: BikewayMenuAction = BikewayMenuAction.MARK, ww: Boolean = false, blocked: Boolean = false) =
        PressedRoad(name, main, oneway, ww, blocked, bike, badRatioTitle = "Kötü kesim %12")

    @Test
    fun `yol yokken yalnız genel satırlar`() {
        val rows = LongPressMenu.rows(null, null, Lang.TR)
        assertEquals(listOf(GO_TO, ADD_STOP, PICK_AS_RESTAURANT, ADD_TASK, CURB_WAIT, CURB_REMOVE, LIGHT_HERE, NO_LIGHT, POI_EDIT), rows.map { it.action })
        assertEquals("Buraya işletme ekle", rows.last().title)
        assertEquals("Yakındaki ışıkla karıştırma", rows[4].subtitle)
    }

    @Test
    fun `ara sokak ve ana cadde satırları, sıra`() {
        val side = LongPressMenu.rows(road(), PressedPlace('r', "Dolma"), Lang.TR)
        assertEquals(
            listOf(GO_TO, ADD_STOP, GO_TO_RESTAURANT, ADD_TASK, CURB_WAIT, CURB_REMOVE, ROAD_HOURS, RATE_ROAD, BAD_RATIO, BLOCK_ROAD, BIKEWAY, NO_ROAD_HERE, NO_STREET, LIGHT_HERE, NO_LIGHT, POI_EDIT),
            side.map { it.action },
        )
        assertEquals("Restorana git: Dolma", side[2].title)
        assertEquals("İşletme adını düzelt", side.last().title)
        assertEquals("Dolma", side.last().subtitle)
        assertTrue(side.single { it.action == RATE_ROAD }.thumbs)
        val main = LongPressMenu.rows(road(main = true, oneway = true), null, Lang.TR).map { it.action }
        assertFalse(ROAD_HOURS in main)
        assertTrue(WRONG_WAY_SEGMENT in main)
        // Ana cadde iki yönlüyse ne saat izni ne kesim izni.
        val twoWay = LongPressMenu.rows(road(main = true), null, Lang.TR).map { it.action }
        assertFalse(ROAD_HOURS in twoWay || WRONG_WAY_SEGMENT in twoWay)
    }

    @Test
    fun `metinler duruma ve dile göre`() {
        val r = LongPressMenu.rows(road(main = true, oneway = true, name = "", ww = true, blocked = true, bike = BikewayMenuAction.ALREADY_OSM), PressedPlace('m', "28 Mall"), Lang.AZ)
        fun row(a: LongPressAction) = r.single { it.action == a }
        assertEquals("Bu nöqtəni restoran kimi seç", row(PICK_AS_RESTAURANT).title) // AVM restoran değil
        assertEquals("Bu hissədə əks istiqamət icazəsini sil", row(WRONG_WAY_SEGMENT).title)
        assertEquals("adsız yol · yalnız bu kəsişmələr arası, əsas küçə", row(WRONG_WAY_SEGMENT).subtitle)
        assertEquals("Yolun bağlanmasını ləğv et", row(BLOCK_ROAD).title)
        assertEquals("adsız yol · OSM-də artıq velosiped yoludur", row(BIKEWAY).subtitle)
        assertFalse(row(BIKEWAY).enabled)
        val tr = LongPressMenu.rows(road(bike = BikewayMenuAction.UNMARK), null, Lang.TR)
        assertEquals("Bisiklet yolu işaretini kaldır", tr.single { it.action == BIKEWAY }.title)
        assertEquals("Bu yolu tamamen kapat", tr.single { it.action == BLOCK_ROAD }.title)
        assertEquals("Elle yüzey puanı · Nizami", tr.single { it.action == RATE_ROAD }.title)
        assertEquals("Kaliteli yol" to "Kalitesiz yol", LongPressMenu.thumbHints(Lang.TR))
    }
}
