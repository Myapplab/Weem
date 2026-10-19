package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.ToastTone

/**
 * Gün motorunun toast metinleri (MAP.md 11.14, M-11-1): "KuryePlus" adı yok, `<ek>` hep boş. Olay verisindeki `source: "kuryeplus"` yalnız kayıt dizgisidir.
 * `:app` kaynağında düz metin olmaz; şerit `DayToast` taşır.
 */
enum class DayText(override val tr: String, override val az: String) : Bilingual {
    DAY_START("Gün başladı — km, süre ve durmalar kaydediliyor", "Gün başladı — km, vaxt və işıqforlar qeyd olunur"),
    DAY_START_HOME("Evden çıkıldı — gün otomatik başladı", "Evdən çıxdın — gün avtomatik başladı"),
    DAY_END_HOME("Eve döndün — gün otomatik bitti", "Evə qayıtdın — gün avtomatik bitdi"),
    ORDER("Sipariş", "Sifariş"),
    ORDER_START_TAIL("başladı → restorana", "başladı → restorana"),
    ARRIVE_RESTAURANT("Restorana vardın — bekleme başladı", "Restorana çatdın — gözləmə başladı"),
    LEAVE_RESTAURANT_UNKNOWN("Restorandan ayrıldın · bekleme bilinmiyor", "Restorandan çıxdın · gözləmə məlum deyil"),
    LEAVE_RESTAURANT_WAIT("Restorandan ayrıldın · bekleme", "Restorandan çıxdın · gözləmə"),
    ARRIVE_CUSTOMER("Müşteriye vardın", "Müştəriyə çatdın"),
    DELIVERED_TAIL("teslim edildi", "çatdırıldı"),
    LIGHT_WAIT_HEAD("Trafik ışığında", "İşıqforda"),
    LIGHT_WAIT_TAIL("bekledin", "gözlədin"),
    ORDER_CANCEL_TAIL("alınmadan bitti — iptal sayıldı", "götürülmədən bitdi — ləğv sayıldı"),
    LIGHT_SAVED("Trafik ışığı kaydedildi", "İşıqfor yadda saxlanıldı"),
    LIGHT_HIDDEN("Bu ışık haritadan gizlendi", "Bu işıqfor xəritədən gizlədildi"),
    SIDEWALK_ON("Bu nokta kaldırım beklemesi olarak kaydedilecek", "Bu nöqtədə gözləmə səki kimi qeyd olunacaq"),
    SIDEWALK_OFF("Kaldırım işareti kaldırıldı", "Səki işarəsi silindi"),
    ;

    companion object {
        /** `m:ss`; bilinmiyorsa "?". */
        fun mmss(s: Int?): String = if (s == null) "?" else "${s / 60}:${(s % 60).toString().padStart(2, '0')}"

        /** Olay → toast (yoksa null): MAP.md 11.14 tablosu. Süre ve ton, titreşim şiddeti [DayToast]'ta. */
        fun toastFor(e: DayEvent, lang: Lang): DayToast? = when (e.type) {
            "day_start" -> when (e.data["reason"]) {
                "home_departure" -> DayToast(DAY_START_HOME.get(lang), ToastTone.GREEN)
                else -> DayToast(DAY_START.get(lang), ToastTone.GREEN)
            }
            "day_end" -> if (e.data["reason"] == "home_arrival") DayToast(DAY_END_HOME.get(lang), ToastTone.GREEN) else null
            "order_start" -> DayToast("${ORDER.get(lang)} ${e.data["n"]} ${ORDER_START_TAIL.get(lang)}", ToastTone.PINK)
            "arrive_restaurant" -> DayToast(ARRIVE_RESTAURANT.get(lang), ToastTone.FOOD, haptic = Haptic.MEDIUM)
            "leave_restaurant" -> {
                // Varış görülmediyse bekleme bilinmiyor (uydurulmaz).
                val waitS = (e.data["waitS"] as? Number)?.toInt()
                val text = if (waitS == null) LEAVE_RESTAURANT_UNKNOWN.get(lang) else "${LEAVE_RESTAURANT_WAIT.get(lang)} ${mmss(waitS)}"
                DayToast(text, ToastTone.BLUE, haptic = Haptic.MEDIUM)
            }
            "arrive_customer" -> DayToast(ARRIVE_CUSTOMER.get(lang), ToastTone.PINK, haptic = Haptic.MEDIUM)
            "delivered" -> DayToast(
                "${ORDER.get(lang)} ${e.data["n"]} ${DELIVERED_TAIL.get(lang)} · ${mmss((e.data["totalS"] as? Number)?.toInt())}",
                ToastTone.GREEN, seconds = 6, haptic = Haptic.STRONG,
            )
            "stop" -> if (e.data["kind"] == "light") {
                val st = (e.data["start"] as? Number)?.toLong()
                val en = (e.data["end"] as? Number)?.toLong()
                val w = if (st != null && en != null) ((en - st) / 1000).toInt() else null
                DayToast("${LIGHT_WAIT_HEAD.get(lang)} ${mmss(w)} ${LIGHT_WAIT_TAIL.get(lang)}", ToastTone.ORANGE)
            } else {
                null
            }
            "order_cancel" -> if (e.data["source"] == ShiftEngine.FEED_SOURCE) {
                DayToast("${ORDER.get(lang)} ${e.data["n"]} ${ORDER_CANCEL_TAIL.get(lang)}", ToastTone.ORANGE)
            } else {
                null
            }
            else -> null
        }
    }
}
