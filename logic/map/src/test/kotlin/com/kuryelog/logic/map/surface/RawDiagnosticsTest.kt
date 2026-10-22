package com.kuryelog.logic.map.surface

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Ham tanı kaydı (A10.4c). YolGo'da `RawDiagnostics.kt` doğrudan testsizdi (ekran/servis kodu): beklenen davranış kodundan yazıldı —
 * dakikalık dosya, 10 dakika sınırı, başka boot'un dosyaları silinir, gün silinince gider, dışa aktarma eskiden yeniye.
 */
class RawDiagnosticsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun raw(keep: Int = 10) = RawDiagnostics(tmp.newFolder(), keep)
    private fun row(i: Int) = JSONObject().put("i", i)
    private fun ms(minute: Long, extraMs: Long = 0) = minute * 60_000 + extraMs

    @Test fun rowsOfTheSameMinuteShareOneFile() {
        val r = raw()
        r.append("d", "b", ms(5, 100), row(1)); r.append("d", "b", ms(5, 2_000), row(2))
        assertEquals(1, r.fileCount())
        val out = tmp.newFile()
        assertEquals(2, r.export(out))
        assertEquals(listOf(1, 2), out.readLines().map { JSONObject(it).getInt("i") })
    }

    @Test fun onlyTheLastTenMinutesAreKept() {
        val r = raw()
        for (m in 0L..14L) r.append("d", "b", ms(m), row(m.toInt()))
        assertEquals(10, r.fileCount())
        val out = tmp.newFile(); r.export(out)
        assertEquals((5..14).toList(), out.readLines().map { JSONObject(it).getInt("i") })
    }

    @Test fun anotherBootAndAnotherDayFilesAreDeleted() {
        val r = raw()
        r.append("d", "old", ms(100), row(1))
        r.append("d", "new", ms(1), row(2)) // boot sıfırlandı: eski boot'un dakika sayıları anlamsız
        assertEquals(1, r.fileCount())
        val out = tmp.newFile(); r.export(out)
        assertEquals(listOf(2), out.readLines().map { JSONObject(it).getInt("i") })
    }

    @Test fun removeDayDeletesThatDaysFilesAndClearDeletesAll() {
        val r = raw()
        r.append("d1", "b", ms(1), row(1))
        r.removeDay("d1")
        assertEquals(0, r.fileCount())
        r.append("d1", "b", ms(2), row(2)); r.clear()
        assertEquals(0, r.fileCount())
        val out = tmp.newFile()
        assertEquals(0, r.export(out))
    }

    @Test fun exportFlushesBufferedRowsAndKeepsWriting() {
        val r = raw()
        r.append("d", "b", ms(3, 10), row(1))
        r.append("d", "b", ms(3, 20), row(2)) // 1 sn dolmadı: tamponda
        val out = tmp.newFile()
        assertEquals(2, r.export(out))
        r.append("d", "b", ms(3, 30), row(3))
        assertEquals(3, r.export(tmp.newFile()))
    }

    @Test fun sampleJsonUsesTheYolgoFieldNamesAndNoLocation() {
        val j = RawSample("d", "b", "d:free", 1, 2, 3, 4, true, doubleArrayOf(1.0, 2.0, 3.0), null, 0.5, 6.0, 4.0, "p", true, false, true).toJson()
        assertEquals(2, j.getInt("v")); assertEquals("d:free", j.getString("leg")); assertTrue(j.isNull("worldUp"))
        assertEquals(3, j.getJSONArray("accelerationMS2").length()); assertEquals(6.0, j.getDouble("speedMS"), 0.0)
        assertFalse(j.has("lat") || j.has("lon"))
    }
}
