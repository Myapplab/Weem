package com.kuryelog.logic.map.settings

import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.route.RideParams
import org.json.JSONObject

/**
 * `map` dosyasındaki `settings.v1` (MAP.md 14.2): değişmez veri sınıfı. `theme`, `lang`, ev, `autoStartDay` … girmez (`:app` ve `:core`).
 * İskelet (A6.2a): türetilenler ve kodek A6.4'te.
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
    val region: String = "central",
    val googleKey: String = "",
    val orsKey: String = "",
    val cartoKey: String = "",
    val logging: Boolean = true,
    val volumeKeys: Boolean = true,
    val qualityTolerancePct: Double = 10.0,
    val showQualityLayer: Boolean = false,
    val wwMaxProblemPct: Double = 50.0,
    val wwAskAlways: Boolean = false,
    val mapStyle: String = "minimal",
    val lightMaxM: Double = 5.0,
    val turnPenaltyS: Double = 12.0,
    val speedChoices: List<Double> = DEFAULT_SPEED_CHOICES,
    /** E13: bisiklet yolu kenarının rota maliyeti (%; 85 = ×0,85, 100 = tercih yok), 50–100. */
    val bikewayCostPct: Double = 85.0,
    val showBikewayLayer: Boolean = true,
) {
    val regionBox: BBox get() = TODO("A6.4")

    /** Batarya yüzdesine göre etkin eğim limiti (%50 altı → %10). */
    fun gradeLimitFor(batteryPct: Double?): Double = TODO("A6.4")

    fun paramsFor(batteryPct: Double?): RideParams = TODO("A6.4")

    companion object {
        val DEFAULT_SPEED_CHOICES = listOf(20.0, 25.0, 29.0)
    }
}

/** `settings.v1` JSON kodeği (okuma kuralı MAP.md 14.2: tip yanlışı → bütün varsayılan). */
object MapSettingsCodec {
    fun decode(json: String?): MapSettings = TODO("A6.4")
    fun toJson(s: MapSettings): JSONObject = TODO("A6.4")
}
