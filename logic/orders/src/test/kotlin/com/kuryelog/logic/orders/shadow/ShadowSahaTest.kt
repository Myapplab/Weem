package com.kuryelog.logic.orders.shadow

import com.kuryelog.logic.capture.CaptureRows
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.capture.SahaFrames
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.orders.CourierRecords
import com.kuryelog.logic.orders.OrderFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A3.9 "bitti" ölçütü — saha karelerinde fark 0 (WEEM_ISLER A3.9; ORDERS.md 14.3, CAPTURE.md 6.5). 11–12 Ekim'in 6.481 temizlenmiş
 * saha karesi iki yoldan geçer:
 * - **Rawstats:** `shadow/saha_rawstats_2026-10-11_12.zip` — Rawstats @ 6388c04'ün kendi saf sınıflarıyla (`CourierParser`,
 *   `DeliveryCore`, `PlatformHistory`, `PlatformTotals`, `CashPending`, `BoostLog`, `OnlineSessions`, `RecordJson`) `OrderTracker.onScreen`
 *   sırasıyla üretildi (`tools/shadow-fixture`; S42'de silindi, git'te `888423c`); `screens.jsonl` kareler + Rawstats'ın yazdığı aşama (`saha_rawstats_asamalar`).
 * - **WEEM:** aynı kareler `OrderFlow`'dan (gün başına yeni süreç, vardiya ilk karede başlar, son kareden 1 ms sonra biter) ve
 *   `CaptureRows.screen`'den geçer, gün klasörleri ZIP'lenir.
 * Karşılaştırıcı iki ZIP'te sert fark 0 bulmalı; beklenen farklar yalnız ekonomi dosyası (A4.1'e kadar yalnız Rawstats).
 * Gün özetleri saha beklenen değerleriyle (`saha_beklenen_2026-10-11_12.json`, `SahaTekrarTest` D3/D4) aynı olmalı.
 */
class ShadowSahaTest {
    companion object {
        @get:ClassRule @JvmStatic val tmp = TemporaryFolder()
        private val baku: ZoneId = ZoneId.of("Asia/Baku")
        lateinit var rawstats: ShadowExport
        lateinit var weem: ShadowExport
        lateinit var report: ShadowReport

        private class FakeClock(var now: Long) : Clock {
            override fun wallMs() = now
            override fun monoMs() = now
            override fun zone(): ZoneId = baku
        }

        private class Env(root: File, val clockImpl: FakeClock) : OrderFlow.Env {
            override val clock: Clock get() = clockImpl
            override val log = LogStore(root, now = { clockImpl.now }, zone = { baku })
            override val prefs = KuryelogPrefs.Memory().also {
                it.put(mapOf(KuryelogPrefs.MANUAL_POLICY to 1, KuryelogPrefs.SHIFT to true, KuryelogPrefs.MANUAL_SESSION to true, OrderFlow.MIGRATION_07 to true))
            }
            override val db: CourierRecords = CourierRecords.Memory()
            override fun phoneBattery() = 77
        }

        private fun resource(name: String) = ShadowSahaTest::class.java.classLoader!!.getResourceAsStream(name) ?: error("$name yok")

        /** WEEM yolu: kareler `OrderFlow`'a ve `screens.jsonl`'e (WEEM'in `:capture` satırı) gider; gün klasörleri ZIP olur. */
        private fun weemZip(): ByteArray {
            val root = tmp.newFolder("weem")
            for ((day, fs) in SahaFrames.frames.groupBy { it.day }) {
                val first = fs.first().at; val last = fs.maxOf { it.at }
                val env = Env(root, FakeClock(first))
                val flow = OrderFlow(env).also { it.start() }
                flow.cashPendingHooks.onNewShift(first, "manual")
                for (f in fs) {
                    env.clockImpl.now = f.at
                    val o = SahaFrames.parse(f)
                    env.log.append("screens.jsonl", CaptureRows.screen(env.log, f.at, f.pkg, 0, f.bounds, f.src, o.stage, f.texts, f.actions, null, 77, false).toString())
                    flow.onScreen(f.pkg, f.texts, f.actions, f.at, f.src, parsed = o)
                }
                env.clockImpl.now = last + 1
                flow.cashPendingHooks.onEnd(first, last + 1, "manual")
                flow.stop()
                assertTrue(day, File(root, day).isDirectory)
            }
            return ByteArrayOutputStream().also { bytes ->
                ZipOutputStream(bytes).use { z ->
                    root.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { f ->
                        z.putNextEntry(ZipEntry(f.relativeTo(root).invariantSeparatorsPath)); z.write(f.readBytes()); z.closeEntry()
                    }
                }
            }.toByteArray()
        }

        /** Rawstats yolu: üretilmiş gün klasörleri + `screens.jsonl` (kare + Rawstats'ın aşaması; `WatchService.kt:161-166` alanları). */
        private fun rawstatsFiles(): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            ZipInputStream(resource("shadow/saha_rawstats_2026-10-11_12.zip")).use { z ->
                while (true) { val e = z.nextEntry ?: break; if (!e.isDirectory) out[e.name] = String(z.readBytes(), Charsets.UTF_8) }
            }
            val stages = GZIPInputStream(resource("shadow/saha_rawstats_asamalar_2026-10-11_12.txt.gz")).bufferedReader().readLines().filter { it.isNotBlank() }
            assertEquals(SahaFrames.frames.size, stages.size)
            val raw = GZIPInputStream(resource("saha/saha_kareleri_2026-10-11_12.jsonl.gz")).bufferedReader().readLines().filter { it.isNotBlank() }.map(::JSONObject)
            for ((i, f) in raw.withIndex()) {
                val stage = stages[i]
                val row = JSONObject().put("t", f.getString("t")).put("pkg", f.getString("pkg")).put("window", 0).put("bounds", f.optString("bounds"))
                    .put("source", f.optString("src")).put("offerLike", stage == ScreenStage.OFFER.name).put("stage", stage)
                    .put("texts", f.optJSONArray("texts") ?: JSONArray()).put("actions", f.optJSONArray("actions") ?: JSONArray()).put("phone_battery_pct", 77)
                out.merge("${f.getString("gun")}/screens.jsonl", row.toString() + "\n", String::plus)
            }
            return out
        }

        @BeforeClass @JvmStatic fun build() {
            rawstats = ShadowExport("rawstats-saha", rawstatsFiles())
            weem = ShadowExport.zip("weem-saha.zip", weemZip().inputStream())
            report = ShadowCompare(rawstats, weem, baku).run()
            println(report.text(3))
        }
    }

    @Test fun sahaKarelerindeSertFarkYok() {
        val hard = report.diffs.filter { it.level == Level.HARD }
        assertEquals(hard.take(10).joinToString("\n"), 0, report.hard)
        assertEquals(listOf("2026-10-06", "2026-10-08", "2026-10-11", "2026-10-12"), report.days.map { it.day })
    }

    /** Aynı kareler, aynı saat: süre ve sıklık farkı da yok (gerçek gölge modda yumuşak farklar beklenir). */
    @Test fun sahaKarelerindeYumusakFarkYok() {
        assertEquals(report.diffs.filter { it.level == Level.SOFT }.take(10).joinToString("\n"), 0, report.soft)
    }

    /** Beklenen farklar yalnız A4.1'e kadar yazılmayan ekonomi dosyası (Rawstats `economics-exposure.jsonl`). */
    @Test fun beklenenFarkYalnizEkonomiDosyasi() {
        val expected = report.diffs.filter { it.level == Level.EXPECTED }
        assertEquals(setOf("file.not_yet_written"), expected.map { it.rule }.toSet())
        assertEquals(setOf("economics-exposure.jsonl"), expected.map { it.file }.toSet())
        assertTrue(expected.all { it.key == "yalnız Rawstats" })
        assertTrue(report.expected > 1000)
    }

    /** Ayrıştırıcı eşitliği (CAPTURE.md 6.5): 6.481 karenin hepsinde WEEM `CourierParser` aşaması Rawstats'ın yazdığıyla aynı. */
    @Test fun ayristiriciRawstatsIleAyni() {
        val p = report.days.map { it.parser }
        assertEquals(SahaFrames.frames.size, p.sumOf { it.rows })
        assertEquals(SahaFrames.frames.size, p.sumOf { it.checked })
        assertEquals(SahaFrames.frames.size, p.sumOf { it.same })
    }

    /**
     * Gün özetleri saha beklenen değerleriyle (D3/D4: 11 Ekim Wolt 9 + Bolt 1 teslim, 12 Ekim 12 görev = 13 sipariş, 2 iptal teslim
     * sayılmaz; D1/D2: teklif sayıları ayrık Wolt teklifleri 11 ve 17) ve iki tarafta aynı; her sipariş ve teklif eşlendi.
     */
    @Test fun gunOzetleriSahaBeklenenleriyle() {
        val d11 = report.days.single { it.day == "2026-10-11" }; val d12 = report.days.single { it.day == "2026-10-12" }
        for (d in listOf(d11, d12)) {
            assertEquals(d.day, d.rawstats, d.weem.copy(screens = d.rawstats.screens))
            assertEquals(d.day, d.rawstats.screens, d.weem.screens)
            assertEquals(d.day, d.rawstats.trips, d.matchedTrips)
            assertEquals(d.day, d.rawstats.offers, d.matchedOffers)
            assertEquals(d.day, 0, d.rawstats.active)
        }
        assertEquals(10, d11.weem.trips); assertEquals(10, d11.weem.orders); assertEquals(10, d11.weem.delivered + d11.weem.inferred)
        assertEquals(14, d12.weem.trips); assertEquals(13, d12.weem.orders); assertEquals(12, d12.weem.delivered + d12.weem.inferred)
        val expected = SahaFrames.expected.getJSONObject("kabul_olcutleri")
        assertTrue(expected.getString("D3"), expected.getString("D3").isNotEmpty())
    }

    /** Makine okunur rapor (S42: komut satırı aracı kalktı, rapor yalnız testte): sert fark 0, beklenen farklar metin raporuyla aynı. */
    @Test fun raporJsonuSertFarkSifir() {
        val j = report.json()
        assertEquals(ShadowReport.SCHEMA, j.getString("schema"))
        assertEquals(0, j.getJSONObject("summary").getInt("hard"))
        assertEquals(report.expected, j.getJSONObject("summary").getInt("expected"))
    }
}
