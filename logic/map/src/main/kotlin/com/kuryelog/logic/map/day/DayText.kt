package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Lang

/** Gün motorunun toast metinleri (MAP.md 11.14; M-11-1: " (KuryePlus)" eki kalktı, iptal metni "alınmadan bitti"). */
enum class DayText(override val tr: String, override val az: String) : Bilingual {
    DAY_START("Gün başladı — km, süre ve durmalar kaydediliyor", "Gün başladı — km, vaxt və işıqforlar qeyd olunur"),
    DAY_START_HOME("Evden çıkıldı — gün otomatik başladı", "Evdən çıxdın — gün avtomatik başladı"),
    DAY_END_HOME("Eve döndün — gün otomatik bitti", "Evə qayıtdın — gün avtomatik bitdi"),
    ORDER_START("Sipariş %d başladı → restorana", "Sifariş %d başladı → restorana"),
    ARRIVE_RESTAURANT("Restorana vardın — bekleme başladı", "Restorana çatdın — gözləmə başladı"),
    LEAVE_RESTAURANT_UNKNOWN("Restorandan ayrıldın · bekleme bilinmiyor", "Restorandan çıxdın · gözləmə məlum deyil"),
    LEAVE_RESTAURANT("Restorandan ayrıldın · bekleme %s", "Restorandan çıxdın · gözləmə %s"),
    ARRIVE_CUSTOMER("Müşteriye vardın", "Müştəriyə çatdın"),
    DELIVERED("Sipariş %d teslim edildi · %s", "Sifariş %d çatdırıldı · %s"),
    LIGHT_WAIT("Trafik ışığında %s bekledin", "İşıqforda %s gözlədin"),
    ORDER_CANCEL("Sipariş %d alınmadan bitti — iptal sayıldı", "Sifariş %d götürülmədən bitdi — ləğv sayıldı"),
    LIGHT_SAVED("Trafik ışığı kaydedildi", "İşıqfor yadda saxlanıldı"),
    LIGHT_HIDDEN("Bu ışık haritadan gizlendi", "Bu işıqfor xəritədən gizlədildi"),
    SIDEWALK_SAVED("Bu nokta kaldırım beklemesi olarak kaydedilecek", "Bu nöqtədə gözləmə səki kimi qeyd olunacaq"),
    SIDEWALK_REMOVED("Kaldırım işareti kaldırıldı", "Səki işarəsi silindi"),
    ;

    fun format(lang: Lang, vararg args: Any?): String = String.format(get(lang), *args)

    companion object {
        /** `m:ss`; bilinmiyorsa "?". */
        fun mmss(seconds: Int?): String = if (seconds == null) "?" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }
}

/** Olaydan toast (varsa) ve titreşim (arrive/leave/delivered). */
object DayToasts {
    fun of(e: DayEvent, lang: Lang): Pair<DayToast, Boolean>? {
        fun n() = (e.data["n"] as? Number)?.toInt() ?: 0
        return when (e.type) {
            "day_start" -> {
                val t = if (e.data["reason"] == "home_departure") DayText.DAY_START_HOME else DayText.DAY_START
                DayToast(t.get(lang)) to false
            }
            "day_end" -> if (e.data["reason"] == "home_arrival") DayToast(DayText.DAY_END_HOME.get(lang)) to false else null
            "order_start" -> DayToast(DayText.ORDER_START.format(lang, n()), DayTone.PINK) to false
            "arrive_restaurant" -> DayToast(DayText.ARRIVE_RESTAURANT.get(lang)) to true
            "leave_restaurant" -> {
                val wait = (e.data["waitS"] as? Number)?.toInt()
                val t = if (wait == null) DayText.LEAVE_RESTAURANT_UNKNOWN.get(lang) else DayText.LEAVE_RESTAURANT.format(lang, DayText.mmss(wait))
                DayToast(t) to true
            }
            "arrive_customer" -> DayToast(DayText.ARRIVE_CUSTOMER.get(lang)) to true
            "delivered" -> DayToast(DayText.DELIVERED.format(lang, n(), DayText.mmss((e.data["totalS"] as? Number)?.toInt()))) to true
            "stop" -> {
                val stop = e.data["stop"] as? org.json.JSONObject ?: return null
                if (stop.optString("kind") != "light") return null
                val s = ((stop.getLong("end") - stop.getLong("start")) / 1000).toInt()
                DayToast(DayText.LIGHT_WAIT.format(lang, DayText.mmss(s)), DayTone.ORANGE) to false
            }
            "order_cancel" -> if (e.data["source"] == ShiftEngine.FEED_SOURCE) DayToast(DayText.ORDER_CANCEL.format(lang, n()), DayTone.ORANGE) to false else null
            else -> null
        }
    }
}
