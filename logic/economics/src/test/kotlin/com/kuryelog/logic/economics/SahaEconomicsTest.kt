package com.kuryelog.logic.economics

import com.kuryelog.logic.capture.CourierParser
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.core.WeatherPort
import com.kuryelog.logic.orders.CourierRecords
import com.kuryelog.logic.orders.OrderFlow
import com.kuryelog.logic.orders.OrderObserver
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * A4.1 uçtan uca (ORDERS.md 16 risk 5: `:economics`'in kanca sırası Rawstats'la birebir): 11–12 Ekim'in 6.481 saha karesi iki yoldan geçer.
 * - **Rawstats:** `shadow/saha_rawstats_ekonomi_2026-10-11_12.zip` — Rawstats @ 6388c04'ün kendi `EconomicsRecorder`, `WaitObservation`,
 *   `ShadowMode`, `OfferScore`, `EnergySegments` sınıfları `OrderTracker` sırasıyla (`tools/shadow-fixture`, `--economics`).
 * - **WEEM:** aynı kareler `OrderFlow` + [EconomicsObserver]'dan (gün başına yeni süreç; `ShadowSahaTest` ile aynı düzen).
 * Ekonomi dosyalarının her satırı (JSON, anahtar sırası hariç) aynı olmalı. Konum, scooter, hava, komisyon ayarı yok (fikstürde de yok).
 */
class SahaEconomicsTest {
    companion object {
        @get:ClassRule @JvmStatic val tmp = TemporaryFolder()
        private val baku: ZoneId = ZoneId.of("Asia/Baku")
        lateinit var rawstats: Map<String, String>
        lateinit var weem: Map<String, String>

        private class FakeClock(var now: Long) : Clock {
            override fun wallMs() = now
            override fun monoMs() = now
            override fun zone(): ZoneId = baku
        }

        private class Env(root: File, val clockImpl: FakeClock, economics: Boolean) : OrderFlow.Env, EconomicsObserver.Env {
            override val clock: Clock get() = clockImpl
            override val log = LogStore(root, now = { clockImpl.now }, zone = { baku })
            override val prefs = KuryelogPrefs.Memory().also {
                it.put(mapOf(KuryelogPrefs.MANUAL_POLICY to 1, KuryelogPrefs.SHIFT to true, KuryelogPrefs.MANUAL_SESSION to true, OrderFlow.MIGRATION_07 to true))
            }
            override val db: CourierRecords = CourierRecords.Memory()
            override fun phoneBattery() = 77
            override val scooter: ScooterBridge get() = ScooterBridge.NONE
            override val weather: WeatherPort get() = WeatherPort.NONE
            /** Fikstürle aynı: kare anı × 10⁶ ns. */
            override fun monoNs() = clockImpl.now * 1_000_000L
            val econ = EconomicsObserver(this)
            override val observer: OrderObserver = if (economics) econ else OrderObserver.NONE
        }

        private class Frame(val day: String, val at: Long, val pkg: String, val texts: List<String>, val actions: List<String>, val src: String)

        private fun frames(): List<Frame> {
            val f = RepoSource.file("logic/capture/src/testFixtures/resources/saha/saha_kareleri_2026-10-11_12.jsonl.gz")
            fun list(a: JSONArray?) = a?.let { List(it.length()) { i -> it.getString(i) } } ?: emptyList()
            return GZIPInputStream(f.inputStream()).bufferedReader().readLines().filter { it.isNotBlank() }.map(::JSONObject).map { o ->
                val at = OffsetDateTime.parse(o.getString("t").replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")).toInstant().toEpochMilli()
                Frame(o.getString("gun"), at, o.getString("pkg"), list(o.optJSONArray("texts")), list(o.optJSONArray("actions")), o.optString("src"))
            }
        }

        /** Gün klasörleri: `<gün>/<dosya>` → metin. */
        fun run(economics: Boolean): Map<String, String> {
            val root = tmp.newFolder()
            for ((_, fs) in frames().groupBy { it.day }) {
                val first = fs.first().at; val last = fs.maxOf { it.at }
                val env = Env(root, FakeClock(first), economics)
                val flow = OrderFlow(env).also { it.start() }
                flow.cashPendingHooks.onNewShift(first, "manual")
                for (f in fs) {
                    env.clockImpl.now = f.at
                    flow.onScreen(f.pkg, f.texts, f.actions, f.at, f.src, parsed = CourierParser.parse(f.pkg, f.texts, f.actions, f.at, f.src))
                }
                env.clockImpl.now = last + 1
                flow.cashPendingHooks.onEnd(first, last + 1, "manual")
                flow.stop()
            }
            return root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
        }

        @BeforeClass @JvmStatic fun build() {
            rawstats = LinkedHashMap<String, String>().also { out ->
                ZipInputStream(SahaEconomicsTest::class.java.classLoader!!.getResourceAsStream("shadow/saha_rawstats_ekonomi_2026-10-11_12.zip")!!).use { z ->
                    while (true) { val e = z.nextEntry ?: break; if (!e.isDirectory) out[e.name] = String(z.readBytes(), Charsets.UTF_8) }
                }
            }
            weem = run(economics = true)
        }

        /** Anahtar sırasından bağımsız biçim (org.json anahtar sırasını korumaz). */
        fun canon(v: Any?): String = when (v) {
            is JSONObject -> v.keySet().sorted().joinToString(",", "{", "}") { k -> JSONObject.quote(k) + ":" + canon(v.opt(k)) }
            is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.opt(it)) }
            null, JSONObject.NULL -> "null"
            is String -> JSONObject.quote(v)
            else -> v.toString()
        }
        private fun lines(s: String?) = s.orEmpty().lines().filter { it.isNotBlank() }.map { canon(JSONObject(it)) }

        /** Fikstürdeki ekonomi kaydedicisi dosyaları (platform/nakit/boost'lar `:orders` `ShadowSahaTest`'te). */
        private val OWN = Regex("""economics-(offers|legs|idle|penalties|waits|exposure|energy|offer-scores|offer-ratings)\.jsonl""")
    }

    @Test fun everyEconomicsRowIsTheSameAsRawstats() {
        assertTrue(rawstats.keys.toString(), rawstats.size >= 16)
        for ((name, text) in rawstats) {
            val want = lines(text); val got = lines(weem[name])
            assertEquals("$name satır sayısı", want.size, got.size)
            for (i in want.indices) assertEquals("$name satır ${i + 1}", want[i], got[i])
        }
    }

    @Test fun weemWritesNoOtherEconomicsFile() {
        val own = weem.keys.filter { OWN.matches(it.substringAfterLast('/')) }.toSet()
        assertEquals(rawstats.keys, own)
    }

    /** Puanlar gerçekten hesaplandı (gölge değerlendirme çalıştı): hepsi boş olsaydı karşılaştırma bir şey kanıtlamazdı. */
    @Test fun scoresAndVerdictsWereComputed() {
        val scores = rawstats.filterKeys { it.endsWith(OfferScore.FILE) }.values.flatMap { lines(it) }.map(::JSONObject)
        assertTrue(scores.size >= 30)
        assertTrue(scores.count { !it.isNull("score") } >= 28)
        val offers = rawstats.filterKeys { it.endsWith(EconomicsLogs.OFFERS) }.values.flatMap { lines(it) }.map(::JSONObject)
        assertTrue(offers.all { !it.isNull("verdictShadow") })
    }

    /** `OrderObserver.NONE` (A3.5–A3.7 düzeni): hiçbir ekonomi kaydedicisi dosyası yazılmaz. */
    @Test fun noneWritesNoEconomicsFile() {
        assertEquals(emptyList<String>(), run(economics = false).keys.filter { OWN.matches(it.substringAfterLast('/')) })
    }
}
