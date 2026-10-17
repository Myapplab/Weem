package com.kuryelog.app.core

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.core.ShiftMachine

/**
 * Vardiya ön plan servisi (G1, CORE.md 1.2–1.5; RS `GpsService`), tür `location`. Her karar ve sıra [ShiftMachine]'de; burada yalnız
 * Android çağrıları. Konum geri çağrısı ve her adım korumalı (makine kaydeder, fırlatmaz).
 */
class ShiftService : Service(), LocationListener {
    private val machine by lazy {
        ShiftMachine(Store.values(this), Store.log(this), Home.repository(this), Shift.AndroidClock, platform,
            importing = { Store.importing }, lang = { Store.lang(this) }, track = Shift.track)
    }

    private val platform = object : ShiftMachine.Platform {
        override fun startForeground() {
            startForeground(NotificationIds.SHIFT_FGS, Notices.shift(this@ShiftService), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }
        override fun stopForeground() = stopForeground(STOP_FOREGROUND_REMOVE)
        override fun stopSelf() = this@ShiftService.stopSelf()
        // İzin geri alınmışsa SecurityException: ShiftMachine.requestGps yakalar ve `gps_no_permission` yazar.
        @SuppressLint("MissingPermission")
        override fun requestGps() {
            // Durunca da yeni fix gelsin: mesafe filtresi yok (RS).
            getSystemService(LocationManager::class.java)
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_INTERVAL_MS, 0f, this@ShiftService, Looper.getMainLooper())
        }
        override fun removeGps() { getSystemService(LocationManager::class.java)?.removeUpdates(this@ShiftService) }
        override fun showShiftInterrupted() = Notices.shiftInterrupted(this@ShiftService)
        override fun cancel(id: Int) = Notices.cancel(this@ShiftService, id)
        override fun recheckHomeWatch() { HomeWatch.recheck(this@ShiftService) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val revision = intent?.takeIf { it.hasExtra(ShiftMachine.EXTRA_HOME_REVISION) }?.getLongExtra(ShiftMachine.EXTRA_HOME_REVISION, -1L)
        return when (machine.onStartCommand(intent?.action, revision)) {
            ShiftMachine.StartResult.STICKY -> START_STICKY
            ShiftMachine.StartResult.NOT_STICKY -> START_NOT_STICKY
        }
    }

    override fun onLocationChanged(loc: Location) = machine.onLocation(Shift.fix(loc))

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) = machine.onProviderDisabled()

    override fun onDestroy() {
        runCatching { getSystemService(LocationManager::class.java)?.removeUpdates(this) }
        super.onDestroy()
    }

    private companion object {
        const val GPS_INTERVAL_MS = 2_000L
    }
}
