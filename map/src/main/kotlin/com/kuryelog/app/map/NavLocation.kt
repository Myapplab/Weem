package com.kuryelog.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.kuryelog.logic.map.nav.NavFix
import com.kuryelog.logic.map.nav.NavLocator
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Navigasyonun konum kaynağı (MAP.md 7.6, 7.16 `NavLocation`): GPS 500 ms; harita önde, gün açık ya da yalnız-nav iken açılır ([setActive]).
 * Gün açıkken ön plan servisi `:core` vardiya servisidir; bu istek onun yanında ikinci istek olarak çalışır (Android birleştirir).
 * S38 (pil): harita arkadayken ve rota yokken 1 sn ([SLOW_INTERVAL_MS]); gün motoru, sürüş günlüğü ve öğrenme zamana göre çalışır, 1 sn yeter.
 * Süzgeç (`_emit`): doğruluk 0–65 m, ≤15 sn taze, zaman damgası bir öncekinden büyük.
 */
internal class NavLocation(private val app: Context, private val onFix: (NavFix) -> Unit) : NavLocator {
    private val lm = app.getSystemService(LocationManager::class.java)
    private var listening = false
    private var intervalMs = INTERVAL_MS
    private var lastAt = 0L
    private val listener = LocationListener { loc -> emit(loc.toNavFix()) }

    /** [fast] harita önde ya da rota açık: 500 ms; değilse 1 sn. Aralık değişince istek yenilenir (aynı dinleyici: Android eskisinin yerine koyar). */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun setActive(on: Boolean, fast: Boolean = true) {
        val interval = if (fast) INTERVAL_MS else SLOW_INTERVAL_MS
        if (on == listening && (!on || interval == intervalMs)) return
        if (!on) {
            runCatching { lm?.removeUpdates(listener) }
            listening = false
            return
        }
        if (lm == null || !granted()) return
        listening = runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 0f, listener, Looper.getMainLooper())
            true
        }.getOrDefault(false)
        if (listening) intervalMs = interval
    }

    @Synchronized
    private fun emit(f: NavFix) {
        val age = System.currentTimeMillis() - f.atMs
        if (!f.accuracyM.isFinite() || f.accuracyM < 0 || f.accuracyM > 65 || age < -5_000 || age > 15_000) return
        if (f.atMs <= lastAt) return
        lastAt = f.atMs
        onFix(f)
    }

    private fun granted() = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override suspend fun ensurePermission(): Boolean = granted() && lm != null && runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    override suspend fun currentFix(): NavFix? {
        val m = lm ?: return null
        if (!granted()) return null
        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { c ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = android.os.CancellationSignal()
                    c.invokeOnCancellation { signal.cancel() }
                    m.getCurrentLocation(LocationManager.GPS_PROVIDER, signal, executor) { loc -> c.resume(loc?.toNavFix()) }
                } else {
                    @Suppress("DEPRECATION")
                    m.requestSingleUpdate(LocationManager.GPS_PROVIDER, { loc: Location -> c.resume(loc.toNavFix()) }, Looper.getMainLooper())
                }
            }
        }?.also { emit(it) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): NavFix? {
        val m = lm ?: return null
        if (!granted()) return null
        val gps = runCatching { m.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        val net = runCatching { m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        return listOfNotNull(gps, net).maxByOrNull { it.time }?.toNavFix()
    }

    companion object {
        const val INTERVAL_MS = 500L
        const val SLOW_INTERVAL_MS = 1_000L
        private val executor = Executors.newSingleThreadExecutor()
    }
}

internal fun Location.toNavFix() = NavFix(
    atMs = time,
    lat = latitude,
    lon = longitude,
    accuracyM = if (hasAccuracy()) accuracy.toDouble() else Double.POSITIVE_INFINITY,
    speedMps = if (hasSpeed()) speed.toDouble() else null,
    headingDeg = if (hasBearing()) bearing.toDouble() else null,
)
