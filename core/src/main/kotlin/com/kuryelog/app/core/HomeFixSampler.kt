package com.kuryelog.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kuryelog.logic.core.HomeFixes

/**
 * "Şu anki konumumu kullan" (CORE.md 2.2; RS `HomeFixSampler`): etkin sağlayıcılardan (pasif hariç) son bilinen konum + 1 sn'lik
 * güncelleme, 6 sn sonra en iyi taze örnek. Ekran kapanınca [cancel]. Seçim kurallar `logic/core` [HomeFixes]'te.
 */
class HomeFixSampler(private val ctx: Context) : LocationListener {
    private val handler = Handler(Looper.getMainLooper())
    private var fixes = HomeFixes()
    private var finish: ((HomeFixes.Sample?) -> Unit)? = null
    private var progress: ((HomeFixes.Sample) -> Unit)? = null
    private val manager get() = ctx.getSystemService(LocationManager::class.java)
    private val timeout = Runnable { val cb = finish; val best = fixes.bestAt(SystemClock.elapsedRealtime()); cancel(); cb?.invoke(best) }

    /** İzin ya da etkin sağlayıcı yoksa false (hiçbir şey başlamaz). İzin `Home.hasFine` ile denetlenir; arada geri alınırsa SecurityException yakalanır. */
    @SuppressLint("MissingPermission")
    fun start(onProgress: (HomeFixes.Sample) -> Unit, onFinish: (HomeFixes.Sample?) -> Unit): Boolean {
        cancel()
        if (!Home.hasFine(ctx)) return false
        val m = manager ?: return false
        val providers = runCatching { m.getProviders(true) }.getOrDefault(emptyList()).filter { it != LocationManager.PASSIVE_PROVIDER }
        if (providers.isEmpty()) return false
        fixes = HomeFixes(); progress = onProgress; finish = onFinish
        var requested = false
        for (provider in providers) try {
            m.getLastKnownLocation(provider)?.let { accept(it) }
            m.requestLocationUpdates(provider, 1_000L, 0f, this, Looper.getMainLooper())
            requested = true
        } catch (_: SecurityException) {
            // İzin istek kaydedilirken geri alınmış olabilir.
        } catch (_: IllegalArgumentException) {
            // Sağlayıcı anlık listeden sonra kaybolmuş olabilir.
        }
        if (!requested) { cancel(); return false }
        handler.postDelayed(timeout, HomeFixes.COLLECT_MS)
        return true
    }

    private fun accept(l: Location) {
        val s = HomeFixes.Sample(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy.toDouble() else null, l.elapsedRealtimeNanos / 1_000_000)
        if (fixes.add(s, SystemClock.elapsedRealtime())) fixes.best?.let { progress?.invoke(it) }
    }
    override fun onLocationChanged(location: Location) { if (finish != null) accept(location) }
    fun cancel() { handler.removeCallbacks(timeout); runCatching { manager?.removeUpdates(this) }; finish = null; progress = null }
    @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
