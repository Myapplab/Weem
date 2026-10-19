package com.kuryelog.logic.map.roads

import java.time.ZoneId

/**
 * Eski toplamların güvenilir gün bilgisi yoktur: o taban korunur, yalnız yeni otomatik farklar gerçek gün kimliğiyle (`yolgo-<ms>`)
 * kaydedilir (MAP.md 9.10 `roads.days.v2`). Gün silme yalnız o günün farkını geri alır. [zone]: takvim günü dilimi (Bakü +4 testte).
 */
class RoadDayContributions(knowledge: RoadKnowledge, stored: String? = null, val zone: ZoneId = ZoneId.systemDefault()) {
    fun capture(k: RoadKnowledge, day: String): Unit = TODO("A6.11")

    /** Elle yapılan değişiklik tabana işlenir. */
    fun manual(k: RoadKnowledge): Unit = TODO("A6.11")

    /** [date] (`yyyy-MM-dd`) günlerinin katkısını siler; silinen gün kimliklerini döner. */
    fun deleteDate(k: RoadKnowledge, date: String): Set<String> = TODO("A6.11")

    fun resetWay(id: Long?, wrongWay: Boolean = false): Unit = TODO("A6.11")
    fun encode(): String = TODO("A6.11")
}
