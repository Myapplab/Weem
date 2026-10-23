package com.kuryelog.logic.map.day

import org.json.JSONObject

/**
 * 2 telefonda Receiver'ın günü Provider'da (A10.4i; MAP.md 11.17, SORU-11-2 a, S35 #17): gün motoru yalnız Receiver'da (konum ve harita
 * orada; çift km ve çift ışık sayımı yok). Receiver 60 sn'de bir ve gün değişince [encode] ile kısaltılmış gün durumunu yollar
 * (`DaySnapshot`, son durum), gün raporu dosyalarını [report] ile (güvenilir). Provider'ın gün kartı, İstatistik'i, görevleri ve
 * hatırlatıcıları bundan beslenir.
 *
 * Kısaltma: duruşlar (ışıklar dahil) ve bütün koordinatlar çıkar; siparişlerin saatleri, mesafesi ve restoran adı kalır. Yük Bluetooth
 * yedeğine sığsın diye en çok [MAX_CHARS]; aşarsa en eski biten siparişler atılır. Tam gün raporu gün sonunda ayrıca gelir.
 */
object DaySnapshot {
    const val SCHEMA = 1
    /** Bluetooth yedeğinin 4 KiB'lık yükünden zarf payı düşülmüş üst sınır. */
    const val MAX_CHARS = 3_200
    /** Rapor iletisi Wi-Fi'da en çok bu kadar (şifreli ileti 64 KiB). */
    const val MAX_REPORT_CHARS = 60_000

    private val COORDS = listOf("restLat", "restLon", "custLat", "custLon", "restTarget", "customerTarget")

    private fun order(o: OrderRec): JSONObject = o.toJson().also { j -> COORDS.forEach(j::remove) }

    fun encode(view: DayView, sentAtMs: Long): String {
        val s = view.state
        val orders = s.orders.map(::order).toMutableList()
        fun build(): String {
            val j = s.toJson()
            j.remove("stops")
            j.put("orders", org.json.JSONArray(orders))
            j.put("queuedOrders", org.json.JSONArray(s.queuedOrders.map(::order)))
            s.current?.let { j.put("current", order(it)) }
            return JSONObject().put("schemaVersion", SCHEMA).put("sentAtMs", sentAtMs).put("state", j)
                .apply { view.lightSinceMs?.let { put("lightSinceMs", it) } }.toString()
        }
        var text = build()
        while (text.length > MAX_CHARS && orders.isNotEmpty()) {
            orders.removeAt(0)
            text = build()
        }
        return text
    }

    /** Gelen kısaltılmış gün; bozuk ya da başka sürüm null. */
    fun decode(text: String?): DayView? = runCatching {
        val j = JSONObject(text ?: return null)
        if (j.optInt("schemaVersion") != SCHEMA) return null
        val state = DayState.fromJson(j.getJSONObject("state"))
        DayView(state, if (j.has("lightSinceMs")) j.getLong("lightSinceMs") else null)
    }.getOrNull()

    /** Gün raporu iletisi: dosya adı + girintisiz rapor; sığmazsa null (gönderilmez). */
    fun report(name: String, text: String): String? {
        if (!reportName(name)) return null
        val body = runCatching { JSONObject(text).toString() }.getOrNull() ?: return null
        return JSONObject().put("schemaVersion", SCHEMA).put("name", name).put("report", body).toString().takeIf { it.length <= MAX_REPORT_CHARS }
    }

    /** Gelen rapor iletisi → (dosya adı, rapor metni); ad gün raporu adı değilse ya da rapor okunamıyorsa null. */
    fun readReport(text: String?): Pair<String, String>? = runCatching {
        val j = JSONObject(text ?: return null)
        if (j.optInt("schemaVersion") != SCHEMA) return null
        val name = j.getString("name").takeIf(::reportName) ?: return null
        val body = j.getString("report")
        DayState.fromJson(JSONObject(body)).startedAtMs ?: return null
        name to body
    }.getOrNull()

    /** `<gün>_<HHmm>.gun.json` (`DayReportFile.name`); yol ayırıcısı ya da başka tür kabul edilmez. */
    fun reportName(name: String): Boolean = REPORT.matches(name)

    private val REPORT = Regex("\\d{4}-\\d{2}-\\d{2}_\\d{4}\\.gun\\.json")

}
