package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** WEEM: `everyFileWrittenByTheAppIsInTheBackupList` (WEEM kaynağını `append(…, "<ad>")` için tarar) gün dosyası yazan kod
 *  gelince A2.3'te eklenir (CORE.md 6.1). Burada saf kısım. */
class DailyRecordsTest {
    @get:Rule val temp = TemporaryFolder()
    private fun setup(): DailyRecords {
        val day = File(temp.root, "2026-10-02").apply { mkdirs() }
        File(day, "scooter.jsonl").writeText("scooter\n")
        File(day, "gps.csv").writeText("gps\n")
        File(day, "unrecognized.txt").writeText("keep")
        return DailyRecords(temp.root)
    }
    @Test fun onlyRecognizedDatedLogsBecomeDailyRecords() {
        val r = setup()
        File(temp.root, "not-a-date").mkdirs()
        assertEquals(listOf("2026-10-02"), r.days().map { it.date })
        assertEquals(2, r.days().single().files.size)
        assertEquals(12L, r.days().single().bytes)
    }
    @Test fun archiveAndRestoreKeepEveryLogByte() {
        val r = setup()
        r.archive("2026-10-02", enabled = true)
        assertTrue(r.days().single().archived)
        r.archive("2026-10-02", "scooter.jsonl", true)
        assertTrue(r.archived("2026-10-02", "scooter.jsonl"))
        r.archive("2026-10-02", enabled = false)
        assertFalse(r.days().single().archived)
        assertEquals("scooter\n", File(temp.root, "2026-10-02/scooter.jsonl").readText())
    }
    @Test fun deleteOneFileDoesNotRemoveOtherRecords() {
        val r = setup()
        r.delete("2026-10-02", "scooter.jsonl")
        assertEquals(listOf("gps.csv"), r.days().single().files.map { it.name })
    }
    @Test fun dailyDeleteLeavesUnknownFilesAndOtherDaysAlone() {
        val r = setup()
        val other = File(temp.root, "2026-10-01").apply { mkdirs() }
        File(other, "orders.jsonl").writeText("old\n")
        r.delete("2026-10-02")
        assertEquals("keep", File(temp.root, "2026-10-02/unrecognized.txt").readText())
        assertEquals(listOf("2026-10-01"), r.days().map { it.date })
    }
    @Test fun pathTraversalAndKeyFilesAreRejected() {
        val r = setup()
        for ((day, name) in listOf("../" to "gps.csv", "2026-10-02" to "../scooter_keys", "2026-99-99" to "gps.csv")) {
            try { r.delete(day, name); fail("Unsafe path accepted") } catch (_: IllegalArgumentException) { } catch (_: java.time.DateTimeException) { }
        }
        assertEquals("2026-10-02/scooter.jsonl", BackupFiles.checkedPath("2026-10-02/scooter.jsonl"))
        try { BackupFiles.checkedPath("scooter_keys"); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun economicsLogsAreExportedImportableAndDeletedWithTheDay() {
        // Regression: 0.8 wrote these files but they were outside the backup list,
        // so the ZIP export skipped them and deleting the day left them on disk.
        val r=setup(); val day=File(temp.root,"2026-10-02")
        for (name in listOf("economics-waits.jsonl","economics-exposure.jsonl")) {
            File(day,name).writeText("{}\n")
            assertEquals("2026-10-02/$name",BackupFiles.checkedPath("2026-10-02/$name"))
            assertTrue(r.files("2026-10-02").any { it.name == name })
        }
        r.delete("2026-10-02")
        assertFalse(File(day,"economics-waits.jsonl").exists())
        assertFalse(File(day,"economics-exposure.jsonl").exists())
    }
    @Test fun newScooterAveragesAndPlatformRecordsAreExportable() {
        val r=setup(); val day=File(temp.root,"2026-10-02")
        for (name in listOf("scooter_averages.jsonl","platforms.jsonl","errors.jsonl")) {
            File(day,name).writeText("{}\n")
            assertEquals("2026-10-02/$name",BackupFiles.checkedPath("2026-10-02/$name"))
        }
        assertEquals(5,r.files("2026-10-02").size)
    }
}
