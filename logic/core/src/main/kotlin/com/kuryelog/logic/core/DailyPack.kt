package com.kuryelog.logic.core

import java.io.File
import java.time.ZoneId

/**
 * Günlük paket (R17; RS `DailyPack.kt:16-63`, 0.22.0, kullanıcı 10 Ekim 2026): vardiya bitince, [KuryelogPrefs.DAILY_PACK] açıksa, vardiyanın
 * değdiği gün(ler)in ANONİM analiz ZIP'i (`AnalysisExport`, `logic/orders`; elle "anonim analiz" ile aynı izin listesi, her pakette yeni gizli
 * anahtar) `Downloads/<FOLDER>/<fileName>`'e yazılır; aynı günün önceki paketi önce silinir. Geri yükleme yedeği DEĞİL ([LogImporter] işaret
 * dosyasını reddeder). Ağ kodu yok. Burada saf parçalar; MediaStore ve iş parçacığı Android `:app`'te.
 */
object DailyPack {
    const val PREF = KuryelogPrefs.DAILY_PACK
    /** RS `"Rawstats/gunluk"`; WEEM: [BackupExport.FOLDER] altında. */
    const val FOLDER = BackupExport.FOLDER + "/gunluk"
    const val EVENT = "daily_pack"
    const val EVENT_FAILED = "daily_pack_failed"

    fun enabled(prefs: KuryelogPrefs.Values) = prefs.getBoolean(PREF, true)
    fun fileName(date: String) = "${BackupExport.APP_NAME}_gunluk_$date.zip"
    /** Vardiyanın değdiği günler ([startMs]–[endMs]; gece yarısını geçen vardiya iki gün). */
    fun days(startMs: Long, endMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<String> = listOf(LogTime.day(startMs, zone), LogTime.day(endMs, zone)).distinct()

    /**
     * RS `write`'ın kopyalama yarısı: [date] gününün kayıtları ve `restaurants.json` [staging]'e; günün dosyası yoksa null (paket yazılmaz).
     * Yazanla aynı kilit altında, `importing` değilken çağrılır.
     */
    fun stage(logsRoot: File, filesDir: File, date: String, staging: File): List<Pair<String, File>>? {
        val files = DailyRecords(logsRoot).files(date)
        if (files.isEmpty()) return null
        for (f in files) { val copy = File(staging, f.relativeTo(logsRoot).invariantSeparatorsPath); copy.parentFile?.mkdirs(); f.copyTo(copy) }
        val rf = File(filesDir, BackupExport.RESTAURANTS); if (rf.isFile) rf.copyTo(File(staging, rf.name))
        return BackupExport.entries(staging)
    }
}
