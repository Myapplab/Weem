package com.kuryelog.logic.map.tasks

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Görev denetleyicisi yaşam döngüsü (MAP.md 13.13, 13.18 "Yeni"): uyarı, kapatma kalıcı, süre dolumu, gün kapanışı, silme, olaylar. */
class TaskLifecycleTest {
    private val clock = FakeClock()
    private val here = GeoPoint(40.4, 49.8)

    private class Rec : TaskEvents {
        var alerts = 0
        var cancels = 0
        val logged = ArrayList<String>()
        override fun alert(tasks: List<DailyTask>, dayId: String?) { alerts += tasks.size }
        override fun cancelAlert() { cancels++ }
        override fun logEvent(name: String) { logged += name }
    }

    private fun ctl(prefs: MemoryMapPrefs = MemoryMapPrefs(), opens: Boolean = true): Pair<TaskController, Rec> {
        val c = TaskController(prefs, clock) { opens }
        val r = Rec().also { c.events = it }
        c.activeDay("yolgo-1")
        return c to r
    }

    private fun task(id: String = "a", due: Long = clock.nowMs) = DailyTask(id, "Banka", "Filial", here, due)

    @Test
    fun `teslimatta uyarı, kapatınca uyarıdan çıkar tamamlanmaz ve kapatma kalıcıdır`() {
        val prefs = MemoryMapPrefs()
        val (c, r) = ctl(prefs)
        c.add(task())
        c.deliveryFinished(here, 5.0, clock.nowMs)
        assertEquals(listOf("a"), c.alerts.map { it.id }); assertEquals(1, r.alerts)
        c.dismiss("a")
        assertTrue(c.alerts.isEmpty()); assertEquals(1, r.cancels)
        assertEquals(null, c.tasks.single().completedAtMs)
        assertTrue(prefs.getString(TaskController.CLOSED_KEY)!!.contains("\"a\""))
        // yeni süreç: bölge içinde açıldı → kapatılmış görev yeniden girişle gelmez, yalnız teslimatla
        val (c2, _) = ctl(prefs)
        c2.onFix(here, 5.0, clock.nowMs)
        assertTrue(c2.alerts.isEmpty())
        c2.deliveryFinished(here, 5.0, clock.nowMs)
        assertEquals(1, c2.alerts.size)
    }

    @Test
    fun `süresi geçen uyarı düşer, gün kapanınca uyarılar temizlenir`() {
        val (c, r) = ctl()
        c.add(task())
        c.deliveryFinished(here, 5.0, clock.nowMs)
        clock.advance(24 * 3600_000L)
        c.expire()
        assertTrue(c.alerts.isEmpty()); assertEquals(1, r.cancels)
        c.add(task("b", clock.nowMs))
        c.deliveryFinished(here, 5.0, clock.nowMs)
        assertEquals(1, c.alerts.size)
        c.activeDay(null)
        assertTrue(c.alerts.isEmpty()); assertEquals(2, r.cancels)
    }

    @Test
    fun `silme görevi ve uyarıyı kaldırır, rota açılışı olay yazar ve ikinci basış yok sayılır`() {
        val (c, r) = ctl()
        c.add(task("a")); c.add(task("b", clock.nowMs + 1000))
        c.deliveryFinished(here, 5.0, clock.nowMs)
        c.delete("a")
        assertEquals(listOf("b"), c.tasks.map { it.id })
        assertTrue(c.alerts.isEmpty())
        assertTrue(c.openRoute(c.tasks.single()))
        assertEquals(listOf("task_route_opened"), r.logged)
        assertFalse(c.opening)
    }

    @Test
    fun `bozuk görev kaydı atlanır, bozuk dizi boş liste verir`() {
        val prefs = MemoryMapPrefs()
        prefs.setString(TaskController.KEY, """[{"id":"x","title":"t","place":"p","point":{"lat":999,"lon":1},"due":1},{"id":"y","title":"t","place":"p","point":{"lat":40.4,"lon":49.8},"due":5}]""")
        assertEquals(listOf("y"), TaskController(prefs, clock) { true }.tasks.map { it.id })
        prefs.setString(TaskController.KEY, "{bozuk")
        assertTrue(TaskController(prefs, clock) { true }.tasks.isEmpty())
    }
}
