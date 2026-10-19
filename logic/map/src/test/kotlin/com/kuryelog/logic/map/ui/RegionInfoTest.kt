package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.Baku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Y21 bölge bilgisi (MAP.md 5.4): km etiketleri (R = 6 371 000 m haversine), OSM bağlantısı ve iki dilli metinler birebir. */
class RegionInfoTest {
    @Test
    fun `km etiketleri merkez 17 × 12, geniş 57 × 41`() {
        assertEquals(16.952, RegionInfo.widthKm(Baku.centralBox), 0.001)
        assertEquals(12.231, RegionInfo.heightKm(Baku.centralBox), 0.001)
        assertEquals(56.836, RegionInfo.widthKm(Baku.wideBox), 0.001)
        assertEquals(41.142, RegionInfo.heightKm(Baku.wideBox), 0.001)
        assertEquals("~17 × 12 km", RegionInfo.sizeLabel(Baku.centralBox))
        assertEquals("~57 × 41 km", RegionInfo.sizeLabel(Baku.wideBox))
        assertEquals("Merkez Bakü · ~17 × 12 km", RegionInfo.central(Lang.TR))
        assertEquals("Mərkəzi Bakı · ~17 × 12 km", RegionInfo.central(Lang.AZ))
        assertEquals("Geniş Bakü · ~57 × 41 km", RegionInfo.wide(Lang.TR))
        assertEquals("Böyük Bakı · ~57 × 41 km", RegionInfo.wide(Lang.AZ))
    }

    @Test
    fun `OSM bağlantı biçimi ve karo adresi`() {
        assertEquals("https://www.openstreetmap.org/?minlon=49.76&minlat=40.335&maxlon=49.96&maxlat=40.445&box=yes", RegionInfo.osmLink(Baku.centralBox))
        assertEquals("https://www.openstreetmap.org/?minlon=49.58&minlat=40.28&maxlon=50.25&maxlat=40.65&box=yes", RegionInfo.osmLink(Baku.wideBox))
        assertEquals("https://tile.openstreetmap.org/{z}/{x}/{y}.png", RegionInfo.OSM_TILES)
    }

    @Test
    fun `iki dilli metinler`() {
        assertEquals("Harita bölgeleri", RegionText.TITLE.tr); assertEquals("Xəritə əraziləri", RegionText.TITLE.az)
        assertTrue(RegionText.CENTRAL_BODY.tr.startsWith("Badamdar, Bayıl, Sabail, İçerişehir"))
        assertTrue(RegionText.WIDE_BODY.az.endsWith("Uzaq sifarişlərə gedirsənsə bunu seç."))
        assertEquals("Turuncu sınırın dışındaki hedefler için ek harita verisi gerekir.", RegionText.NOTE.tr.substringAfter("kullanılır. "))
    }
}
