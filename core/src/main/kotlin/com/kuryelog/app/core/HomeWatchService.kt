package com.kuryelog.app.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import com.kuryelog.logic.core.NotificationIds

/**
 * S44 (kullanıcı: "evden ayrılınca otomatik vardiya başlamıyor"): vardiya dışında ev izlenirken ([HomeWatch], CORE.md 1.7) süreç ön planda kalır.
 * Ekran kapalıyken Android arka plandaki uygulamaya konumu saatte birkaç kez verir (ekran okuma servisi yalnız ekran açıkken ön plan sayılır):
 * telefon cepteyken evden çıkışta hiç fix gelmiyor, vardiya başlamıyordu. Bu servis (tür location, sessiz MIN bildirim 5105) çalışırken
 * izleyicinin istekleri kısılmaz. Koşulu `HomeWatchCore` verir (`keepAlive`): ev kayıtlı, vardiya kapalı, "her zaman konum" izni; koşul
 * kalkınca (vardiya başladı, ev silindi, izin geri alındı) durur. Her başlatış startForeground'a varır (CrashGuard).
 */
class HomeWatchService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val ok = runCatching { startForeground(NotificationIds.HOME_WATCH_FGS, Notices.homeWatch(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) }.isSuccess
        on = ok
        if (!ok) {
            failedAt = SystemClock.elapsedRealtime()
            stopSelf()
            return START_NOT_STICKY
        }
        // Sistem yeniden başlattıysa (START_STICKY, yeni süreç) izleyici de kurulur; koşul yoksa izleyici bu servisi durdurur.
        HomeWatch.recheck(this)
        return START_STICKY
    }

    companion object {
        private const val ACTION_STOP = "com.kuryelog.app.core.HOME_WATCH_STOP"
        /** Android arka plandan başlatmayı reddettiyse en erken bu kadar sonra yeniden denenir (izleyici 5 sn'de bir sorar). */
        private const val RETRY_MS = 60_000L
        @Volatile private var on = false
        @Volatile private var failedAt = 0L

        /** Ucuz ve tekrar güvenli: durum aynıysa hiçbir şey yapmaz. */
        fun set(ctx: Context, run: Boolean) {
            if (run == on) return
            if (run && failedAt != 0L && SystemClock.elapsedRealtime() - failedAt < RETRY_MS) return
            on = run
            val i = Intent(ctx, HomeWatchService::class.java)
            runCatching {
                if (run) ctx.startForegroundService(i) else ctx.startService(i.setAction(ACTION_STOP))
            }.onFailure {
                if (run) { on = false; failedAt = SystemClock.elapsedRealtime() }
            }
        }
    }
}
