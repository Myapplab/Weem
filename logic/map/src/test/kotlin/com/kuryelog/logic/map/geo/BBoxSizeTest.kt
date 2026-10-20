package com.kuryelog.logic.map.geo

import org.junit.Assert.assertEquals
import org.junit.Test

/** Bölge ekranı etiketleri (MAP.md 5.4): R = 6 371 000 m haversine. */
class BBoxSizeTest {
    @Test
    fun `merkez 17 x 12 km, geniş 57 x 41 km`() {
        assertEquals(16.952, Baku.centralBox.widthKm, 0.005)
        assertEquals(12.231, Baku.centralBox.heightKm, 0.005)
        assertEquals("~17 × 12 km", Baku.centralBox.sizeKmLabel)
        assertEquals(56.836, Baku.wideBox.widthKm, 0.005)
        assertEquals(41.142, Baku.wideBox.heightKm, 0.005)
        assertEquals("~57 × 41 km", Baku.wideBox.sizeKmLabel)
    }
}
