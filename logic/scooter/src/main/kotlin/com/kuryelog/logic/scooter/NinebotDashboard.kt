package com.kuryelog.logic.scooter

import java.util.Locale
import java.util.Objects

/** Plain dashboard state for the Segway-style screen; Android-free so the local tests cover it (SCOOTER.md 6.1; NB `NinebotDashboard.java`). */
class NinebotDashboard {
    /** One immutable screen frame. Null means "no fresh value", drawn as a dash, never as 0. */
    class Snapshot internal constructor(
        val speedKmh: Double?,
        val powerW: Double?,
        val voltageV: Double?,
        val currentA: Double?,
        val batteryPct: Double?,
        val rangeKm: Double?,
        val tripKm: Double?,
        val rideSec: Double?,
        val tempC: Double?,
        val maxSpeedKmh: Double?,
        /** External pack percent (first answering non-internal pack); null until read. */
        val extBatteryPct: Double?,
        /** Odometer; null until read. */
        val odometerKm: Double?,
        val monoMs: Long,
    ) {
        internal constructor(
            speed: Double?, power: Double?, volt: Double?, amp: Double?, battery: Double?, range: Double?, trip: Double?, ride: Double?,
            temp: Double?, max: Double?, now: Long,
        ) : this(speed, power, volt, amp, battery, range, trip, ride, temp, max, null, null, now)

        /** True when every displayed value matches, ignoring the timestamp; lets the view redraw only on change. `Objects.equals` as
         *  in the original (boxed `Double.equals`: NaN equals NaN, 0.0 and -0.0 differ), not Kotlin's IEEE `==` on `Double?`. */
        fun sameValues(o: Snapshot?): Boolean = o != null && Objects.equals(speedKmh, o.speedKmh) && Objects.equals(powerW, o.powerW) &&
            Objects.equals(voltageV, o.voltageV) && Objects.equals(currentA, o.currentA) && Objects.equals(batteryPct, o.batteryPct) &&
            Objects.equals(rangeKm, o.rangeKm) && Objects.equals(tripKm, o.tripKm) && Objects.equals(rideSec, o.rideSec) &&
            Objects.equals(tempC, o.tempC) && Objects.equals(maxSpeedKmh, o.maxSpeedKmh) && Objects.equals(extBatteryPct, o.extBatteryPct) &&
            Objects.equals(odometerKm, o.odometerKm)
    }

    private var maxSpeed = -1.0

    /** Session max from the speed register; the official app's own max may differ (field 2026-10-07: 17.4 logged vs 8 shown). */
    fun reset() {
        maxSpeed = -1.0
    }

    fun snapshot(values: Collection<NinebotMetrics.Value>, now: Long): Snapshot {
        val fast = NinebotMetrics.FAST_FRESH_MS
        val slow = NinebotMetrics.SLOW_FRESH_MS
        val speed = NinebotMetrics.freshValue(values, "scooter_speed_kmh", now, fast)
        if (speed != null && speed > maxSpeed) maxSpeed = speed
        // Tiles show the internal pack; an external pack (once found) only adds to the power total.
        val volt = NinebotMetrics.freshValue(values, NinebotMetrics.packKey(NinebotMetrics.PRIMARY_BMS, "voltage_v"), now, fast)
        val amp = NinebotMetrics.freshValue(values, NinebotMetrics.packKey(NinebotMetrics.PRIMARY_BMS, "current_a_charge_positive"), now, fast)
        var battery = NinebotMetrics.freshValue(values, "battery_percent", now, slow)
        if (battery == null) battery = NinebotMetrics.freshValue(values, NinebotMetrics.packKey(NinebotMetrics.PRIMARY_BMS, "percent"), now, slow)
        return Snapshot(
            speed, NinebotMetrics.powerW(values, now, fast), volt, amp, battery, NinebotMetrics.freshValue(values, "remaining_range_km", now, slow),
            NinebotMetrics.freshValue(values, "dashboard_trip_km", now, slow), NinebotMetrics.freshValue(values, "dashboard_ride_seconds", now, slow),
            NinebotMetrics.freshValue(values, "vehicle_temperature_c", now, slow), if (maxSpeed < 0) null else maxSpeed,
            externalPct(values, now, slow), NinebotMetrics.freshValue(values, "odometer_km", now, slow), now,
        )
    }

    companion object {
        const val DASH = "–"

        private fun externalPct(values: Collection<NinebotMetrics.Value>, now: Long, fresh: Long): Double? {
            for (b in NinebotMetrics.packBoards(values)) {
                if (b == NinebotMetrics.PRIMARY_BMS) continue
                val p = NinebotMetrics.freshValue(values, NinebotMetrics.packKey(b, "percent"), now, fresh)
                if (p != null) return p
            }
            return null
        }

        /** "12:05" minutes:seconds, "1:02:05" past an hour. `Math.round` as in the original (NaN → 0, no exception). */
        fun clock(seconds: Double?): String {
            if (seconds == null) return DASH
            val s = maxOf(0L, Math.round(seconds))
            return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
            else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
        }

        fun speed(v: Double?): String = if (v == null) DASH else String.format(Locale.ROOT, "%.1f", v)

        fun watts(v: Double?): String {
            if (v == null) return DASH
            val w = Math.round(v)
            return if (w == 0L) "0" else w.toString()
        }

        fun oneDecimal(v: Double?): String = if (v == null) DASH else String.format(Locale.ROOT, "%.1f", v)

        fun whole(v: Double?): String = if (v == null) DASH else Math.round(v).toString()

        /** "1 dk 28 sn" style; under a minute shows seconds only. */
        fun ride(seconds: Double?): Array<String> {
            if (seconds == null) return arrayOf(DASH)
            val s = maxOf(0L, Math.round(seconds))
            val m = s / 60
            val r = s % 60
            if (m == 0L) return arrayOf(r.toString(), "sn")
            if (m >= 60) return arrayOf((m / 60).toString(), "sa", (m % 60).toString(), "dk")
            return arrayOf(m.toString(), "dk", r.toString(), "sn")
        }

        /** Gauge sweep fraction 0..1 against a 30 km/h scale (G3 top speed 25 km/h leaves head room). */
        fun gauge(speed: Double?): Float {
            if (speed == null) return 0f
            return Math.max(0.0, Math.min(1.0, speed / 30.0)).toFloat()
        }
    }
}
