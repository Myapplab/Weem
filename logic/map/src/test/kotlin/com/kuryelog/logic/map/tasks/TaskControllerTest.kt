package com.kuryelog.logic.map.tasks

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YolGo `task_controller_test.dart` (1; döngü iki değerle aynen, A6.2b, MAP.md 13.13, 13.18) — UYARLANIR (S9): "Google Maps açıldı mı" yerine "WEEM navigasyonu hedefi kabul etti
 * mi" (sahte navigasyon kapısı): görev yalnız hedef kabul edilirse tamamlanır; yeniden açılışta korunur.
 */
class TaskControllerTest {
    @Test
    fun `navigasyon hedefi kabul etmezse görev bekler, ederse tamamlanır ve yeniden açılışta korunur`() {
        for (opens in listOf(false, true)) {
            val prefs = MemoryMapPrefs()
            val clock = FakeClock()
            val ctl = TaskController(prefs, clock) { opens }
            val task = DailyTask("a", "Banka", "Filial", GeoPoint(40.4, 49.8), clock.nowMs)
            ctl.add(task)
            assertEquals(opens, ctl.openRoute(task))
            assertEquals("açılış $opens", opens, ctl.tasks.single().completedAtMs != null)
            val restored = TaskController(prefs, clock) { opens }
            assertEquals(opens, restored.tasks.single().completedAtMs != null)
        }
    }
}
