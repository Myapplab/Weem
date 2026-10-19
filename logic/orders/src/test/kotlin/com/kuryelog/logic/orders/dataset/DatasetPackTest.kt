package com.kuryelog.logic.orders.dataset

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rawstats'tan YolGo `DatasetMatchingTest.kt` (4; A6.2b, MAP.md 13.7, 13.18; aynı girdi → aynı çıktı): `DatasetPack.match`, ham klasör değişimi. Alan adları (`onlyYolgo` …) korunur. */
class DatasetPackTest {
    private fun offer(platform: String = "wolt", time: Long = 10000, fee: Double = 4.49) = JSONObject().put("platform", platform).put("firstSeenAtMs", time).put("feeAzn", fee)

    @Test
    fun exactKeysAndFiveSecondFallbackAreOneToOne() {
        val result = DatasetPack.match(
            mapOf("same" to offer(), "r" to offer(time = 20000), "raw-only" to offer(platform = "bolt")),
            mapOf("same" to offer(), "y" to offer(time = 25000), "yolgo-only" to offer(time = 40000)),
        )
        assertEquals(2, result.getInt("matched")); assertEquals(1, result.getInt("onlyRawstats")); assertEquals(1, result.getInt("onlyYolgo"))
        assertEquals(.5, result.getDouble("ratio"), 0.0)
    }

    @Test
    fun ambiguousFallbackIsNotGuessed() {
        val result = DatasetPack.match(mapOf("r1" to offer(), "r2" to offer()), mapOf("y" to offer()))
        assertEquals(0, result.getInt("matched")); assertEquals(1, result.getInt("ambiguousFallback"))
    }

    @Test
    fun missingFeeOrMoreThanFiveSecondsDoesNotMatch() {
        assertEquals(0, DatasetPack.match(mapOf("r" to offer()), mapOf("y" to offer(time = 15001))).getInt("matched"))
        assertEquals(0, DatasetPack.match(mapOf("r" to offer().put("feeAzn", JSONObject.NULL)), mapOf("y" to offer())).getInt("matched"))
    }

    @Test
    fun sameDayReplacementKeepsOnlyNewestFilesAndRecoversInterruptedSwap() {
        val dir = java.nio.file.Files.createTempDirectory("dataset-test").toFile()
        try {
            val raw = java.io.File(dir, "rawstats").apply { mkdir() }
            val first = java.io.File(raw, "economics-offers.jsonl").apply { writeText("{\"feeAzn\":4.49}\n") }
            val original = DatasetPack.sha(first)
            java.io.File(raw, "obsolete.jsonl").writeText("{}\n")
            assertTrue(raw.renameTo(java.io.File(dir, ".raw-previous")))
            DatasetPack.recoverRaw(dir)
            assertEquals(original, DatasetPack.sha(first))
            val stage = java.io.File(dir, ".next").apply { mkdir() }
            java.io.File(stage, "economics-offers.jsonl").writeText("{\"feeAzn\":5.49}\n")
            DatasetPack.replaceRaw(dir, stage)
            assertNotEquals(original, DatasetPack.sha(first)); assertEquals(1L, DatasetPack.lineCount(first))
            assertFalse(java.io.File(raw, "obsolete.jsonl").exists()); assertFalse(java.io.File(dir, ".raw-previous").exists())
        } finally { dir.deleteRecursively() }
    }
}
