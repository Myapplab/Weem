package com.kuryelog.app.capture

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.kuryelog.app.WatchService
import com.kuryelog.app.core.OwnOverlays
import com.kuryelog.app.core.Store
import com.kuryelog.logic.capture.CaptureText
import com.kuryelog.logic.capture.ScreenReading
import com.kuryelog.logic.capture.ScreenReadingGuard
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.core.ShiftMachine

/**
 * R19/E7 (CAPTURE.md 5.2; RS `GpsService.kt:25-31, 100, 117-155, 293, 316-326`): vardiya boyunca ekran okuma 5 sn'de bir denetlenir;
 * 10 sn kapalı kalırsa kalıcı bildirim **5104** ve ("diğer uygulamaların üzerinde göster" izni varsa) kırmızı rozet; açılınca ya da
 * vardiya bitince ikisi de kalkar. Bir `ShiftListeners` dinleyicisi (`:app` kaydeder): `:core` vardiya servisi `:capture`'ı tanımaz.
 * Elle vardiya başlatma ekran okumaya bakmaz.
 */
// Yalnız uygulama bağlamı tutulur: sızıntı yok.
@SuppressLint("StaticFieldLeak")
class ScreenReadingWarning(ctx: Context) : ShiftMachine.Hooks {
    private val app = ctx.applicationContext
    private val h = Handler(Looper.getMainLooper())
    private val guard = ScreenReadingGuard()
    private var badge: TextView? = null
    private val screenTick = object : Runnable {
        override fun run() { runCatching { checkScreenReading() }; if (Store.shiftOn(app)) h.postDelayed(this, ScreenReadingGuard.CHECK_MS) }
    }

    /** Her vardiya başlatmasında (yapışkan yeniden başlatma dahil). Vardiya servisinin iş parçacığından gelebilir. */
    override fun onRunning() { h.post { h.removeCallbacks(screenTick); h.post(screenTick) } }

    override fun onEnd(startMs: Long, nowMs: Long, reason: String) {
        h.post { h.removeCallbacks(screenTick); guard.check(true, false, System.currentTimeMillis()); clearScreenReadingWarning() }
    }

    /** E7: one check of screen reading; the warning appears after [ScreenReadingGuard.GRACE_MS] off and goes when it is back. */
    private fun checkScreenReading() {
        if (!guard.check(CaptureStatus.screenReadingOn(app), Store.shiftOn(app), System.currentTimeMillis())) return
        if (guard.warning) warnScreenReading() else clearScreenReadingWarning()
        Store.event(app, if (guard.warning) "screen_reading_off" else "screen_reading_on")
    }

    private fun warnScreenReading() {
        val lang = Store.lang(app)
        val title = CaptureText.SCREEN_READING_OFF_TITLE.get(lang)
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, CaptureText.SCREEN_READING_CHANNEL.get(lang), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(app, NotificationIds.SCREEN_READING_OFF, CaptureStatus.accessibilitySettings(app),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val red = OwnOverlays.palette(app).danger
        runCatching { nm.notify(NotificationIds.SCREEN_READING_OFF, Notification.Builder(app, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title).setContentText(CaptureText.SCREEN_READING_OFF_TEXT.get(lang))
            .setColor(red).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).build()) }
        // The red badge over the courier apps needs "display over other apps" (the scooter's floating panel asks for it); else the notification alone.
        if (badge == null && Settings.canDrawOverlays(app)) runCatching {
            val d = app.resources.displayMetrics.density
            val dark = OwnOverlays.dark(app)
            val tv = TextView(app).apply {
                text = title; textSize = 13f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(if (dark) 0xff121212.toInt() else 0xffffffff.toInt()); setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
                background = GradientDrawable().apply { cornerRadius = 16 * d; setColor(red) }; elevation = 6 * d
                setOnClickListener { runCatching { app.startActivity(CaptureStatus.accessibilitySettings(app)) } }
            }
            val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = (64 * d).toInt() }
            app.getSystemService(WindowManager::class.java).addView(tv, lp); badge = tv
        }
    }

    private fun clearScreenReadingWarning() {
        runCatching { app.getSystemService(NotificationManager::class.java).cancel(NotificationIds.SCREEN_READING_OFF) }
        badge?.let { b -> runCatching { app.getSystemService(WindowManager::class.java).removeView(b) } }
        badge = null
    }

    private companion object { const val CHANNEL = "screen_reading" }
}

/** Ekran okumanın durumu kabuk için (vardiya kartı, Daha fazla); karar saf [ScreenReading]'de. */
object CaptureStatus {
    /** Kartın yenilenme aralığı = uyarı denetimi ([ScreenReadingGuard.CHECK_MS]). */
    const val CHECK_MS = ScreenReadingGuard.CHECK_MS
    /** Açık ve bağlı (RS `MainActivity.screenReadingOn`). */
    fun screenReadingOn(ctx: Context): Boolean =
        ScreenReading.on(Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), ctx.packageName, CaptureState.running)

    /** Vardiya kartının kırmızı satırı: vardiya açık ve ekran okuma çalışmıyorsa başlık (RS `MainActivity.kt:543`), değilse null. */
    fun offWarning(ctx: Context, lang: Lang, shiftOn: Boolean): String? =
        if (shiftOn && !screenReadingOn(ctx)) CaptureText.SCREEN_READING_OFF_TITLE.get(lang) else null

    /** K39: erişilebilirlik ayarı, destekleyen telefonda WEEM servisi vurgulu (RS `GpsService.accessibilitySettings`). */
    fun accessibilitySettings(ctx: Context): Intent {
        val service = ComponentName(ctx, WatchService::class.java).flattenToString()
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(":settings:fragment_args_key", service)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", service) })
    }

    /** Ayar açılamazsa genel ayarlar (RS `MainActivity.openAccessibilitySettings`). */
    fun openAccessibilitySettings(ctx: Context) {
        runCatching { ctx.startActivity(accessibilitySettings(ctx)) }.onFailure {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
