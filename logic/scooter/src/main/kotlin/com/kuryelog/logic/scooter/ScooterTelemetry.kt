package com.kuryelog.logic.scooter

import org.json.JSONObject
import java.util.Locale

/**
 * Bağlantının son register değerlerinden kayıt satırı, 1 Hz enerji örneği ve ayar ekranı metni (SCOOTER.md 7.1, 7.2, 8.1; NB
 * `BleCapture.telemetry/sample/measurements`, satır satır). Saf: Android `BleLink` `latestValues`'ı verir. Değer yoksa hiçbiri
 * uydurulmaz (null / bekleme metni).
 */
object ScooterTelemetry {
    /** `sample` olayının `scooter_telemetry` alanı; değer yoksa null. */
    fun telemetry(values: Collection<NinebotMetrics.Value>, now: Long): JSONObject? {
        if (values.isEmpty()) return null
        val fields = JSONObject()
        for (v in values) {
            val age = now - v.monoMs
            val fresh = age >= 0 && age <= NinebotMetrics.freshMs(v.source)
            fields.put(
                v.source.key,
                LogExport.json(
                    "value", if (fresh) v.value else null, "last_value", v.value, "age_ms", age, "fresh", fresh, "unit", v.source.unit,
                    "board", v.source.board, "register", v.source.index, "decoded_mono_ms", v.monoMs,
                ),
            )
        }
        val power = NinebotMetrics.powerW(values, now, NinebotMetrics.FAST_FRESH_MS)
        val internal = NinebotMetrics.packPowerW(values, now, NinebotMetrics.FAST_FRESH_MS, NinebotMetrics.PRIMARY_BMS)
        val external = NinebotMetrics.externalPowerW(values, now, NinebotMetrics.FAST_FRESH_MS)
        val speed = NinebotMetrics.freshValue(values, "scooter_speed_kmh", now, NinebotMetrics.FAST_FRESH_MS)
        val tripKm = NinebotMetrics.freshValue(values, "dashboard_trip_km", now, NinebotMetrics.SLOW_FRESH_MS)
        val rideSec = NinebotMetrics.freshValue(values, "dashboard_ride_seconds", now, NinebotMetrics.SLOW_FRESH_MS)
        return LogExport.json(
            "mapping", "community_g3_candidate", "device_verified", false, "fields", fields, "scooter_speed_kmh", speed, "dashboard_power_w", power,
            "dashboard_power_source", if (power == null) null else if (external == null) "voltage_times_current" else "voltage_times_current_all_packs",
            "internal_power_w", internal, "external_power_w", external, "dashboard_trip_km", tripKm,
            "dashboard_ride_minutes", rideSec?.let { it / 60 }, "dashboard_ride_seconds", rideSec,
        )
    }

    /** Bir 1 Hz enerji örneği; ilk bilinen harici paket kartı harici pakettir ([externalBoards] tercih `external_bms_boards`). Değer yoksa null. */
    fun sample(values: Collection<NinebotMetrics.Value>, wallMs: Long, monoMs: Long, externalBoards: String?): ScooterEnergy.Sample? {
        if (values.isEmpty()) return null
        val ext = NinebotMetrics.parseBoards(externalBoards)
        return ScooterEnergy.sample(values, wallMs, monoMs, if (ext.isNotEmpty()) ext[0] else 0)
    }

    /** Ayar ekranındaki ölçüm metni (doğrulanmamış değerler açıkça belirtilir). */
    fun measurements(values: Collection<NinebotMetrics.Value>, now: Long): String {
        if (values.isEmpty()) return "Scooter ölçümü bekleniyor • GPS ayrı kaydedilir"
        val text = StringBuilder("G3 ölçümleri • sahada karşılaştırılmalı")
        val power = NinebotMetrics.powerW(values, now, NinebotMetrics.FAST_FRESH_MS)
        text.append(if (power == null) "\nGüç: ölçülüyor…" else String.format(Locale.ROOT, "\nGüç: %.0f W (V×A • doğrulanmamış)", power))
        for (v in values) {
            if (v.source.sweep) continue
            if (now - v.monoMs <= NinebotMetrics.freshMs(v.source)) {
                var label = when (v.source.key) {
                    "battery_percent" -> "Pil"
                    "remaining_range_km" -> "Menzil"
                    "odometer_km" -> "Toplam km"
                    "vehicle_temperature_c" -> "Araç sıcaklığı"
                    "scooter_speed_kmh" -> "Hız (aday)"
                    "dashboard_trip_km" -> "Sürüş km (aday)"
                    "dashboard_ride_seconds" -> "Sürüş sn (aday)"
                    else -> v.source.key
                }
                if (label.contains("lifetime")) continue
                if (v.source.board != 0x16) {
                    label = String.format(
                        Locale.ROOT, "Pil [%02x] • %s", v.source.board,
                        when (v.source.index) {
                            0x8c -> "Voltaj"
                            0x8d -> "Akım (+ şarj)"
                            0x8a -> "Reg 0x8a (kapasite?)"
                            else -> "Yüzde"
                        },
                    )
                }
                text.append(String.format(Locale.ROOT, "\n%s: %.2f %s", label, v.value, v.source.unit))
            }
        }
        return text.toString()
    }
}
