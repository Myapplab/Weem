package com.kuryelog.app.scooter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.kuryelog.logic.core.NotificationIds
import org.json.JSONObject

/**
 * Eşleştirme uyarısı (SCOOTER.md 4.2; NB `PairAttention.java` satır satır): kullanıcının başlattığı her eşleştirme penceresi başına bir
 * uyarı (yeniden bağlanmalar dahil): ilk yanıtta titreşim + bildirim 8 "ŞİMDİ güç düğmesine bir kez kısa bas". BLE işlemi yok.
 */
class PairAttention(private val context: Context, private val audit: (String, JSONObject) -> Unit) {
    private var alerted = false

    init {
        set(false)
    }

    fun set(show: Boolean) {
        visible = show
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!show) {
            manager.cancel(NotificationIds.PAIRING)
            return
        }
        if (alerted) return
        alerted = true
        val vibrator = context.getSystemService(Vibrator::class.java)
        val available = vibrator != null && vibrator.hasVibrator()
        var requested = false
        var error = ""
        try {
            if (available) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 120, 300), -1))
                requested = true
            }
        } catch (e: RuntimeException) {
            error = e.javaClass.simpleName
        }
        audit("pair_attention", json("message", MESSAGE, "first_reply", true, "vibrator_available", available, "vibration_requested", requested, "vibration_error", error))
        try {
            val channel = NotificationChannel(CHANNEL, "Eşleştirme onayı", NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(null, null)
            channel.enableVibration(false)
            manager.createNotificationChannel(channel)
            val b = Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Scooter yanıt verdi")
                .setContentText(MESSAGE).setStyle(Notification.BigTextStyle().bigText(MESSAGE)).setOnlyAlertOnce(true)
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                b.setContentIntent(PendingIntent.getActivity(context, REQUEST, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            manager.notify(NotificationIds.PAIRING, b.build())
        } catch (e: RuntimeException) {
            audit("pair_attention_notification", json("error", e.javaClass.simpleName))
        }
    }

    companion object {
        const val MESSAGE = "ŞİMDİ güç düğmesine bir kez kısa bas"
        const val CHANNEL = "pairing-attention"
        private const val REQUEST = 2

        @Volatile
        private var visible = false

        fun visible(): Boolean = visible
    }
}
