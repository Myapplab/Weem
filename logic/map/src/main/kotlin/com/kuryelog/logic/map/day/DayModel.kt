package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.GeoPoint
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private fun r6(v: Double): Double = String.format(Locale.ROOT, "%.6f", v).toDouble()

private fun JSONObject.optMs(k: String): Long? = (opt(k) as? Number)?.toLong()
private fun JSONObject.optNum(k: String): Double? = (opt(k) as? Number)?.toDouble()
private fun JSONObject.objects(k: String): List<JSONObject> = (opt(k) as? JSONArray)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()

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
    val atLight: Boolean
        get() = !mall && !manualSidewalk && known == true &&
            (if (lightM != null) lightM <= maxLightM && (onRoad || (footM ?: Double.POSITIVE_INFINITY) <= 1.0) else surface != StopSurface.SIDEWALK && !roadside)

    /** Işık ihtimali olan yer: yolda, kaldırımda/kenarda/AVM'de değil. */
    val allowsTrafficLight: Boolean get() = onRoad && surface != StopSurface.SIDEWALK && !mall && !roadside
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
    val isLight: Boolean get() = answer == "light" || (answer == null && kind == "light")

    fun toJson(): JSONObject = JSONObject().apply {
        put("start", startMs); put("end", endMs)
        put("lat", r6(lat)); put("lon", r6(lon))
        put("kind", kind); put("onRoad", onRoad)
        junctionM?.let { put("jm", Math.round(it)) }
        put("inOrder", inOrder)
        answer?.let { put("answer", it) }
        lightLat?.let { put("llat", r6(it)) }
        lightLon?.let { put("llon", r6(it)) }
        if (measured) put("measured", true)
        put("surface", surface.name.lowercase(Locale.ROOT))
        if (longWait) put("long", true)
    }

    /** `stop` olayının verisi (JSON alanları). */
    fun toJsonMap(): Map<String, Any?> = toJson().let { j -> j.keys().asSequence().associateWith { j.opt(it) } }

    companion object {
        fun fromJson(j: JSONObject): StopRec = StopRec(
            startMs = j.getLong("start"), endMs = j.getLong("end"),
            lat = j.getDouble("lat"), lon = j.getDouble("lon"),
            kind = j.getString("kind"),
            onRoad = j.optBoolean("onRoad", false),
            junctionM = j.optNum("jm"),
            inOrder = j.optBoolean("inOrder", false),
            answer = j.opt("answer") as? String,
            lightLat = j.optNum("llat"), lightLon = j.optNum("llon"),
            measured = j.opt("measured") == true,
            surface = StopSurface.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == j.opt("surface") } ?: StopSurface.UNKNOWN,
            longWait = j.opt("long") == true,
        )
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

    val phase: OrderPhase
        get() = when {
            custArrivedAtMs != null -> OrderPhase.AT_CUSTOMER
            restLeftAtMs != null -> if (customerDepartedAtMs == null) OrderPhase.READY_FOR_CUSTOMER else OrderPhase.TO_CUSTOMER
            restArrivedAtMs != null -> OrderPhase.AT_RESTAURANT
            else -> OrderPhase.TO_RESTAURANT
        }

    /** Bekleme süreleri (sn): varış ya da çıkış görülmediyse null (uydurulmaz). */
    val restWaitS: Int? get() = secs(restArrivedAtMs, restLeftAtMs)
    val custWaitS: Int? get() = secs(custArrivedAtMs, deliveredAtMs)
    val totalS: Int? get() = secs(startedAtMs, deliveredAtMs ?: endedAtMs)

    private fun secs(from: Long?, to: Long?): Int? = if (from == null || to == null) null else ((to - from) / 1000).toInt()

    fun toJson(): JSONObject = JSONObject().apply {
        put("n", n); put("start", startedAtMs)
        restArrivedAtMs?.let { put("restArrived", it) }
        restLeftAtMs?.let { put("restLeft", it) }
        custArrivedAtMs?.let { put("custArrived", it) }
        deliveredAtMs?.let { put("delivered", it) }
        endedAtMs?.let { put("ended", it) }
        restLat?.let { put("restLat", it) }
        restLon?.let { put("restLon", it) }
        custLat?.let { put("custLat", it) }
        custLon?.let { put("custLon", it) }
        put("restAuto", restAuto); put("custAuto", custAuto); put("cancelled", cancelled)
        put("m", Math.round(distM))
        restTarget?.let { put("restTarget", it.toJson()) }
        customerTarget?.let { put("customerTarget", it.toJson()) }
        if (restName.isNotEmpty()) put("restName", restName)
        put("pickupFlow", 2)
        customerDepartedAtMs?.let { put("customerDeparted", it) }
    }

    companion object {
        fun fromJson(j: JSONObject): OrderRec = OrderRec(j.getInt("n"), j.getLong("start")).also { o ->
            o.restArrivedAtMs = j.optMs("restArrived")
            o.restLeftAtMs = j.optMs("restLeft")
            o.custArrivedAtMs = j.optMs("custArrived")
            o.deliveredAtMs = j.optMs("delivered")
            o.endedAtMs = j.optMs("ended")
            o.restLat = j.optNum("restLat"); o.restLon = j.optNum("restLon")
            o.custLat = j.optNum("custLat"); o.custLon = j.optNum("custLon")
            o.restAuto = j.optBoolean("restAuto", false)
            o.custAuto = j.optBoolean("custAuto", false)
            o.cancelled = j.optBoolean("cancelled", false)
            o.distM = j.optNum("m") ?: 0.0
            o.restTarget = GeoPoint.fromJson(j.opt("restTarget"))
            o.customerTarget = GeoPoint.fromJson(j.opt("customerTarget"))
            o.restName = j.opt("restName") as? String ?: ""
            // Eski kayıt (pickupFlow yok): alıştan sonra müşteri bacağı sayılır, geri sarılmaz.
            o.customerDepartedAtMs = j.optMs("customerDeparted") ?: if (!j.has("pickupFlow")) j.optMs("restLeft") else null
        }
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
    /** 1.10.0 öncesi ikinci sipariş (MAP.md 11.5): motor üretmez, yalnız okur. */
    val queuedOrders = ArrayList<OrderRec>()
    var current: OrderRec? = null
    var phase = OrderPhase.NONE
    var homeReturnAtMs: Long? = null
    var homeStartM = 0.0
    var homeDistM = 0.0
    var homeTimeS = 0.0
    var startReason: String? = null
    var endReason: String? = null

    /** Yeni gün için boş durum (Dart `startDay` yeni `ShiftState` kurar; burada nesne korunur). */
    fun reset() {
        active = false; startedAtMs = null; endedAtMs = null; distM = 0.0; movingS = 0.0
        stops.clear(); orders.clear(); queuedOrders.clear(); current = null; phase = OrderPhase.NONE
        homeReturnAtMs = null; homeStartM = 0.0; homeDistM = 0.0; homeTimeS = 0.0; startReason = null; endReason = null
    }

    /** `yolgo-<başlangıç ms>`: gün kimliği (okunmaz, türetilir; MAP.md 11.2). */
    val dayId: String? get() = startedAtMs?.let { "yolgo-$it" }

    /** Teslim sayısı (türetilir). */
    val delivered: Int get() = orders.count { it.deliveredAtMs != null }

    /** Gün sonunda sorulacak adaylar (`light?` ve cevapsız) ve ışık sayılanlar. */
    val lightCandidates: List<StopRec> get() = stops.filter { it.kind == "light?" && it.answer == null }
    val lights: List<StopRec> get() = stops.filter { it.isLight }

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 2)
        put("dayId", dayId ?: JSONObject.NULL)
        put("active", active)
        startedAtMs?.let { put("start", it) }
        endedAtMs?.let { put("end", it) }
        put("m", Math.round(distM))
        put("movingS", Math.round(movingS))
        put("stops", JSONArray().also { a -> stops.forEach { a.put(it.toJson()) } })
        put("orders", JSONArray().also { a -> orders.forEach { a.put(it.toJson()) } })
        put("queuedOrders", JSONArray().also { a -> queuedOrders.forEach { a.put(it.toJson()) } })
        current?.let { put("current", it.toJson()) }
        put("phase", phase.name.lowercase(Locale.ROOT).let { snakeToCamel(it) })
        homeReturnAtMs?.let { put("homeReturnAt", it) }
        put("homeStartM", homeStartM)
        put("homeDistM", homeDistM)
        put("homeTimeS", homeTimeS.toLong())
        startReason?.let { put("startReason", it) }
        endReason?.let { put("endReason", it) }
    }

    companion object {
        fun fromJson(j: JSONObject): DayState = DayState().also { s ->
            s.active = j.optBoolean("active", false)
            s.startedAtMs = j.optMs("start")
            s.endedAtMs = j.optMs("end")
            s.distM = j.optNum("m") ?: 0.0
            s.movingS = j.optNum("movingS") ?: 0.0
            s.homeReturnAtMs = j.optMs("homeReturnAt")
            s.homeStartM = j.optNum("homeStartM") ?: 0.0
            s.homeDistM = j.optNum("homeDistM") ?: 0.0
            s.homeTimeS = (j.optNum("homeTimeS") ?: 0.0).toLong().toDouble()
            s.startReason = j.opt("startReason") as? String
            s.endReason = j.opt("endReason") as? String
            j.objects("stops").forEach { s.stops += StopRec.fromJson(it) }
            j.objects("orders").forEach { s.orders += OrderRec.fromJson(it) }
            s.current = j.optJSONObject("current")?.let { OrderRec.fromJson(it) }
            j.objects("queuedOrders").forEach { s.queuedOrders += OrderRec.fromJson(it) }
            s.phase = OrderPhase.entries.firstOrNull { snakeToCamel(it.name.lowercase(Locale.ROOT)) == j.opt("phase") } ?: OrderPhase.NONE
        }

        /** `TO_RESTAURANT` → `toRestaurant` (Dart `OrderPhase.name`). */
        private fun snakeToCamel(s: String): String = s.split('_').mapIndexed { i, p -> if (i == 0) p else p.replaceFirstChar { it.uppercase() } }.joinToString("")
    }
}
