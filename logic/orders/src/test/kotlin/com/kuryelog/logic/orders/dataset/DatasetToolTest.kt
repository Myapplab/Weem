package com.kuryelog.logic.orders.dataset

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset

/** Çevrimdışı denetim aracı (MAP.md 13.10, 13.18 "Yeni"): `kontrol.py --self-test` ve WEEM'in ürettiği sentetik ZIP'in denetimi; python3 yoksa atlanır. */
class DatasetToolTest {
    private fun tool(): File? = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "tools/veri_seti/kontrol.py") }.firstOrNull { it.isFile }

    private fun python(vararg args: String): Pair<Int, String>? = try {
        val p = ProcessBuilder(listOf("python3", "-I") + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor() to out
    } catch (_: Exception) {
        null
    }

    @Test
    fun `öz-test geçer`() {
        val script = tool()
        assumeTrue(script != null)
        val r = python(script!!.path, "--self-test")
        assumeTrue(r != null)
        assertEquals(r!!.second, 0, r.first)
    }

    @Test
    fun `WEEM paketi şema ve hash denetiminden geçer`() {
        val script = tool()
        assumeTrue(script != null && python("--version") != null)
        val root = Files.createTempDirectory("weem-dataset-tool").toFile()
        try {
            val source = File(root, "src").apply { mkdirs() }
            val t = 1_791_295_200_000L // 2026-10-06
            File(source, "economics-offers.jsonl").writeText("{\"offerKey\":\"offer-1791295200000-1\",\"platform\":\"wolt\",\"feeAzn\":2.31,\"firstSeenAtMs\":$t,\"lastSeenAtMs\":${t + 1000},\"wall_ms\":$t,\"mono_ns\":1}\n")
            File(source, "economics-energy.jsonl").writeText("")
            File(source, "gps.csv").writeText("t,lat,lon\n")
            File(source, "screens.jsonl").writeText("")
            val dataRoot = File(root, "TeklifVeriSeti").apply { mkdirs() }
            val day = "2026-10-06"
            val zone = ZoneOffset.ofHours(4)
            val input = JSONObject("""{"displayedFeeAzn":2.31,"platformDistanceKm":2.8,"pickupAddress":"40.40912,49.86721","v2":{"offerKey":"offer-1791295200000-1","platform":"wolt","firstSeenAtMs":$t,"lastSeenAtMs":${t + 1000},"shown":{"km":2.8}}}""")
            val live = DatasetContract.measure(DatasetContract.flagUnknowns(DatasetContract.offer(input, t + 2000, 5, DatasetContract.point(40.4091, 49.867), "baku", zone)!!), t + 2000, t + 1000, "observer")
            DatasetFiles.append(dataRoot, day, DatasetRecorder.FILE, live)
            DatasetFiles.append(dataRoot, day, DatasetShifts.FILE, DatasetShifts.row(t - 3_600_000, null, t, 3)!!)
            val result = DatasetPack.collect(dataRoot, source, day, ByteArray(32) { it.toByte() }, "0.0-test", 1, zone, t + 3000, 9)
            val zip = File(result.getString("zip"))
            val r = python(script!!.path, zip.path)
            assumeTrue(r != null)
            assertEquals(r!!.second, 0, r.first)
        } finally {
            root.deleteRecursively()
        }
    }
}
