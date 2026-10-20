package com.kuryelog.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.kuryelog.logic.map.geo.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Ev seçicinin açılış konumu (YolGo `_locate`): yüksek doğrulukla en çok 8 sn; izin yoksa ya da konum gelmezse hata (ekran "Konum alınamadı · Tekrar dene"). */
object HomePickLocate {
    private const val TIMEOUT_MS = 8_000L

    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): GeoPoint {
        check(ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { "no permission" }
        val lm = checkNotNull(ctx.getSystemService(LocationManager::class.java))
        return withTimeout(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val l = object : LocationListener {
                    override fun onLocationChanged(loc: android.location.Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(GeoPoint(loc.latitude, loc.longitude))
                    }
                }
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, l, Looper.getMainLooper())
                cont.invokeOnCancellation { lm.removeUpdates(l) }
            }
        }
    }
}
