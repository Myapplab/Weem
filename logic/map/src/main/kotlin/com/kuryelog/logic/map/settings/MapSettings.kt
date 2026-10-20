package com.kuryelog.logic.map.settings

import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.route.RideParams
import com.kuryelog.logic.map.route.VehicleProfile
import com.kuryelog.logic.map.ui.MapStyles
import org.json.JSONArray
import org.json.JSONObject

/**
 * `map` dosyasındaki `settings.v1` (MAP.md 14.2): değişmez veri sınıfı. `theme`, `lang`, ev, `autoStartDay` … girmez (`:app` ve
 * `:core`); YolGo göç işaretleri (`lightMaxMv2/v3`, `mapDesignV1`) yazılmaz (S1).
 */
data class MapSettings(
    val vehicleId: String = "g3",
    val flatWhPerM: Double = 0.0135,
    val climbWhPerM: Double = 0.38,
    val climbSafety: Double = 1.0,
    val flatKmh: Double = 28.5,
    val climbKmh: Double = 18.5,
    val walkKmh: Double = 4.5,
    val maxGradePct: Double = 18.0,
    val lowBatteryGradePct: Double = 10.0,
    val lowBatteryPct: Double = 50.0,
    val homeReturnPct: Double = 30.0,
    val mainRoadPenalty: Double = 1.15,
    val walkRadius: Double = 150.0,
    /** `central` | `wide` (öteki her değer merkez kutu). */
    val region: String = "central",
    val googleKey: String = "",
    val orsKey: String = "",
    val cartoKey: String = "",
    val logging: Boolean = true,
    val volumeKeys: Boolean = true,
    /** Yol kalitesi toleransı (%): süre/mesafe farkı bundan azsa kaliteli yol seçilir. */
    val qualityTolerancePct: Double = 10.0,
    val showQualityLayer: Boolean = false,
    /** Ters yön sorun oranı eşiği (%): aşan onaylı sokakta (≥ 3 geçiş) ters yöne girilmez. */
    val wwMaxProblemPct: Double = 50.0,
    val wwAskAlways: Boolean = false,
    /**
     * Yol kalitesi öğrenme (Y12) ana anahtarı — WEEM, S26 (varsayılan kapalı): kapalıyken öğrenme, ters yön soruları, kalite kartı ve
     * Ses± yakalama çalışmaz; önceden öğrenilmiş/elle girilmiş yol bilgisi rotada kullanılmaya devam eder.
     */
    val roadLearning: Boolean = false,
    val mapStyle: String = MapStyles.DEFAULT,
    /** Trafik ışığı sayımı: durma noktası ışığa en fazla bu kadar yakın (m). */
    val lightMaxM: Double = 5.0,
    /** Rota: her belirgin dönüş bu kadar saniye pahalı sayılır. */
    val turnPenaltyS: Double = 12.0,
    /** Sol kenardaki hız penceresinin seyir hızları (km/s, 3 adet). */
    val speedChoices: List<Double> = DEFAULT_SPEED_CHOICES,
    /** E13: bisiklet yolu kenarının rota maliyeti (%; 85 = ×0,85, 100 = tercih yok), 50–100. */
    val bikewayCostPct: Double = 85.0,
    val showBikewayLayer: Boolean = true,
) {
    val vehicle: VehicleProfile get() = VehicleProfile.byId(vehicleId)

    val regionBox: BBox get() = if (region == "wide") Baku.wideBox else Baku.centralBox

    /** Batarya yüzdesine göre etkin eğim limiti (şartname: %50 altı → %10). */
    fun gradeLimitFor(batteryPct: Double?): Double =
        (if (batteryPct != null && batteryPct < lowBatteryPct) lowBatteryGradePct else maxGradePct) / 100

    fun paramsFor(batteryPct: Double?): RideParams = RideParams(
        flatWhPerM = flatWhPerM,
        climbWhPerM = climbWhPerM,
        climbSafety = climbSafety,
        flatKmh = flatKmh,
        climbKmh = climbKmh,
        walkKmh = walkKmh,
        maxGrade = gradeLimitFor(batteryPct),
        mainRoadPenalty = mainRoadPenalty,
        walkRadius = walkRadius,
        qualityWeight = qualityTolerancePct / 100,
        turnPenaltyS = turnPenaltyS,
        bikewayFactor = bikewayCostPct.coerceIn(50.0, 100.0) / 100,
    )

    companion object {
        /** Tercih anahtarı (`map` dosyası, String JSON). */
        const val KEY = "settings.v1"
        val DEFAULT_SPEED_CHOICES = listOf(20.0, 25.0, 29.0)
    }
}

/**
 * `settings.v1` JSON kodeği (YolGo `AppSettings.fromJson`/`toJson`, `SettingsController.build`; MAP.md 14.2). Okuma: dizge yok,
 * JSON değil ya da nesne değil → bütün varsayılanlar; alan yok ya da `null` → o alanın varsayılanı; alan var ama tipi yanlış →
 * **bütün ayarlar** varsayılan (Dart `as` dökümü gibi); bilinmeyen anahtarlar (eski `voice` …) yok sayılır.
 */
object MapSettingsCodec {
    fun decode(json: String?): MapSettings {
        if (json == null) return MapSettings()
        return try {
            fromJson(JSONObject(json))
        } catch (_: Exception) {
            MapSettings()
        }
    }

    fun toJson(s: MapSettings): JSONObject = JSONObject()
        .put("vehicleId", s.vehicleId)
        .put("flatWhPerM", s.flatWhPerM)
        .put("climbWhPerM", s.climbWhPerM)
        .put("climbSafety", s.climbSafety)
        .put("flatKmh", s.flatKmh)
        .put("climbKmh", s.climbKmh)
        .put("walkKmh", s.walkKmh)
        .put("maxGradePct", s.maxGradePct)
        .put("lowBatteryGradePct", s.lowBatteryGradePct)
        .put("lowBatteryPct", s.lowBatteryPct)
        .put("homeReturnPct", s.homeReturnPct)
        .put("mainRoadPenalty", s.mainRoadPenalty)
        .put("walkRadius", s.walkRadius)
        .put("region", s.region)
        .put("googleKey", s.googleKey)
        .put("orsKey", s.orsKey)
        .put("cartoKey", s.cartoKey)
        .put("logging", s.logging)
        .put("volumeKeys", s.volumeKeys)
        .put("qualityTolerancePct", s.qualityTolerancePct)
        .put("showQualityLayer", s.showQualityLayer)
        .put("wwMaxProblemPct", s.wwMaxProblemPct)
        .put("wwAskAlways", s.wwAskAlways)
        .put("roadLearning", s.roadLearning)
        .put("mapStyle", s.mapStyle)
        .put("lightMaxM", s.lightMaxM)
        .put("turnPenaltyS", s.turnPenaltyS)
        .put("speedChoices", JSONArray().also { a -> s.speedChoices.forEach { a.put(it) } })
        .put("bikewayCostPct", s.bikewayCostPct)
        .put("showBikewayLayer", s.showBikewayLayer)

    private fun fromJson(j: JSONObject): MapSettings {
        val d = MapSettings()
        fun raw(k: String): Any? = j.opt(k).takeUnless { it == JSONObject.NULL }
        fun n(k: String, def: Double): Double = when (val v = raw(k)) {
            null -> def
            is Number -> v.toDouble()
            else -> throw ClassCastException(k)
        }
        fun str(k: String, def: String): String = when (val v = raw(k)) {
            null -> def
            is String -> v
            else -> throw ClassCastException(k)
        }
        fun bool(k: String, def: Boolean): Boolean = when (val v = raw(k)) {
            null -> def
            is Boolean -> v
            else -> throw ClassCastException(k)
        }
        return MapSettings(
            vehicleId = str("vehicleId", d.vehicleId),
            flatWhPerM = n("flatWhPerM", d.flatWhPerM),
            climbWhPerM = n("climbWhPerM", d.climbWhPerM),
            climbSafety = n("climbSafety", d.climbSafety),
            flatKmh = n("flatKmh", d.flatKmh),
            climbKmh = n("climbKmh", d.climbKmh),
            walkKmh = n("walkKmh", d.walkKmh),
            maxGradePct = n("maxGradePct", d.maxGradePct),
            lowBatteryGradePct = n("lowBatteryGradePct", d.lowBatteryGradePct),
            lowBatteryPct = n("lowBatteryPct", d.lowBatteryPct),
            homeReturnPct = n("homeReturnPct", d.homeReturnPct),
            mainRoadPenalty = n("mainRoadPenalty", d.mainRoadPenalty),
            walkRadius = n("walkRadius", d.walkRadius),
            region = str("region", d.region),
            googleKey = str("googleKey", ""),
            orsKey = str("orsKey", ""),
            cartoKey = str("cartoKey", ""),
            logging = bool("logging", true),
            volumeKeys = bool("volumeKeys", true),
            qualityTolerancePct = n("qualityTolerancePct", d.qualityTolerancePct),
            showQualityLayer = bool("showQualityLayer", false),
            wwMaxProblemPct = n("wwMaxProblemPct", d.wwMaxProblemPct),
            wwAskAlways = bool("wwAskAlways", false),
            roadLearning = bool("roadLearning", false),
            mapStyle = str("mapStyle", d.mapStyle),
            lightMaxM = n("lightMaxM", d.lightMaxM),
            turnPenaltyS = n("turnPenaltyS", d.turnPenaltyS),
            speedChoices = speeds(raw("speedChoices")),
            bikewayCostPct = n("bikewayCostPct", d.bikewayCostPct).coerceIn(50.0, 100.0),
            showBikewayLayer = bool("showBikewayLayer", d.showBikewayLayer),
        )
    }

    /** Kayıtlı hız listesi bozuksa varsayılan (3 değer, 5–45 km/s; YolGo `_speeds`). */
    private fun speeds(raw: Any?): List<Double> {
        if (raw !is JSONArray || raw.length() != 3) return MapSettings.DEFAULT_SPEED_CHOICES
        val v = (0 until raw.length()).mapNotNull { i ->
            (raw.opt(i) as? Number)?.toDouble()?.takeIf { it >= 5 && it <= 45 }
        }
        return if (v.size == 3) v else MapSettings.DEFAULT_SPEED_CHOICES
    }
}
