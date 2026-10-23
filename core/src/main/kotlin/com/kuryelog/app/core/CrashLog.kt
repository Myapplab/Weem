package com.kuryelog.app.core

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.kuryelog.logic.core.CrashFile
import com.kuryelog.logic.core.CrashReport
import org.json.JSONObject
import kotlin.system.exitProcess

/**
 * Süreci kapatan istisna ([CrashReport]) Android uygulamayı kapatmadan önce `crashes.jsonl`'e yazılır (CORE.md 4.1, RS `CrashLog.kt`);
 * `WeemApplication` her giriş noktasından (Activity, servisler) önce kurar. Java işleyicisinin görmediği ölümler (yerel çökme, ANR)
 * Android'in çıkış kaydından gelir ([lastExit]). Buradaki işleyici `Store`'un hiçbir kilidini almaz: çöken iş parçacığı tutuyor olabilir.
 */
object CrashLog {
    @Volatile private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { CrashFile(app.filesDir).save(CrashReport.json(System.currentTimeMillis(), version(app), thread.name, e)) }
            // Android'in kendi işleyicisi çökmeyi kaydedip uygulamayı kapatır; yoksa süreç burada biter.
            if (previous != null) previous.uncaughtException(thread, e) else { Process.killProcess(Process.myPid()); exitProcess(10) }
        }
    }

    fun version(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

    /** Uygulamayı kapatan en yeni rapor. */
    fun last(ctx: Context): JSONObject? = CrashFile(ctx.filesDir).last()

    /**
     * Android 11+: Android'in kendisinin kaydettiği en yeni yerel çökme, ANR ya da başlatma hatası; S38: WEEM görünürken ya da vardiya servisi çalışırken
     * bellek/kaynak yüzünden kapatılması da ([CrashReport.exitLabel], [CrashReport.exitShown]).
     */
    fun lastExit(ctx: Context, now: Long = System.currentTimeMillis()): JSONObject? = if (Build.VERSION.SDK_INT >= 30) runCatching {
        ctx.getSystemService(ActivityManager::class.java)?.getHistoricalProcessExitReasons(ctx.packageName, 0, 5)
            ?.firstOrNull { CrashReport.exitShown(it.reason, it.timestamp, now, it.importance) }
            // Kayıt sürüm söylemez: güncellemeden önceki sürüme ait olabilir.
            ?.let { CrashReport.exitJson(it.timestamp, "?", it.reason, it.description, it.processName) }
    }.getOrNull() else null

    /** İkisinden kuryenin henüz gizlemediği en yenisi (kart). */
    fun unseen(ctx: Context): JSONObject? =
        CrashFile.unseen(listOf(last(ctx), lastExit(ctx)), Store.prefs(ctx).getLong(CrashFile.SEEN_KEY, 0L))

    fun hide(ctx: Context, j: JSONObject) { Store.prefs(ctx).edit().putLong(CrashFile.SEEN_KEY, j.optLong("at")).apply() }
}
