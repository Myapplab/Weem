package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/** Rawstats `RecordingPolicyTest`'in iki testi (A3.2; CAPTURE.md 8): geri yükleme yolu denetimi ve yeniden içe aktarmada satır birleştirme (`BackupFiles`, A2.3). */
class BackupRestoreTest {
    @Test fun restoreOnlyAcceptsKnownLogsInsideAnActualDay() {
        assertEquals("2026-10-02/orders.jsonl", BackupFiles.checkedPath("2026-10-02/orders.jsonl"))
        assertEquals("restaurants.json", BackupFiles.checkedPath("restaurants.json"))
        for (bad in listOf("../orders.jsonl", "/2026-10-02/orders.jsonl", "2026-10-02/../../prefs", "2026-10-02\\orders.jsonl", "2026-99-99/gps.csv", "2026-10-02/unknown.json")) {
            try { BackupFiles.checkedPath(bad); fail(bad) } catch (expected: Exception) { }
        }
    }

    @Test fun reimportKeepsNewRowsAndDoesNotDoubleOldOrders() {
        val current = "order-a\norder-b\n"
        val merged = BackupFiles.mergeLines(current, "order-a\norder-c\n")
        assertEquals("order-a\norder-b\norder-c\n", merged)
        assertEquals(merged, BackupFiles.mergeLines(merged, "order-a\norder-c\n"))
    }
}
