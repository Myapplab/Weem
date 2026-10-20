package com.kuryelog.logic.map.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Pano tekrar önleme (MAP.md 8.3; A6.6 yeni): FNV-1a 32 bit, UTF-16 birimleri; bilinen vektörler ve 0…2³²−1 aralığı. */
class ClipboardHashTest {
    @Test
    fun `FNV-1a 32 bit bilinen değerler ve UTF-16 birimleri`() {
        assertEquals(0x811c9dc5L, ClipboardHash.fnv1a(""))
        assertEquals(0xe40c292cL, ClipboardHash.fnv1a("a"))
        assertEquals(0xbf9cf968L, ClipboardHash.fnv1a("foobar"))
        // ə (U+0259) tek UTF-16 birimi: baytlara değil koda göre karıştırılır
        assertEquals(((0x811c9dc5L xor 0x259L) * 0x01000193L) and 0xFFFFFFFFL, ClipboardHash.fnv1a("ə"))
        assertNotEquals(ClipboardHash.fnv1a("Nizami 5"), ClipboardHash.fnv1a("Nizami 6"))
    }
}
