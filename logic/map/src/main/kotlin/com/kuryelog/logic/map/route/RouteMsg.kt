package com.kuryelog.logic.map.route

import com.kuryelog.logic.core.Bilingual

/** Rota motorunun ileti kodları (MAP.md 3.12). Hata ve uyarılar kodla taşınır; metin [RouteMsg]'de. */
enum class RouteCode {
    OUTSIDE_MAP,
    NO_ACCESS_NEAR_TARGET,
    NO_RIDEABLE_NEAR_START,
    TARGET_NOT_CONNECTED,
    NO_VEHICLE_ROAD_IN_WALK,
    START_NOT_RIDEABLE,
    NO_ROUTE_BY_RULES,
    NO_ROUTE,
    TIMEOUT,
    ENGINE_FAILED,

    /** Uyarı: rotada [RouteMsg.meters] m yaya yolu (scooter itilir). */
    PUSH,

    /** Uyarı: rota erişilebilir yolda biter, kapı [RouteMsg.meters] m uzakta. */
    DOOR,
    START_WALK,
    BAD_ROADS_NEEDED,
}

/**
 * Kullanıcıya görünen rota iletisi: kod + sayı; TR/AZ metin YolGo `trMsg` sözlüğüyle (`i18n.dart:30–89`) aynı sonuç (MAP.md 3.12).
 * Motor TR üretirdi ve ekran alt dize değişimiyle çevirirdi; WEEM metni koddan kurar.
 */
data class RouteMsg(val code: RouteCode, val meters: Int = 0) : Bilingual {
    override val tr: String
        get() = when (code) {
            RouteCode.OUTSIDE_MAP -> "Konum veya hedef indirilen yol haritasının dışında"
            RouteCode.NO_ACCESS_NEAR_TARGET -> "Hedef yakınında erişilebilir yol yok; pini yol girişine taşı"
            RouteCode.NO_RIDEABLE_NEAR_START -> "Konumunun yakınında sürülebilir yol yok"
            RouteCode.TARGET_NOT_CONNECTED -> "Hedefe bağlı yol bulunamadı; yol olmayan yerden geçiş çizilmedi"
            RouteCode.NO_VEHICLE_ROAD_IN_WALK -> "Hedefe yürüyüş mesafesinde bağlı araç yolu bulunamadı"
            RouteCode.START_NOT_RIDEABLE -> "Başlangıç yolu şu kurallarla sürülemiyor"
            RouteCode.NO_ROUTE_BY_RULES -> "Kurallara uyan rota bulunamadı"
            RouteCode.NO_ROUTE -> "Rota bulunamadı"
            RouteCode.TIMEOUT -> "Rota hesaplaması çok uzun sürdü; tekrar deneniyor"
            RouteCode.ENGINE_FAILED -> "Rota hesaplanamadı; tekrar dene"
            RouteCode.PUSH -> "Rotada yaklaşık $meters m yaya yolu: scooter'ı it"
            RouteCode.DOOR -> "Rota erişilebilir yolda biter; kapı girişi yaklaşık $meters m uzakta"
            RouteCode.START_WALK -> "Önce yaya yolundan en yakın sürülebilir yola yürü"
            RouteCode.BAD_ROADS_NEEDED -> "Kötü yolları tamamen dışlayan bağlantı yok; kalite puanı düşük yol gerekebilir"
        }

    override val az: String
        get() = when (code) {
            RouteCode.OUTSIDE_MAP -> "Məkan və ya ünvan yüklənmiş yol xəritəsinin xaricindədir"
            RouteCode.NO_ACCESS_NEAR_TARGET -> "Ünvanın yaxınlığında giriş yolu yoxdur; işarəni yolun girişinə çək"
            RouteCode.NO_RIDEABLE_NEAR_START -> "Yaxınlığında sürülə bilən yol yoxdur"
            RouteCode.TARGET_NOT_CONNECTED -> "Ünvana bağlı yol tapılmadı; yol olmayan yerdən keçid çəkilmədi"
            RouteCode.NO_VEHICLE_ROAD_IN_WALK -> "Ünvana piyada məsafəsində bağlı nəqliyyat yolu tapılmadı"
            RouteCode.START_NOT_RIDEABLE -> "Olduğun yol bu qaydalarla sürülə bilmir"
            RouteCode.NO_ROUTE_BY_RULES -> "Qaydalara uyğun marşrut tapılmadı"
            RouteCode.NO_ROUTE -> "Marşrut tapılmadı"
            RouteCode.TIMEOUT -> "Marşrut hesabı çox uzun çəkdi; yenidən yoxlanılır"
            RouteCode.ENGINE_FAILED -> "Marşrut hesablanmadı; yenidən yoxla"
            RouteCode.PUSH -> "Marşrutda təxminən $meters m piyada yolu: skuteri it"
            RouteCode.DOOR -> "Marşrut giriş yolunda bitir; qapı təxminən $meters m uzaqdadır"
            RouteCode.START_WALK -> "Əvvəlcə piyada yolu ilə ən yaxın sürülə bilən yola piyada get"
            RouteCode.BAD_ROADS_NEEDED -> "Pis yolları tam kənarda saxlayan marşrut yoxdur; zəif keyfiyyətli yol lazım ola bilər"
        }

    override fun toString() = tr

    companion object {
        val OUTSIDE_MAP = RouteMsg(RouteCode.OUTSIDE_MAP)
        val NO_ACCESS_NEAR_TARGET = RouteMsg(RouteCode.NO_ACCESS_NEAR_TARGET)
        val NO_RIDEABLE_NEAR_START = RouteMsg(RouteCode.NO_RIDEABLE_NEAR_START)
        val TARGET_NOT_CONNECTED = RouteMsg(RouteCode.TARGET_NOT_CONNECTED)
        val NO_VEHICLE_ROAD_IN_WALK = RouteMsg(RouteCode.NO_VEHICLE_ROAD_IN_WALK)
        val START_NOT_RIDEABLE = RouteMsg(RouteCode.START_NOT_RIDEABLE)
        val NO_ROUTE_BY_RULES = RouteMsg(RouteCode.NO_ROUTE_BY_RULES)
        val NO_ROUTE = RouteMsg(RouteCode.NO_ROUTE)
        val TIMEOUT = RouteMsg(RouteCode.TIMEOUT)
        val ENGINE_FAILED = RouteMsg(RouteCode.ENGINE_FAILED)
        val START_WALK = RouteMsg(RouteCode.START_WALK)
        val BAD_ROADS_NEEDED = RouteMsg(RouteCode.BAD_ROADS_NEEDED)

        fun push(meters: Int) = RouteMsg(RouteCode.PUSH, meters)
        fun door(meters: Int) = RouteMsg(RouteCode.DOOR, meters)
    }
}

/** Rota hatası; [message] TR (testler ve günlük), ekran [msg]'yi seçili dilde gösterir. */
class RoutingException(val msg: RouteMsg) : Exception(msg.tr) {
    override val message: String get() = msg.tr
}

/** Rota motoru [RouteEngine.timeoutMs] içinde yanıt vermedi (ağ hatası değildir); YolGo `RouteWorkerTimeout`. */
class RouteTimeout : Exception(RouteMsg.TIMEOUT.tr) {
    val msg: RouteMsg get() = RouteMsg.TIMEOUT
}
