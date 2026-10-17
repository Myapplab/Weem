package com.kuryelog.logic.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** CORE.md 4.1 (RS `CrashLog.kt:37-60`): `crashes.jsonl` yazımı, son rapor, kart için gizlenmemiş en yeni (A2.3). */
class CrashFileTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun reportsAreAppendedAtTheFilesRootNotInADayFolder() {
        val f = CrashFile(temp.root)
        f.save(JSONObject().put("at", 1L))
        f.save(JSONObject().put("at", 2L))
        assertEquals(File(temp.root, "crashes.jsonl"), f.file)
        assertEquals(listOf(1L, 2L), f.file.readLines().map { JSONObject(it).getLong("at") })
        assertTrue(f.file.readText().endsWith("}\n"))
        assertFalse("crashes.jsonl" in BackupFiles.logNames)
        assertEquals(2L, f.last()!!.getLong("at"))
    }

    @Test fun blankAndBrokenLinesAreSkipped() {
        val f = CrashFile(temp.root)
        assertNull(f.last())
        f.file.writeText("{\"at\":5}\n\n{broken\n")
        assertEquals(5L, f.last()!!.getLong("at"))
        f.save(JSONObject().put("at", 6L))
        assertEquals(listOf("{\"at\":5}", "{broken", "{\"at\":6}"), f.file.readLines())
    }

    @Test fun aLargeFileKeepsTheNewestTen() {
        val f = CrashFile(temp.root)
        val big = "x".repeat(25_000)
        for (i in 1..12) f.save(JSONObject().put("at", i.toLong()).put("stack", big))
        val ats = f.file.readLines().map { JSONObject(it).getLong("at") }
        assertEquals((3L..12L).toList(), ats)
    }

    @Test fun theCardShowsTheNewestReportNotYetHidden() {
        val crash = JSONObject().put("at", 100L)
        val exit = JSONObject().put("at", 200L).put("exit", 6)
        assertEquals(200L, CrashFile.unseen(listOf(crash, exit), 0L)!!.getLong("at"))
        assertEquals(100L, CrashFile.unseen(listOf(crash, null), 50L)!!.getLong("at"))
        assertNull(CrashFile.unseen(listOf(crash, exit), 200L))
        assertNull(CrashFile.unseen(listOf(null, null), 0L))
        assertEquals("crashSeenAt", KuryelogPrefs.CRASH_SEEN_AT)
    }
}
