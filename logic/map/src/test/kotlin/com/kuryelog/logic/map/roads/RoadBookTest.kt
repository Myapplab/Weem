package com.kuryelog.logic.map.roads

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.MemoryRoadFiles
import com.kuryelog.logic.map.route.StreetPrefsStore
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.ui.Toasts
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YolGo `road_save_test.dart` (1 test; A6.2a, MAP.md 9.11). Regresyon (ölçümde donma): her 30 sn'lik otomatik kayıt katman
 * sayacını artırıp kalite katmanına bütün yol kenarlarını yeniden taratıyordu; katman yalnız elle girilen puanlara bağlıdır.
 * Uyarlama: yaşam döngüsü olayları yerine `onPause()`, `SharedPreferences` yerine bellek içi `roads.rate.v1.json`. Uygulama: A6.11.
 */
class RoadBookTest {
    @Test
    fun `otomatik kayıt katmanı yenilemez, elle puan yeniler ve kalıcıdır`() {
        val files = MemoryRoadFiles()
        val book = RoadBook(files, StreetPrefsStore(MemoryMapPrefs()), Toasts(), { MapSettings() }, FakeClock())
        val before = book.state.statsRev
        // Uygulama arka plana geçince zorunlu kayıt.
        book.onPause()
        assertEquals(before, book.state.statsRev)
        book.rate(42, 25, name = "Test küçəsi")
        assertEquals(before + 1, book.state.statsRev)
        assertEquals(25, RoadKnowledge.decodeMap(files.read("roads.rate.v1.json")) { (it as Number).toInt() }[42L])
    }
}
