package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.ScooterLive

/**
 * Canlı kare + yolculuk sayacı → [ScooterLive] (SCOOTER.md 8.4, 13.2; RS `ScooterLink.liveValues` + `ScooterFeedContract.snapshot`).
 * `:map`, `:orders`, `:economics` ve (Receiver'dan) `BatterySummary` bunu okur. Ölçülmeyen değer null (sıfır uydurulmaz); sonsuz/NaN null;
 * yük yalnız scooter değerleri ve sayaç: sipariş, müşteri, adres, konum yok.
 */
object ScooterLiveMapper {
    /**
     * [active] bağlantı çalışıyor; [frame] son panel karesi (bağlantı düşünce null); [trip] sayaç özeti. [nowMono] kare zamanıyla aynı saat
     * (`elapsedRealtime`), [nowWall] yükün üretim anı.
     */
    fun map(active: Boolean, frame: NinebotDashboard.Snapshot?, trip: TripMeter.Stats?, nowMono: Long, nowWall: Long): ScooterLive {
        val age = frame?.let { nowMono - it.monoMs }?.takeIf { it >= 0 }
        return ScooterLive(
            generatedAtMs = nowWall,
            linkActive = active,
            ageMs = if (active) age else null,
            scooter = ScooterLive.Values(
                internalPct = n(frame?.batteryPct), externalPct = n(frame?.extBatteryPct), speedKmh = n(frame?.speedKmh), powerW = n(frame?.powerW),
                voltageV = n(frame?.voltageV), currentA = n(frame?.currentA), rangeKm = n(frame?.rangeKm), tempC = n(frame?.tempC),
                odometerKm = n(frame?.odometerKm),
            ),
            trip = if (active && trip != null) {
                ScooterLive.Trip(
                    wh = n(trip.wh), pctUsed = n(trip.pctUsed), avgMovingKmh = n(trip.avgMovingKmh), km = n(trip.km), movingMs = trip.movingMs,
                    startedAtMs = trip.startedWallMs,
                )
            } else {
                null
            },
        )
    }

    private fun n(v: Double?): Double? = v?.takeIf { it.isFinite() }
}
