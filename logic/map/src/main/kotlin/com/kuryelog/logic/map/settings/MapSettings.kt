package com.kuryelog.logic.map.settings

import com.kuryelog.logic.map.route.RideParams

/**
 * Harita ayarları (`settings.v1`, `MAP.md` 14.2): değişmez veri sınıfı; alan adları ve varsayılanlar YolGo `AppSettings` ile aynı.
 * `home`, `theme`, `lang`, `autoStartDay`… `:core`/`:app`'te kalır, buraya girmez. İskelet (A6.2a): `paramsFor` ve kodek A6.4'te.
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
    val turnPenaltyS: Double = 12.0,
    val qualityTolerancePct: Double = 10.0,
    val bikewayCostPct: Double = 85.0,
    val showBikewayLayer: Boolean = true,
    val speedChoices: List<Double> = listOf(20.0, 25.0, 29.0),
    val lightMaxM: Double = 5.0,
    val volumeKeys: Boolean = true,
    val showQualityLayer: Boolean = false,
    val wwMaxProblemPct: Double = 50.0,
    val wwAskAlways: Boolean = false,
    val logging: Boolean = true,
    val region: String = "central",
    val mapStyle: String = "minimal",
    val googleKey: String = "",
    val orsKey: String = "",
    val cartoKey: String = "",
) {
    /** `gradeLimitFor(battery)` / 100: batarya `lowBatteryPct` altındaysa `lowBatteryGradePct`. */
    fun gradeLimitFor(batteryPct: Double?): Double = TODO("A6.4")

    /** `qualityWeight = qualityTolerancePct/100`, `bikewayFactor = clamp(bikewayCostPct, 50, 100)/100`. */
    fun paramsFor(batteryPct: Double?): RideParams = TODO("A6.4")
}

/** `settings.v1` JSON kodeği (`MAP.md` 14.2 okuma kuralı); `org.json`. */
object MapSettingsCodec {
    /** Dizge yok/bozuk/nesne değil → varsayılanlar; alan yok ya da null → varsayılan; tipi yanlış alan → bütün varsayılanlar. */
    fun decode(json: String?): MapSettings = TODO("A6.4")

    fun encode(s: MapSettings): String = TODO("A6.4")
}
