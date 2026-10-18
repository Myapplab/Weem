package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.RepoSource
import org.junit.Assert.*
import org.junit.Test

/**
 * A4.3 Android bağlantısı (kaynak taramaları; ekran telefonda denenmedi). Rawstats karşılıkları: `DailyPackTest.shiftEndPacksAnonymousExportWithoutNetwork`,
 * `OfferNetLineTest.analysisExportButtonSitsUnderKayitlarNextToTheFullExport`, `CrashGuardTest` (vardiya sonunda `DailyPack.afterShift` korumalı).
 */
class RecordExportWiringTest {
    private val app = RepoSource.text("app/src/main/kotlin/com/kuryelog/app/WeemApplication.kt")
    private val export = RepoSource.text("app/src/main/kotlin/com/kuryelog/app/RecordExport.kt")
    private val records = RepoSource.text("app/src/main/kotlin/com/kuryelog/app/RecordsScreen.kt")

    @Test fun shiftEndPacksAnonymousExportWithoutNetwork() {
        // ORDERS.md 4.2: cash_pending → daily_pack → orders; every listener is guarded by ShiftListeners (CrashGuardTest).
        val cash = app.indexOf("ShiftHub.listeners.add(\"cash_pending\""); val pack = app.indexOf("ShiftHub.listeners.add(\"daily_pack\", DailyPackHook(this))")
        val orders = app.indexOf("ShiftHub.listeners.add(\"orders\"")
        assertTrue(cash >= 0 && pack > cash && orders > pack)
        val hook = RepoSource.body(export, "class DailyPackHook", "\n}\n")
        assertTrue(hook.contains("AnalysisExport().zip("))
        assertTrue(hook.contains("Downloads.save(app, DailyPack.FOLDER, DailyPack.fileName(date), replace = true)"))
        assertTrue(hook.contains("Thread({") && hook.contains("\"daily-pack\""))
        for (src in listOf(export, RepoSource.text("core/src/main/kotlin/com/kuryelog/app/core/Downloads.kt"),
            RepoSource.text("logic/core/src/main/kotlin/com/kuryelog/logic/core/BackupExport.kt"), RepoSource.text("logic/core/src/main/kotlin/com/kuryelog/logic/core/DailyPack.kt")))
            for (net in listOf("java.net", "HttpURLConnection", "okhttp", "Socket")) assertFalse(net, src.contains(net))
    }

    @Test fun analysisExportButtonSitsUnderKayitlarNextToTheFullExport() {
        val tools = RepoSource.body(records, "private fun BackupTools(", "\n}\n")
        val daily = tools.indexOf("Str.RECORDS_DAILY_PACK.get(lang)"); val full = tools.indexOf("RecordExport.exportLogs(ctx, lang)")
        val anon = tools.indexOf("RecordExport.exportAnalysis(ctx, lang)")
        assertTrue(daily >= 0 && full > daily && anon > full)
        assertTrue(records.indexOf("Str.RECORDS_IMPORT.get(lang)") < records.indexOf("BackupTools(lang)"))
        assertTrue(export.contains("AnalysisExport().zip(entries, it, versionName(app), now)"))
        // The full export still zips every staged file unchanged.
        assertTrue(RepoSource.text("logic/core/src/main/kotlin/com/kuryelog/logic/core/BackupExport.kt").contains("f.inputStream().use { it.copyTo(z) }"))
        // A day and a single file are shared too (RS day dialog → "Kaydı paylaş").
        assertTrue(records.contains("RecordExport.exportLogs(ctx, lang, date)") && records.contains("RecordExport.exportLogs(ctx, lang, date, name)"))
    }

    @Test fun copiesHoldTheWritersLock() {
        // Regresyon (A4.3): RS `synchronized(Store)` writers' lock was `Store.append`'s; in WEEM that lock is the LogStore instance, so
        // `synchronized(Store)` locked nothing the writers take (A3.8 archive/delete had it). Copies, archive and delete use Store.locked.
        val store = RepoSource.text("core/src/main/kotlin/com/kuryelog/app/core/Store.kt")
        assertTrue(store.contains("inline fun <T> locked(ctx: Context, block: () -> T): T = synchronized(log(ctx)) { block() }"))
        assertTrue(RepoSource.text("logic/core/src/main/kotlin/com/kuryelog/logic/core/LogStore.kt").contains("@Synchronized fun append("))
        val android = RepoSource.root.walkTopDown().onEnter { it.name != "build" && it.name != ".gradle" && it.name != "logic" && it.name != "tools" }
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.invariantSeparatorsPath }.toList()
        assertTrue(android.size > 10)
        for (f in android) assertFalse(f.path, f.readText().contains("synchronized(Store)"))
        for (s in listOf(RepoSource.body(export, "private fun stage(", "\n\n"), RepoSource.body(export, "private fun write(date: String)", "Downloads.save"),
            RepoSource.body(records, "fun change(", "onChanged()"))) {
            assertTrue(s, s.contains("Store.locked("))
            assertTrue(s, s.contains("Store.importing"))
        }
    }

    @Test fun encryptedWeeklyBackupFollowsS22() {
        val n3 = RepoSource.text("app/src/main/kotlin/com/kuryelog/app/EncryptedBackups.kt")
        // Shift end: after the daily pack, before the orders close; only with a password; own thread.
        val pack = app.indexOf("ShiftHub.listeners.add(\"daily_pack\""); val enc = app.indexOf("ShiftHub.listeners.add(\"encrypted_backup\", EncryptedBackupHook(this))")
        assertTrue(enc > pack && app.indexOf("ShiftHub.listeners.add(\"orders\"") > enc)
        val hook = RepoSource.body(n3, "class EncryptedBackupHook", "\n}\n")
        assertTrue(hook.contains("if (!BackupVault(app).hasPassword()) return") && hook.contains("Thread({") && hook.contains("EncryptedBackup.weeks(startMs, nowMs)"))
        // The password lives in its own Keystore-sealed prefs file, never in kuryelog (which the backup carries).
        assertTrue(n3.contains("const val FILE = \"weem-backup\"") && n3.contains("\"AndroidKeyStore\""))
        assertFalse(n3.contains("KuryelogPrefs.FILE"))
        // Written under the writers' lock, prefs through encodePrefs (no home), the file replaced per week.
        val write = RepoSource.body(n3, "fun write(ctx: Context, week: EncryptedBackup.Week)", "\n    }\n")
        assertTrue(write.contains("Store.locked(app)") && write.contains("Store.importing") && write.contains("EncryptedBackup.encodePrefs(Store.prefs(app).all)"))
        assertTrue(write.contains("Downloads.save(app, EncryptedBackup.FOLDER, week.fileName, replace = true"))
        // Restore: like the import (shift off, importing flag), settings only where missing, rows only added.
        val restore = RepoSource.body(n3, "fun restore(ctx: Context", "private fun applyRest")
        assertTrue(restore.contains("Store.shiftOn(app)") && restore.contains("Store.importing = true") && restore.contains("EncryptedBackup.restore("))
        assertTrue(n3.contains("EncryptedBackup.missingPrefs(opened.prefs, prefs.all.keys)") && n3.contains("EncryptedBackup.mergeTrip("))
        // "Uygulamadan sil" only after an export, never today's open shift.
        val delete = RepoSource.body(n3, "fun deleteWeek(", "\n    }\n")
        assertTrue(delete.contains("check(BackupVault(ctx).exported(week))") && delete.contains("EncryptedBackup.deletableDays(") && delete.contains("Store.shiftOn(ctx)"))
        for (net in listOf("java.net", "HttpURLConnection", "okhttp", "Socket")) {
            assertFalse(net, n3.contains(net))
            assertFalse(net, RepoSource.text("logic/core/src/main/kotlin/com/kuryelog/logic/core/EncryptedBackup.kt").contains(net))
        }
        assertTrue(records.contains("EncryptedBackupTools(lang)"))
    }
}
