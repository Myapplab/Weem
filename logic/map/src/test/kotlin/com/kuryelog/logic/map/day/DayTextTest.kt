package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.TripStages.Stage
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.day.StageFixtures.NOW0
import com.kuryelog.logic.map.day.StageFixtures.TRIP_A
import com.kuryelog.logic.map.day.StageFixtures.feed
import com.kuryelog.logic.map.day.StageFixtures.trip
import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Toast metinleri TR/AZ (MAP.md 11.14, M-11-1): " (KuryePlus)" eki yok, iptal metni "alınmadan bitti"; karakterizasyon. */
class DayTextTest {
    private fun ev(type: String, data: Map<String, Any?> = emptyMap()) = DayEvent(type, NOW0, data)
    private fun text(e: DayEvent, lang: Lang) = DayText.toastFor(e, lang)?.text

    @Test
    fun `gün ve sipariş toast metinleri iki dilde`() {
        assertEquals("Gün başladı — km, süre ve durmalar kaydediliyor", text(ev("day_start", mapOf("reason" to "manual")), Lang.TR))
        assertEquals("Gün başladı — km, vaxt və işıqforlar qeyd olunur", text(ev("day_start", mapOf("reason" to "manual")), Lang.AZ))
        assertEquals("Evden çıkıldı — gün otomatik başladı", text(ev("day_start", mapOf("reason" to "home_departure")), Lang.TR))
        assertEquals("Evə qayıtdın — gün avtomatik bitdi", text(ev("day_end", mapOf("reason" to "home_arrival")), Lang.AZ))
        assertNull("elle bitişte toast yok", text(ev("day_end", mapOf("reason" to "manual")), Lang.TR))
        assertEquals("Sipariş 2 başladı → restorana", text(ev("order_start", mapOf("n" to 2)), Lang.TR))
        assertEquals("Sifariş 2 başladı → restorana", text(ev("order_start", mapOf("n" to 2)), Lang.AZ))
    }

    @Test
    fun `varış alış teslim metinleri, bilinmeyen bekleme uydurulmaz, ek yok`() {
        val feed = mapOf("source" to ShiftEngine.FEED_SOURCE, "auto" to true)
        assertEquals("Restorana vardın — bekleme başladı", text(ev("arrive_restaurant", feed), Lang.TR))
        assertEquals("Restorana çatdın — gözləmə başladı", text(ev("arrive_restaurant", feed), Lang.AZ))
        assertEquals("Restorandan ayrıldın · bekleme bilinmiyor", text(ev("leave_restaurant", feed + ("waitS" to null)), Lang.TR))
        assertEquals("Restorandan çıxdın · gözləmə məlum deyil", text(ev("leave_restaurant", mapOf("waitS" to null)), Lang.AZ))
        assertEquals("Restorandan ayrıldın · bekleme 5:07", text(ev("leave_restaurant", mapOf("waitS" to 307)), Lang.TR))
        assertEquals("Müşteriye vardın", text(ev("arrive_customer", feed), Lang.TR))
        assertEquals("Müştəriyə çatdın", text(ev("arrive_customer", feed), Lang.AZ))
        assertEquals("Sipariş 1 teslim edildi · 12:05", text(ev("delivered", mapOf("n" to 1, "totalS" to 725)), Lang.TR))
        assertEquals("Sifariş 1 çatdırıldı · ?", text(ev("delivered", mapOf("n" to 1, "totalS" to null)), Lang.AZ))
        assertEquals(Haptic.STRONG, DayText.toastFor(ev("delivered", mapOf("n" to 1)), Lang.TR)!!.haptic)
        assertEquals(6, DayText.toastFor(ev("delivered", mapOf("n" to 1)), Lang.TR)!!.seconds)
    }

    @Test
    fun `ışık beklemesi yalnız light türünde, iptal yalnız ekran okuması kaynaklıysa`() {
        val light = mapOf("kind" to "light", "start" to NOW0, "end" to NOW0 + 95_000)
        assertEquals("Trafik ışığında 1:35 bekledin", text(ev("stop", light), Lang.TR))
        assertEquals("İşıqforda 1:35 gözlədin", text(ev("stop", light), Lang.AZ))
        assertNull(text(ev("stop", light + ("kind" to "stop")), Lang.TR))
        assertNull(text(ev("stop", light + ("kind" to "light?")), Lang.TR))
        assertEquals("Sipariş 3 alınmadan bitti — iptal sayıldı", text(ev("order_cancel", mapOf("n" to 3, "source" to "kuryeplus")), Lang.TR))
        assertEquals("Sifariş 3 götürülmədən bitdi — ləğv sayıldı", text(ev("order_cancel", mapOf("n" to 3, "source" to "kuryeplus")), Lang.AZ))
        assertNull(text(ev("order_cancel", mapOf("n" to 3)), Lang.TR))
        for (t in listOf("stopping", "order_target", "customer_journey", "home_return_start", "home_return_arrived")) assertNull(text(ev(t), Lang.TR))
    }

    @Test
    fun `denetleyici dil seçimine göre toast üretir ve elle işaret metinleri`() {
        val c = DayController(MemoryMapPrefs()) { Lang.AZ }
        assertEquals("Gün başladı — km, vaxt və işıqforlar qeyd olunur", c.startDay(NOW0).toasts.single().text)
        c.onStages(feed(trip(TRIP_A, Stage.TO_PICKUP)), NOW0 + 1000, null)
        val arrive = c.onStages(feed(trip(TRIP_A, Stage.AT_PICKUP)), NOW0 + 2000, null)
        assertTrue(arrive.haptic)
        assertEquals("Restorana çatdın — gözləmə başladı", arrive.toasts.single().text)
        assertEquals("İşıqfor yadda saxlanıldı", c.markLight(40.4, 49.85, true, NOW0 + 3000).toasts.single().text)
        assertEquals("Bu işıqfor xəritədən gizlədildi", c.markLight(40.5, 49.85, false, NOW0 + 3000).toasts.single().text)
        val side = c.markSidewalk(GeoPoint(40.4, 49.85), remove = false, nowMs = NOW0 + 4000)
        assertEquals("Bu nöqtədə gözləmə səki kimi qeyd olunacaq", side.toasts.single().text)
        assertEquals("stop_surface_override", side.events.single().type)
        assertEquals("sidewalk", side.events.single().data["surface"])
        assertEquals("Səki işarəsi silindi", c.markSidewalk(GeoPoint(40.4, 49.85), remove = true, nowMs = NOW0 + 5000).toasts.single().text)
        assertFalse(DayText.entries.any { it.tr.contains("KuryePlus") || it.az.contains("KuryePlus") })
    }
}
