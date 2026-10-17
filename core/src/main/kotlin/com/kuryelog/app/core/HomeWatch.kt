package com.kuryelog.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.kuryelog.logic.core.HomeShift
import com.kuryelog.logic.core.HomeWatchCore
import com.kuryelog.logic.core.HomeWatchPlan

/**
 * Vardiya dışında ev izleyici (G1, CORE.md 1.7; RS `HomeWatch`/`HomeWatcher`): süreç başına bir tane. Sürücüler: vardiya servisi
 * (başı/sonu), uygulama açılışı ve (A3) okuma servislerinin tik'i; denetim en çok 5 sn'de bir. Karar `logic/core` [HomeWatchCore]'da.
 * "Her zaman konum" izni olmadan hiçbir şey yapmaz.
 */
object HomeWatch {
    // Yalnız uygulama bağlamı tutulur (Activity değil): sızıntı yok.
    @SuppressLint("StaticFieldLeak")
    @Volatile private var watcher: Watcher? = null

    private fun get(ctx: Context): Watcher = watcher ?: synchronized(this) { watcher ?: Watcher(ctx.applicationContext).also { watcher = it } }

    /** Ana iş parçacığı. Ucuz: en çok 5 sn'de bir denetler. */
    fun refresh(ctx: Context) = Store.guarded(ctx, "home_watch") { get(ctx).core.refresh(SystemClock.elapsedRealtime()) }
    /** Vardiya başı/sonu: 5 sn beklemeden. */
    fun recheck(ctx: Context) = Store.guarded(ctx, "home_watch") { get(ctx).core.recheck(SystemClock.elapsedRealtime()) }
    /** Ev değişti: eski GPS penceresi ve bekleme de atılır. */
    fun homeChanged(ctx: Context) = Store.guarded(ctx, "home_watch") {
        get(ctx).core.homeChanged()
        get(ctx).core.recheck(SystemClock.elapsedRealtime())
    }

    private class Watcher(private val ctx: Context) : LocationListener {
        val core = HomeWatchCore(Home.repository(ctx), Store.log(ctx), object : HomeWatchCore.Platform {
            override fun request(mode: HomeWatchPlan.Mode) = start(mode)
            override fun remove() { ctx.getSystemService(LocationManager::class.java)?.removeUpdates(this@Watcher) }
            override fun startShift(revision: Long) { ctx.startForegroundService(Home.startIntent(ctx, revision)) }
            override fun showHomeStart(revision: Long) = Notices.homeStart(ctx, revision)
        }, { Store.shiftOn(ctx) }, { Store.importing }, { Home.hasFine(ctx) to Home.hasBackground(ctx) })

        /** E6: LOW = dengeli güç 30 sn (FUSED API 31+, yoksa ağ, o da yoksa GPS); HIGH = GPS 2 sn (GPS'in kendi hızı, ≤20 m).
         *  İzin denetimi `HomeWatchCore.refresh`'te (`mayWatch`); geri alınmışsa SecurityException → `home_watch_no_permission`. */
        @SuppressLint("MissingPermission")
        private fun start(next: HomeWatchPlan.Mode) {
            val m = ctx.getSystemService(LocationManager::class.java) ?: throw IllegalStateException("LocationManager")
            val providers = m.getProviders(true)
            val high = next == HomeWatchPlan.Mode.HIGH
            if (high && LocationManager.GPS_PROVIDER in providers) {
                m.requestLocationUpdates(LocationManager.GPS_PROVIDER, HomeWatchPlan.HIGH_INTERVAL_MS, 0f, this, Looper.getMainLooper())
            } else if (Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in providers) {
                val req = LocationRequest.Builder(if (high) HomeWatchPlan.HIGH_INTERVAL_MS else HomeWatchPlan.LOW_INTERVAL_MS)
                    .setQuality(if (high) LocationRequest.QUALITY_HIGH_ACCURACY else LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                    .setMinUpdateIntervalMillis(if (high) HomeWatchPlan.HIGH_INTERVAL_MS else MIN_INTERVAL_MS).build()
                m.requestLocationUpdates(LocationManager.FUSED_PROVIDER, req, ctx.mainExecutor, this)
            } else {
                val p = if (!high && LocationManager.NETWORK_PROVIDER in providers) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                m.requestLocationUpdates(p, if (high) HomeWatchPlan.HIGH_INTERVAL_MS else HomeWatchPlan.LOW_INTERVAL_MS, 0f, this, Looper.getMainLooper())
            }
        }

        override fun onLocationChanged(loc: Location) {
            Store.guarded(ctx, "home_watch") {
                core.onFix(HomeShift.Fix(loc.elapsedRealtimeNanos / 1_000_000, loc.latitude, loc.longitude,
                    if (loc.hasAccuracy()) loc.accuracy.toDouble() else null, if (loc.hasSpeed()) loc.speed.toDouble() else null),
                    SystemClock.elapsedRealtime())
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private const val MIN_INTERVAL_MS = 15_000L
}
