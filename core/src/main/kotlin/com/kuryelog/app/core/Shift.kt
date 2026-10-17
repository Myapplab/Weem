package com.kuryelog.app.core

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.DeviceId
import com.kuryelog.logic.core.GpsFix
import com.kuryelog.logic.core.GpsTrack
import com.kuryelog.logic.core.ShiftMachine
import java.time.ZoneId

/** Vardiyanın süreç içi parçaları ve başlatma/bitirme istekleri (CORE.md 1.2, 1.11). Karar `logic/core` [ShiftMachine]'de. */
object Shift {
    /** Android saati: monoton = `elapsedRealtime` (fix zamanları onunla gelir). */
    object AndroidClock : Clock {
        override fun wallMs() = System.currentTimeMillis()
        override fun monoMs() = SystemClock.elapsedRealtime()
        override fun zone(): ZoneId = ZoneId.systemDefault()
    }

    /** D8: Rawstats `Store.distanceM` gibi süreç boyunca; servis yeniden yaratılsa da kalır. */
    val track = GpsTrack(::distanceM)

    /** Rawstats `Location.distanceTo` ile aynı hesap (WGS84), Float duyarlığıyla. */
    fun distanceM(a: GpsFix, b: GpsFix): Double {
        val out = FloatArray(1)
        Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, out)
        return out[0].toDouble()
    }

    fun fix(l: Location) = GpsFix(l.elapsedRealtimeNanos, l.time, l.latitude, l.longitude, l.accuracy, l.hasAccuracy(), l.speed, l.hasSpeed(), l.bearing, l.altitude)

    fun on(ctx: Context): Boolean = Store.shiftOn(ctx)

    /** Elle başlatma: ön plan servisi olarak (Activity önde). */
    fun start(ctx: Context) { ctx.startForegroundService(Intent(ctx, ShiftService::class.java).setAction(ShiftMachine.ACTION_START)) }
    /** Bitirme düz başlatma (`STOP` ön plan türünde değil). */
    fun stop(ctx: Context) { ctx.startService(Intent(ctx, ShiftService::class.java).setAction(ShiftMachine.ACTION_STOP)) }

    /** WEEM cihaz kimliği (CORE.md 1.10): ilk açılışta üretilir. */
    fun deviceId(ctx: Context): String = DeviceId.ensure(Store.values(ctx))
}
