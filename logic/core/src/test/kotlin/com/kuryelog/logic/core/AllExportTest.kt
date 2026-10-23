package com.kuryelog.logic.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** S37: tüm kayıtlar tek ZIP — her bölüm kendi ZIP'i olarak, iç ZIP'in kapanması dış ZIP'i bozmaz; boşsa "kayıt yok". */
class AllExportTest {
    private val baku = ZoneId.of("Asia/Baku")

    private fun inner(vararg files: Pair<String, String>): (java.io.OutputStream) -> Unit = { out ->
        ZipOutputStream(out).use { z -> for ((n, c) in files) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
    }

    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; map[e.name] = z.readBytes() } }
        return map
    }

    @Test fun fileNameIsDayAndTime() {
        // 2026-10-23 10:31:05 Bakü.
        assertEquals("WEEM_hepsi_2026-10-23_103105.zip", AllExport.fileName(1_792_737_065_000L, baku))
    }

    @Test fun partsAreNestedZipsThatOpenUnchanged() {
        val out = ByteArrayOutputStream()
        AllExport.zip(
            listOf(
                AllExport.Part(AllExport.ORDERS + "WEEM_all_logs_1.zip", inner("2026-10-23/orders.jsonl" to "{\"a\":1}")),
                AllExport.Part(AllExport.SCOOTER + "NinebotLogger-1.zip", inner("manifest.json" to "{}", "sessions.jsonl" to "x")),
            ),
            out,
        )
        val outer = entries(out.toByteArray())
        assertEquals(listOf("kayitlar/WEEM_all_logs_1.zip", "scooter/NinebotLogger-1.zip"), outer.keys.toList())
        val scooter = entries(outer.getValue("scooter/NinebotLogger-1.zip"))
        assertEquals(listOf("manifest.json", "sessions.jsonl"), scooter.keys.toList())
        assertArrayEquals("{\"a\":1}".toByteArray(), entries(outer.getValue("kayitlar/WEEM_all_logs_1.zip")).getValue("2026-10-23/orders.jsonl"))
    }

    @Test(expected = BackupExport.NoLogs::class)
    fun nothingToExportIsNoLogs() {
        AllExport.zip(emptyList(), ByteArrayOutputStream())
    }
}
