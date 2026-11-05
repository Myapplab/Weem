package com.kuryelog.logic.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S44: 2 telefon modunda arama uygulamasının numarası öteki telefona (yalnız açılışta, yalnız numara alanından, bir kez). */
class DialShareTest {
    @Test
    fun numaraAlanindanAranacakDizi() {
        assertEquals("+994501234567", DialShare.number("+994 50 123 45 67"))
        assertEquals("0501234567", DialShare.number("050-123-45-67"))
        assertEquals("+994124040000,,1234#", DialShare.number("‪+994 (12) 404 00 00,,1234#‬"))
        assertEquals("*111#1234567", DialShare.number("*111#1234567"))
        assertNull("ipucu metni", DialShare.number("Numara girin"))
        assertNull("kısa kod", DialShare.number("*111#"))
        assertNull("yarım yazılmış", DialShare.number("050 12"))
        assertNull("+ yalnız başta", DialShare.number("050+1234567"))
        assertNull(DialShare.number(""))
        assertNull("çok uzun", DialShare.number("1".repeat(21)))
    }

    @Test
    fun yalnizPencereAcildiktanSonrakiUcSaniyedeOkunur() {
        val d = DialShare()
        assertFalse(d.reading(1_000))
        assertNull("açılış görülmeden okunmaz", d.read(listOf("+994501234567"), 1_000))
        d.opened(10_000)
        assertTrue(d.reading(12_000))
        assertFalse(d.reading(13_001))
        assertNull("pencere geçti: elle yazılan numara gitmez", d.read(listOf("+994501234567"), 13_001))
    }

    @Test
    fun bulununcaPencereKapanirAyniNumara30SaniyedeBirKez() {
        val d = DialShare()
        d.opened(0)
        assertNull("alan henüz boş", d.read(listOf(""), 100))
        assertEquals("+994501234567", d.read(listOf("", "+994 50 123 45 67"), 300))
        assertNull("aynı açılışta ikinci numara yok", d.read(listOf("+994551112233"), 400))
        d.opened(10_000)
        assertNull("aynı numara 30 sn içinde yeniden gitmez", d.read(listOf("+994 50 123 45 67"), 10_100))
        d.opened(20_000)
        assertEquals("başka numara gider", "+994551112233", d.read(listOf("+994551112233"), 20_100))
        d.opened(60_000)
        assertEquals("+994501234567", d.read(listOf("+994501234567"), 60_100))
    }
}
