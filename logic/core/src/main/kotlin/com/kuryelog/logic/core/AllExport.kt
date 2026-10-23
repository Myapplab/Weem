package com.kuryelog.logic.core

import java.io.FilterOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * S37 "tüm kayıtlar tek ZIP": `WEEM_hepsi_<gün>_<saat>.zip`; içinde her bölüm kendi ZIP'i olarak, biçimi değişmeden (ayrı ayrı içe aktarılabilir):
 * [ORDERS] sipariş kayıtları (tam yedek, [BackupExport]), [SCOOTER] Sürüş Paneli kayıtları (`NinebotLogger-<ms>.zip`), [RIDES] sürüş günlükleri.
 * Ağ kodu yok.
 */
object AllExport {
    const val ORDERS = "kayitlar/"
    const val SCOOTER = "scooter/"
    const val RIDES = "surus/"
    const val RIDES_FILE = "WEEM_surus_kayitlari.zip"

    private val stamp = DateTimeFormatter.ofPattern("HHmmss", Locale.US)

    fun fileName(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "${BackupExport.APP_NAME}_hepsi_${LogTime.day(nowMs, zone)}_${stamp.format(Instant.ofEpochMilli(nowMs).atZone(zone))}.zip"

    /** Bir bölüm: ZIP içindeki adı ve içeriğini yazan (kendi ZIP'ini yazar; akışı kapatsa da dış ZIP kapanmaz). */
    class Part(val name: String, val write: (OutputStream) -> Unit)

    /** Bölümler sırayla; hiç bölüm yoksa [BackupExport.NoLogs]. [out] sonunda kapanır. */
    fun zip(parts: List<Part>, out: OutputStream) {
        if (parts.isEmpty()) throw BackupExport.NoLogs()
        ZipOutputStream(out).use { z ->
            for (p in parts) {
                z.putNextEntry(ZipEntry(p.name))
                p.write(Unclosed(z))
                z.closeEntry()
            }
        }
    }

    /** İç ZIP'in `close()`'u dış girdiyi kapatmasın. */
    private class Unclosed(private val target: OutputStream) : FilterOutputStream(target) {
        override fun write(b: ByteArray, off: Int, len: Int) = target.write(b, off, len)
        override fun close() = flush()
    }
}
