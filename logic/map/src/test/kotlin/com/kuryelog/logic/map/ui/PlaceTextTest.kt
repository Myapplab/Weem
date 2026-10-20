package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.Baku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yer düzeltme penceresi kuralları ve bölge bilgisi (MAP.md 5.3, 5.4). */
class PlaceTextTest {
    @Test
    fun `ad kırpılır, kelime başı büyür, 100 karakter`() {
        assertEquals("İzmir Doner Tantuni", PoiEditRules.normalizeName("  izmir   doner tantuni "))
        assertEquals("İzmir", PoiEditRules.normalizeName("izmir", Lang.TR))
        assertEquals(100, PoiEditRules.normalizeName("a".repeat(150)).length)
    }

    @Test
    fun `tür seçici beş tür, s ve l gelirse restoran`() {
        assertEquals(listOf('r', 'f', 'c', 't', 'm'), PoiEditRules.KINDS)
        assertEquals('r', PoiEditRules.kindOrDefault('s'))
        assertEquals('r', PoiEditRules.kindOrDefault('l'))
        assertEquals('m', PoiEditRules.kindOrDefault('m'))
        assertEquals("AVM", PoiEditRules.kindLabel('m').tr)
        assertEquals("Ticarət mərkəzi", PoiEditRules.kindLabel('m').az)
    }

    @Test
    fun `bölge başlıkları ve OSM bağlantısı`() {
        assertEquals("Merkez Bakü · ~17 × 12 km", PlaceText.CENTRAL_TITLE.format(Lang.TR, Baku.centralBox.sizeKmLabel))
        assertEquals("Böyük Bakı · ~57 × 41 km", PlaceText.WIDE_TITLE.format(Lang.AZ, Baku.wideBox.sizeKmLabel))
        assertTrue(RegionInfo.osmBoxUrl(Baku.wideBox).startsWith("https://www.openstreetmap.org/?minlon=49.58&minlat=40.28&maxlon=50.25&maxlat=40.65&box=yes"))
    }
}
