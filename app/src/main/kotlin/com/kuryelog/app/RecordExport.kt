package com.kuryelog.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.kuryelog.app.core.Downloads
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.BackupExport
import com.kuryelog.logic.core.DailyPack
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.orders.AnalysisExport
import org.json.JSONObject
import java.io.File
import com.kuryelog.logic.app.Text as Str

/**
 * Kayıtlar → Yedek (R17, R18; RS `MainActivity.exportLogs/exportAnalysis/stage/saveAndShare` `:1557-1630`): kopyalar yazanın kilidi altında
 * ([Store], `importing` iken reddedilir), ZIP ayrı iş parçacığında İndirilenler/WEEM'e yazılır, sonra paylaşım seçicisi açılır. Ağ kodu yok.
 */
object RecordExport {
    private val main = Handler(Looper.getMainLooper())

    /** Tam yedek: bütün günler + `restaurants.json` ([date] null), bir gün ya da bir dosya ([BackupExport]; içe aktarılabilir). */
    fun exportLogs(ctx: Context, lang: Lang, date: String? = null, file: String? = null) = export(ctx, lang, "export") { app, staging ->
        val entries = stage(app, staging, date, file)
        Downloads.save(app, BackupExport.FOLDER, BackupExport.fileName(date, file, System.currentTimeMillis())) { BackupExport.zip(entries, it) }
    }

    /** Anonim analiz ZIP'i (R18): bütün günler, her dışa aktarmada yeni gizli anahtar, sonda `ANALIZ_ANONIM.json`; geri yükleme için değil. */
    fun exportAnalysis(ctx: Context, lang: Lang) = export(ctx, lang, "analysis") { app, staging ->
        val entries = stage(app, staging, null, null)
        val now = System.currentTimeMillis()
        Downloads.save(app, BackupExport.FOLDER, BackupExport.analysisName(now)) { AnalysisExport().zip(entries, it, versionName(app), now) }
    }

    private fun stage(app: Context, staging: File, date: String?, file: String?) = Store.locked(app) {
        check(!Store.importing)
        BackupExport.stage(Store.logsRoot(app), app.filesDir, date, file, staging)
    }

    fun versionName(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" }.getOrDefault("")

    private fun export(ctx: Context, lang: Lang, source: String, write: (Context, File) -> Uri) {
        val app = ctx.applicationContext
        if (Store.importing) { toast(app, Str.RECORDS_PREPARING.get(lang)); return }
        toast(app, Str.RECORDS_PREPARING.get(lang))
        Thread({
            val staging = File(app.cacheDir, "$source-${System.nanoTime()}")
            try {
                val uri = write(app, staging)
                main.post {
                    toast(app, Str.RECORDS_SAVED.get(lang))
                    Store.guarded(app, source) { share(app, uri, Str.RECORDS_SHARE.get(lang)) }
                }
            } catch (e: Throwable) {
                // RS: her hata (bellek dahil) tost olur; iş parçacığında yakalanmazsa uygulama kapanır.
                val message = if (e is BackupExport.NoLogs) Str.RECORDS_NO_LOGS.get(lang) else Str.ERROR.get(lang) + e.message
                runCatching { if (e !is BackupExport.NoLogs) Store.failure(app, source, e) }
                main.post { toast(app, message) }
            } finally { staging.deleteRecursively() }
        }, "weem-$source").start()
    }

    private fun share(ctx: Context, uri: Uri, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
}

/**
 * Günlük paket (R17; RS `DailyPack.afterShift/write`): vardiya dinleyicisi (`WeemApplication`, `cash_pending` → `daily_pack` → `orders`).
 * Tercih açıksa vardiyanın değdiği her gün için anonim analiz ZIP'i `Download/WEEM/gunluk/WEEM_gunluk_<gün>.zip`'e (günün önceki paketi
 * yenilenir). Ayrı iş parçacığı `daily-pack`; hata `daily_pack_failed` (`day`, `error` sınıf adı), başarı `daily_pack` (`day`, `files`).
 * Vardiya akışına hiçbir hata ulaşmaz. Ağ kodu yok.
 */
class DailyPackHook(ctx: Context) : com.kuryelog.logic.core.ShiftMachine.Hooks {
    private val app = ctx.applicationContext

    override fun onEnd(startMs: Long, nowMs: Long, reason: String) {
        if (!DailyPack.enabled(Store.values(app))) return
        Thread({
            for (d in DailyPack.days(startMs, nowMs)) runCatching { write(d) }
                .onFailure { Store.event(app, DailyPack.EVENT_FAILED, JSONObject().put("day", d).put("error", it.javaClass.simpleName)) }
        }, "daily-pack").start()
    }

    private fun write(date: String) {
        val staging = File(app.cacheDir, "daily-${System.nanoTime()}")
        try {
            val entries = Store.locked(app) {
                if (Store.importing) return
                DailyPack.stage(Store.logsRoot(app), app.filesDir, date, staging)
            } ?: return
            Downloads.save(app, DailyPack.FOLDER, DailyPack.fileName(date), replace = true) {
                AnalysisExport().zip(entries, it, RecordExport.versionName(app), System.currentTimeMillis())
            }
            Store.event(app, DailyPack.EVENT, JSONObject().put("day", date).put("files", entries.size))
        } finally { staging.deleteRecursively() }
    }
}
