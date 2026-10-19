package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.GeoPoint
import org.json.JSONObject

/** Gün motorunun konum okuması (Dart `Fix`; MAP.md 11.17): [speedMps] negatifse bilinmiyor. */
data class DayFix(val atMs: Long, val lat: Double, val lon: Double, val speedMps: Double = 0.0, val accuracy: Double = 5.0)

/** Durma noktasının yolla ilişkisi (harita verisinden). */
enum class StopSurface { UNKNOWN, ROAD, SIDEWALK }

/**
 * Durma noktasının yol bilgisi (Dart `RoadInfo`, MAP.md 11.8): [known] true = trafik ışığının yanında, false = ışık değil, null = bilinmiyor;
 * [lightM] ışığa uzaklık (m); [maxLightM] ışık sayılması için en fazla uzaklık (ayar, varsayılan 5 m); [footM] yaya yoluna uzaklık.
 * İskelet (A6.2b): türetilenler A6.8'de.
 */
data class RoadInfo(
    val onRoad: Boolean = false,
    val junctionM: Double? = null,
    val known: Boolean? = null,
    val lightLat: Double? = null,
    val lightLon: Double? = null,
    val surface: StopSurface = StopSurface.UNKNOWN,
    val lightM: Double? = null,
    val maxLightM: Double = 5.0,
    val mall: Boolean = false,
    val roadside: Boolean = false,
    val manualSidewalk: Boolean = false,
    val footM: Double? = null,
) {
    /** Işığa ≤[maxLightM] ve ((yoldayım ve yaya yolunda değilim) ya da yaya yoluna ≤1 m); AVM ve elle kaldırım ışık değildir; ışık bilinmiyorsa değil. */
    val atLight: Boolean get() = TODO("A6.8")

    /** AVM'de trafik ışığı olamaz. */
    val allowsTrafficLight: Boolean get() = TODO("A6.8")
}

enum class OrderPhase { NONE, TO_RESTAURANT, AT_RESTAURANT, READY_FOR_CUSTOMER, TO_CUSTOMER, AT_CUSTOMER }

/**
 * Bir duruş kaydı (Dart `StopRec`): [kind] `light` | `stop` | `order` | `light?` | `mall` | `sidewalk`; [longWait] 2 dk üstü ışık duruşu
 * (`light?` + `long` alanı); [measured] canlı ölçerden. JSON biçimi `VERI_SOZLESMESI` 11 (`long` yalnız doğruysa yazılır).
 */
class StopRec(
    val startMs: Long,
    val endMs: Long,
    val lat: Double,
    val lon: Double,
    val kind: String,
    val onRoad: Boolean,
    val junctionM: Double? = null,
    val inOrder: Boolean = false,
    var answer: String? = null,
    val lightLat: Double? = null,
    val lightLon: Double? = null,
    val measured: Boolean = false,
    val surface: StopSurface = StopSurface.UNKNOWN,
    val longWait: Boolean = false,
) {
    val durS: Int get() = ((endMs - startMs) / 1000).toInt()

    /** Işık sayılır mı (cevap "light", ya da `kind == "light"` ve cevap "not" değil). */
    val isLight: Boolean get() = TODO("A6.8")

    fun toJson(): JSONObject = TODO("A6.8")

    companion object {
        fun fromJson(j: JSONObject): StopRec = TODO("A6.8")
    }
}

/** Bir sipariş (Dart `OrderRec`; MAP.md 11.5): zamanlar epoch ms, null = olmadı. */
class OrderRec(val n: Int, val startedAtMs: Long) {
    var restArrivedAtMs: Long? = null
    var restLeftAtMs: Long? = null
    var custArrivedAtMs: Long? = null
    var deliveredAtMs: Long? = null
    var endedAtMs: Long? = null
    var customerDepartedAtMs: Long? = null
    var restLat: Double? = null
    var restLon: Double? = null
    var custLat: Double? = null
    var custLon: Double? = null
    var restAuto = false
    var custAuto = false
    var cancelled = false
    var distM = 0.0
    var restTarget: GeoPoint? = null
    var customerTarget: GeoPoint? = null
    var restName = ""

    val phase: OrderPhase get() = TODO("A6.8")

    /** Bekleme süreleri (sn): varış ya da çıkış görülmediyse null (uydurulmaz). */
    val restWaitS: Int? get() = TODO("A6.8")
    val custWaitS: Int? get() = TODO("A6.8")

    fun toJson(): JSONObject = TODO("A6.8")

    companion object {
        fun fromJson(j: JSONObject): OrderRec = TODO("A6.8")
    }
}

/** Motorun bir olayı (Dart `ShiftEvent`): [type] `day_start`, `order_start`, `arrive_restaurant`, `stop`, `delivered`, `order_cancel`, `ask` … */
data class DayEvent(val type: String, val atMs: Long, val data: Map<String, Any?> = emptyMap())

/**
 * Gün durumu (`shift.v1`, `v: 2`; MAP.md 11.5): km, hareket süresi, duruşlar, siparişler, eve dönüş alanları (`homeReturnAt`, `homeStartM`, `homeDistM`, `homeTimeS`).
 * Eski kayıt eksik alanlarla okunur; eski `question`, `pickupGroup`, `secondOrderWindowUsed` okunur, yazılmaz.
 */
class DayState {
    var active = false
    var startedAtMs: Long? = null
    var endedAtMs: Long? = null
    var distM = 0.0
    var movingS = 0.0
    val stops = ArrayList<StopRec>()
    val orders = ArrayList<OrderRec>()
    var current: OrderRec? = null
    var phase = OrderPhase.NONE
    var delivered = 0
    var homeReturnAtMs: Long? = null
    var homeStartM = 0.0
    var homeDistM = 0.0
    var homeTimeS = 0.0
    var startReason: String? = null
    var endReason: String? = null

    /** Gün sonunda sorulacak adaylar (`light?` ve cevapsız) ve ışık sayılanlar. */
    val lightCandidates: List<StopRec> get() = TODO("A6.8")
    val lights: List<StopRec> get() = TODO("A6.8")

    fun toJson(): JSONObject = TODO("A6.8")

    companion object {
        fun fromJson(j: JSONObject): DayState = TODO("A6.8")
    }
}
