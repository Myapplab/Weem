package com.kuryelog.logic.capture

import org.json.JSONObject
import java.time.OffsetDateTime
import java.util.zip.GZIPInputStream

/** Saha karesi: 11–12 Ekim'in gerçek ekran kareleri (Wolt 2.202.6, Bolt), kişisel veriden temizlenmiş. */
data class SahaFrame(val day: String, val at: Long, val pkg: String, val bounds: String, val src: String,
                     val texts: List<String>, val actions: List<String>) {
    val joined by lazy { texts.joinToString(" | ") }
    val wolt get() = pkg == SahaFrames.WOLT
}

/**
 * Saha kareleri yükleyicisi ve ortak yardımcılar (Rawstats `SahaTekrarTest` companion'ı; CAPTURE.md 8). `logic/capture` ve
 * `logic/orders` testleri aynı kopyayı okur (`testFixtures/resources/saha/`, Rawstats'ın dosyalarının bayt bayt kopyası).
 */
object SahaFrames {
    const val WOLT = "com.wolt.courierapp"
    val frames: List<SahaFrame> by lazy {
        val s = SahaFrames::class.java.classLoader!!.getResourceAsStream("saha/saha_kareleri_2026-10-11_12.jsonl.gz")
            ?: error("saha kareleri bulunamadı")
        GZIPInputStream(s).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }.map { l ->
                val o = JSONObject(l)
                fun list(k: String) = o.optJSONArray(k)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
                SahaFrame(o.getString("gun"), OffsetDateTime.parse(o.getString("t").replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2"))
                    .toInstant().toEpochMilli(), o.getString("pkg"), o.optString("bounds"), o.optString("src"), list("texts"), list("actions"))
            }.toList()
        }
    }
    val expected: JSONObject by lazy {
        val s = SahaFrames::class.java.classLoader!!.getResourceAsStream("saha/saha_beklenen_2026-10-11_12.json")!!
        JSONObject(s.bufferedReader(Charsets.UTF_8).readText())
    }
    private val idleText = Regex("delivery demand|quieter than usual|expected demand", RegexOption.IGNORE_CASE)
    fun parse(f: SahaFrame) = CourierParser.parse(f.pkg, f.texts, f.actions, f.at, f.src)
    fun isIdleFrame(f: SahaFrame) = f.wolt && idleText.containsMatchIn(f.joined)
    fun isSplitFrame(f: SahaFrame) = f.wolt && f.bounds == "0 0 1280 1413"
    /** Kartta kazanç: OCR sırasında "Expected earnings" satırının bir önceki satırındaki AZN (saha analizi §2.1). */
    fun cardEarning(f: SahaFrame): Double? {
        val i = f.texts.indexOfFirst { it.contains("Expected earn") }; if (i <= 0) return null
        val m = Regex("AZN\\s?(\\d+)[.,:](\\d{2})").find(f.texts[i - 1]) ?: return null
        return "${m.groupValues[1]}.${m.groupValues[2]}".toDouble()
    }
    fun changeAmount(f: SahaFrame): Double? {
        val i = f.texts.indexOfFirst { it.contains("Total change needed") }; if (i < 0) return null
        return (maxOf(0, i - 1)..minOf(f.texts.lastIndex, i + 1)).firstNotNullOfOrNull { j ->
            Regex("AZN\\s?(\\d+)[.,:](\\d{2})").find(f.texts[j])?.let { "${it.groupValues[1]}.${it.groupValues[2]}".toDouble() }
        }
    }
}
