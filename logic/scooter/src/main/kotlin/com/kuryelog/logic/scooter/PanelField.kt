package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.Lang
import java.util.Collections
import java.util.Locale

/** Values the floating panel (and the picture-in-picture fallback) can show; the user picks up to MAX. Android-free. (SCOOTER.md 6.3; NB `PanelField.java`.) */
enum class PanelField(val key: String, val label: String, val unit: String) {
    SPEED("speed", "Hız", "km/h"),
    POWER("power", "Güç", "W"),
    BATTERY("battery", "Batarya", "%"),
    RANGE("range", "Menzil", "km"),
    VOLTAGE("voltage", "Voltaj", "V"),
    CURRENT("current", "Akım", "A"),
    TEMP("temp", "Sıcaklık", "°C"),
    EXT_BATTERY("ext_battery", "Harici batarya", "%"),
    MAX_SPEED("max_speed", "Maks. hız", "km/h"),
    ODOMETER("odometer", "Toplam km", "km"),
    SCOOTER_TRIP("scooter_trip", "Scooter sürüş mesafesi", "km"),
    RIDE_TIME("ride_time", "Scooter sürüş süresi", "dk:sn"),
    TRIP_WH("trip_wh", "Bu sürüş enerji", "Wh"),
    TRIP_PCT("trip_pct", "Bu sürüş batarya", "%"),
    TRIP_KM("trip_km", "Bu sürüş mesafe", "km"),
    AVG_SPEED("avg_speed", "Ortalama hız", "km/h"),
    ;

    /** Display text of this value; a dash when nothing fresh is known, never a made-up zero. */
    fun value(s: NinebotDashboard.Snapshot?, t: TripMeter.Stats?): String = when (this) {
        SPEED -> NinebotDashboard.speed(s?.speedKmh)
        POWER -> NinebotDashboard.watts(s?.powerW)
        BATTERY -> NinebotDashboard.whole(s?.batteryPct)
        RANGE -> NinebotDashboard.oneDecimal(s?.rangeKm)
        VOLTAGE -> NinebotDashboard.oneDecimal(s?.voltageV)
        CURRENT -> NinebotDashboard.oneDecimal(s?.currentA)
        TEMP -> NinebotDashboard.whole(s?.tempC)
        EXT_BATTERY -> NinebotDashboard.whole(s?.extBatteryPct)
        MAX_SPEED -> NinebotDashboard.oneDecimal(s?.maxSpeedKmh)
        ODOMETER -> NinebotDashboard.oneDecimal(s?.odometerKm)
        SCOOTER_TRIP -> NinebotDashboard.oneDecimal(s?.tripKm)
        RIDE_TIME -> NinebotDashboard.clock(s?.rideSec)
        TRIP_KM -> if (t == null || t.movingMs == 0L) NinebotDashboard.DASH else String.format(Locale.ROOT, "%.1f", t.km)
        TRIP_WH -> NinebotDashboard.oneDecimal(t?.wh)
        TRIP_PCT -> NinebotDashboard.whole(t?.pctUsed)
        else -> NinebotDashboard.oneDecimal(t?.avgMovingKmh)
    }

    /** Short unit line under a value; trip values say "bu sürüş" so they are not mistaken for the live battery. */
    fun caption(): String = when (this) {
        TRIP_WH -> "Wh • sürüş"
        TRIP_PCT -> "% • sürüş"
        TRIP_KM -> "km • sürüş"
        AVG_SPEED -> "ort. km/h"
        EXT_BATTERY -> "% • harici"
        MAX_SPEED -> "maks. km/h"
        ODOMETER -> "toplam km"
        RIDE_TIME -> "süre"
        else -> unit
    }

    /** Ayar listesindeki ad (A5.4, G3): TR [label] aynen, AZ karşılığı. */
    fun title(lang: Lang): String = if (lang == Lang.TR) label else when (this) {
        SPEED -> "Sürət"
        POWER -> "Güc"
        BATTERY -> "Batareya"
        RANGE -> "Məsafə"
        VOLTAGE -> "Gərginlik"
        CURRENT -> "Cərəyan"
        TEMP -> "Temperatur"
        EXT_BATTERY -> "Xarici batareya"
        MAX_SPEED -> "Maks. sürət"
        ODOMETER -> "Ümumi km"
        SCOOTER_TRIP -> "Skuterin sürüş məsafəsi"
        RIDE_TIME -> "Skuterin sürüş müddəti"
        TRIP_WH -> "Bu gediş enerji"
        TRIP_PCT -> "Bu gediş batareya"
        TRIP_KM -> "Bu gediş məsafə"
        AVG_SPEED -> "Orta sürət"
    }

    /** Birim satırı, dile göre ([caption] TR'dir). Birimler (km/h, W, %) iki dilde aynı. */
    fun caption(lang: Lang): String = if (lang == Lang.TR) caption() else when (this) {
        TRIP_WH -> "Wh • gediş"
        TRIP_PCT -> "% • gediş"
        TRIP_KM -> "km • gediş"
        AVG_SPEED -> "orta km/h"
        EXT_BATTERY -> "% • xarici"
        MAX_SPEED -> "maks. km/h"
        ODOMETER -> "ümumi km"
        RIDE_TIME -> "müddət"
        else -> unit
    }

    /** Birim, dile göre ("dk:sn" AZ'de "dəq:san"). */
    fun unit(lang: Lang): String = if (lang == Lang.AZ && this == RIDE_TIME) "dəq:san" else unit

    companion object {
        const val MAX = 4

        /** Speed is on the scooter's own display, so it is off by default. */
        val DEFAULT: List<PanelField> = Collections.unmodifiableList(listOf(POWER, TRIP_WH, BATTERY, AVG_SPEED))

        /** Stored comma list → known, distinct fields in order, at most MAX; empty or unreadable → DEFAULT. `trim { it <= ' ' }` is
         *  Java's `String.trim()`. */
        fun parse(csv: String?): List<PanelField> {
            val out = ArrayList<PanelField>()
            if (csv != null) for (part in csv.split(",")) {
                for (f in entries) if (f.key == part.trim { it <= ' ' } && !out.contains(f) && out.size < MAX) out.add(f)
            }
            return if (out.isEmpty()) DEFAULT else out
        }

        fun join(fields: List<PanelField>): String {
            val b = StringBuilder()
            for (f in fields) {
                if (b.isNotEmpty()) b.append(',')
                b.append(f.key)
            }
            return b.toString()
        }
    }
}
