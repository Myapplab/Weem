package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Toast metinleri TR/AZ (MAP.md 11.14, M-11-1). */
class DayTextTest {
    private fun toast(type: String, data: Map<String, Any?> = emptyMap(), lang: Lang = Lang.TR) = DayToasts.of(DayEvent(type, 0, data), lang)

    @Test
    fun `varış çıkış teslim ışık metinleri`() {
        assertEquals("Restorana vardın — bekleme başladı", toast("arrive_restaurant")!!.first.text)
        assertEquals("Restorandan ayrıldın · bekleme bilinmiyor", toast("leave_restaurant", mapOf("waitS" to null))!!.first.text)
        assertEquals("Restorandan ayrıldın · bekleme 5:00", toast("leave_restaurant", mapOf("waitS" to 300))!!.first.text)
        assertEquals("Sipariş 2 teslim edildi · 12:05", toast("delivered", mapOf("n" to 2, "totalS" to 725))!!.first.text)
        assertEquals("Sifariş 2 çatdırıldı · 12:05", toast("delivered", mapOf("n" to 2, "totalS" to 725), Lang.AZ)!!.first.text)
        assertTrue(toast("delivered", mapOf("n" to 1, "totalS" to 60))!!.second)
    }

    @Test
    fun `iptal metni yalnız aşama kaynaklı, öteki olaylar toast üretmez`() {
        assertEquals("Sipariş 3 alınmadan bitti — iptal sayıldı", toast("order_cancel", mapOf("n" to 3, "source" to "kuryeplus"))!!.first.text)
        assertNull(toast("order_cancel", mapOf("n" to 3)))
        for (t in listOf("stopping", "order_target", "customer_journey", "day_end", "home_return_start")) assertNull(t, toast(t))
    }

    @Test
    fun `gün başı nedenden seçilir`() {
        assertEquals("Gün başladı — km, süre ve durmalar kaydediliyor", toast("day_start", mapOf("reason" to "manual"))!!.first.text)
        assertEquals("Evden çıkıldı — gün otomatik başladı", toast("day_start", mapOf("reason" to "home_departure"))!!.first.text)
        assertEquals("Eve döndün — gün otomatik bitti", toast("day_end", mapOf("reason" to "home_arrival"))!!.first.text)
    }
}
