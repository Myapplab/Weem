package com.kuryelog.logic.map.reminders

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hatırlatıcı denetleyicisi (MAP.md 13.16, 13.18 "Yeni"): gün bağı, sıra, önizleme, 30 sn, iptaller, ayar sayacı. */
class ReminderControllerTest {
    private class Rec : ReminderEvents {
        val shown = ArrayList<Triple<ReminderKind, String?, Boolean>>()
        val cancelled = ArrayList<ReminderKind>()
        val logged = ArrayList<Pair<String, Map<String, Any?>>>()
        override fun show(item: ReminderItem, dayId: String?, preview: Boolean) { shown += Triple(item.kind, dayId, preview) }
        override fun cancel(kind: ReminderKind) { cancelled += kind }
        override fun logEvent(name: String, data: Map<String, Any?>) { logged += name to data }
    }

    private fun setup(): Triple<ReminderController, FakeClock, Rec> {
        val clock = FakeClock()
        val ctl = ReminderController(MemoryMapPrefs(), clock)
        val rec = Rec().also { ctl.events = it }
        return Triple(ctl, clock, rec)
    }

    @Test
    fun `gün yokken gösterilmez, açılınca su aralığı dolunca gelir, bir denetimde bir hatırlatıcı`() {
        val (ctl, clock, rec) = setup()
        ctl.check(clock.nowMs + 60 * 60_000)
        assertNull(ctl.active)
        ctl.activeDay("yolgo-1", clock.nowMs)
        ctl.updateItem(ReminderKind.CHECK) { it.copy(enabled = true, intervalMin = 10) }
        clock.advance(46 * 60_000)
        ctl.check()
        assertEquals(ReminderKind.WATER, ctl.active!!.item.kind)
        assertEquals(listOf(Triple(ReminderKind.WATER, "yolgo-1", false)), rec.shown)
        assertEquals("reminder", rec.logged.single().first)
        ctl.check() // kart varken yenisi yok
        assertEquals(1, rec.shown.size)
        ctl.done()
        assertNull(ctl.active)
        assertEquals(listOf(ReminderKind.WATER), rec.cancelled)
        ctl.check()
        assertEquals(ReminderKind.CHECK, ctl.active!!.item.kind)
    }

    @Test
    fun `kart 30 sn sonra kalkar, gün kapanınca bildirimler iptal olur`() {
        val (ctl, clock, rec) = setup()
        ctl.activeDay("yolgo-1", clock.nowMs)
        clock.advance(46 * 60_000)
        ctl.check()
        clock.advance(29_000); ctl.hideIfExpired()
        assertNotNull(ctl.active)
        clock.advance(1_000); ctl.hideIfExpired()
        assertNull(ctl.active)
        ctl.check(); ctl.done()
        ctl.activeDay(null, null)
        assertEquals(ReminderKind.values().toList(), rec.cancelled.takeLast(3))
    }

    @Test
    fun `önizleme zamanlamayı bozmaz ve gün kimliği taşımaz`() {
        val (ctl, clock, rec) = setup()
        ctl.preview(ReminderKind.FOOD)
        assertEquals(Triple(ReminderKind.FOOD, null as String?, true), rec.shown.single())
        assertEquals(mapOf("k" to "food", "preview" to true), rec.logged.single().second)
        val saved = ctl.settings
        ctl.done()
        assertEquals(saved, ctl.settings)
        assertEquals(clock.nowMs, ctl.settings.of(ReminderKind.WATER).sinceMs)
    }

    @Test
    fun `ayar değişimi sayacı yalnız açılışta ve aralık değişince sıfırlar, kalıcıdır`() {
        val prefs = MemoryMapPrefs()
        val clock = FakeClock()
        val ctl = ReminderController(prefs, clock)
        clock.advance(1000)
        ctl.updateItem(ReminderKind.WATER) { it.copy(text = "Su!") }
        assertEquals(1_790_000_000_000L, ctl.settings.of(ReminderKind.WATER).sinceMs)
        ctl.updateItem(ReminderKind.WATER) { it.copy(intervalMin = 30) }
        assertEquals(clock.nowMs, ctl.settings.of(ReminderKind.WATER).sinceMs)
        ctl.updateItem(ReminderKind.CHECK) { it.copy(enabled = true) }
        assertEquals(clock.nowMs, ctl.settings.of(ReminderKind.CHECK).sinceMs)
        assertTrue(ctl.wantsPermission)
        val back = ReminderController(prefs, clock)
        assertEquals("Su!", back.settings.of(ReminderKind.WATER).text)
        assertEquals(30, back.settings.of(ReminderKind.WATER).intervalMin)
        assertFalse(back.settings.of(ReminderKind.FOOD).enabled)
    }

    @Test
    fun `son gösterim kalıcı ve bozuk öğe atlanır`() {
        val prefs = MemoryMapPrefs()
        val clock = FakeClock()
        val a = ReminderController(prefs, clock)
        a.activeDay("yolgo-1", clock.nowMs)
        clock.advance(46 * 60_000); a.check()
        assertTrue(prefs.getStringList(ReminderController.LAST_KEY)!!.single().startsWith("water:"))
        prefs.setStringList(ReminderController.LAST_KEY, prefs.getStringList(ReminderController.LAST_KEY)!! + listOf("bozuk", "x:y", "nope:5"))
        val b = ReminderController(prefs, clock)
        b.activeDay("yolgo-1", clock.nowMs - 46 * 60_000)
        b.check()
        assertNull(b.active) // son gösterim yüklendi: aralık yeniden dolmadı
    }
}
