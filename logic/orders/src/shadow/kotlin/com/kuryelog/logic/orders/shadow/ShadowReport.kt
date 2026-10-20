package com.kuryelog.logic.orders.shadow

import org.json.JSONArray
import org.json.JSONObject

/** Fark düzeyi (ORDERS.md 14.3): sert (sıfır olmalı), yumuşak (süre/sıklık; raporlanır), beklenen (bilerek fark; sayılmaz). */
enum class Level(val tr: String) { HARD("sert"), SOFT("yumuşak"), EXPECTED("beklenen") }

/**
 * Bir fark. [rule] makine okunur kural adı (ör. `order.gross`, `parser.stage`, `file.economics`), [key] neyin farkı olduğu
 * (sipariş, teklif, satır anahtarı), [rawstats]/[weem] iki taraftaki değer (yoksa null), [deltaMs] zaman farkı (WEEM − Rawstats).
 */
data class Diff(val level: Level, val day: String, val file: String, val rule: String, val key: String,
                val rawstats: String? = null, val weem: String? = null, val deltaMs: Long? = null, val count: Int = 1) {
    fun json(): JSONObject = JSONObject().put("level", level.name.lowercase()).put("day", day).put("file", file).put("rule", rule).put("key", key)
        .put("rawstats", rawstats).put("weem", weem).put("delta_ms", deltaMs).also { if (count != 1) it.put("count", count) }
}

/** Bir tarafın gün özeti (`OrderHistory.Day` tanımlarıyla). */
data class Side(val trips: Int, val orders: Int, val delivered: Int, val inferred: Int, val unconfirmed: Int, val active: Int,
                val gross: Double, val net: Double, val offers: Int, val screens: Int,
                /** [net] içindeki kullanıcı cevabı bahşişler (A3.11, [ShadowRules.answeredTip]); JSON'a yazılmaz. */
                val answeredTips: Double = 0.0) {
    fun json(): JSONObject = JSONObject().put("trips", trips).put("orders", orders).put("delivered", delivered).put("delivered_inferred", inferred)
        .put("unconfirmed", unconfirmed).put("active", active).put("gross", gross).put("net", net).put("offers", offers).put("screens", screens)
}

/** Ayrıştırıcı eşitliği (CAPTURE.md 6.5): Rawstats `screens.jsonl` satırlarından kaçı WEEM `CourierParser`'dan aynı çıktı. */
data class ParserStats(val rows: Int, val checked: Int, val same: Int) {
    fun json(): JSONObject = JSONObject().put("rows", rows).put("checked", checked).put("same", same)
}

data class DayReport(val day: String, val rawstats: Side, val weem: Side, val parser: ParserStats,
                     val matchedTrips: Int, val matchedOffers: Int, val flaggedMinutes: List<String>)

class ShadowReport(val rawstatsName: String, val weemName: String, val days: List<DayReport>, val diffs: List<Diff>) {
    fun count(level: Level) = diffs.filter { it.level == level }.sumOf { it.count }
    val hard get() = count(Level.HARD)
    val soft get() = count(Level.SOFT)
    val expected get() = count(Level.EXPECTED)

    fun json(): JSONObject = JSONObject().put("schema", SCHEMA).put("rawstats", rawstatsName).put("weem", weemName)
        .put("summary", JSONObject().put("hard", hard).put("soft", soft).put("expected", expected).put("days", days.size))
        .put("days", JSONArray(days.map { d ->
            JSONObject().put("day", d.day).put("rawstats", d.rawstats.json()).put("weem", d.weem.json()).put("parser", d.parser.json())
                .put("matched_trips", d.matchedTrips).put("matched_offers", d.matchedOffers)
                .put("screenshot_unavailable_minutes", JSONArray(d.flaggedMinutes))
                .put("hard", diffs.filter { it.day == d.day && it.level == Level.HARD }.sumOf { it.count })
                .put("soft", diffs.filter { it.day == d.day && it.level == Level.SOFT }.sumOf { it.count })
                .put("expected", diffs.filter { it.day == d.day && it.level == Level.EXPECTED }.sumOf { it.count })
        }))
        .put("diffs", JSONArray(diffs.map { it.json() }))

    /** İnsan için metin: özet, gün tabloları, düzey ve kural başına ilk [examples] örnek. */
    fun text(examples: Int = 5): String = buildString {
        appendLine("WEEM gölge mod raporu")
        appendLine("Rawstats: $rawstatsName · WEEM: $weemName")
        appendLine("Sonuç: sert $hard · yumuşak $soft · beklenen $expected" + if (hard == 0) " — sert fark yok" else " — SERT FARK VAR")
        for (d in days) {
            appendLine()
            val dayHard = diffs.filter { it.day == d.day && it.level == Level.HARD }.sumOf { it.count }
            if (listOf(d.rawstats, d.weem).all { it.trips == 0 && it.offers == 0 && it.screens == 0 }) {
                appendLine("== ${d.day} (sert $dayHard): sipariş, teklif ve ekran yok (yalnız platform/ekonomi satırları)"); continue
            }
            appendLine("== ${d.day} (sert $dayHard)")
            appendLine("   sipariş     Rawstats ${line(d.rawstats)}")
            appendLine("               WEEM     ${line(d.weem)}")
            appendLine("   eşlenen: ${d.matchedTrips} sipariş, ${d.matchedOffers} teklif · ayrıştırıcı: ${d.parser.same}/${d.parser.checked} aynı" +
                (if (d.parser.rows > d.parser.checked) " (${d.parser.rows - d.parser.checked} satırda aşama yok)" else ""))
            if (d.flaggedMinutes.isNotEmpty()) appendLine("   ekran görüntüsü alınamayan dakikalar: ${d.flaggedMinutes.joinToString(" ")}")
        }
        for (level in Level.values()) {
            val of = diffs.filter { it.level == level }
            if (of.isEmpty()) continue
            appendLine()
            appendLine("-- ${level.tr} farklar (${of.sumOf { it.count }})")
            for ((rule, rs) in of.groupBy { it.rule }) {
                appendLine("   $rule: ${rs.sumOf { it.count }}")
                for (r in rs.take(examples)) appendLine("      ${r.day} ${r.file} ${r.key}" + values(r))
                if (rs.size > examples) appendLine("      … ${rs.size - examples} satır daha (JSON'da)")
            }
        }
    }

    private fun line(s: Side) = "${s.trips} kayıt · ${s.orders} sipariş · teslim ${s.delivered} · çıkarım ${s.inferred} · doğrulanamayan ${s.unconfirmed}" +
        " · açık ${s.active} · brüt ${"%.2f".format(java.util.Locale.US, s.gross)} · net ${"%.2f".format(java.util.Locale.US, s.net)} · teklif ${s.offers} · ekran ${s.screens}"
    private fun values(d: Diff) = buildString {
        if (d.rawstats != null || d.weem != null) append(": Rawstats=${short(d.rawstats)} WEEM=${short(d.weem)}")
        d.deltaMs?.let { append(" Δ${it / 1000.0} sn") }
        if (d.count != 1) append(" ×${d.count}")
    }
    private fun short(s: String?) = s?.let { if (it.length > 120) it.take(117) + "…" else it } ?: "-"

    companion object { const val SCHEMA = "weem-shadow-report-v1" }
}
