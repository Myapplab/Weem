package com.kuryelog.logic.orders

// Rawstats AnalysisExport.kt:1-426 @ 6388c04, satır satır (R18; CORE.md 5.3). WEEM: panel satırı tanıma `logic/capture` `OwnLines`
// (Rawstats `OverlayText.isOwn/withoutOwn`; tek kaynak, ORDERS.md 6.3), `Redactor` `logic/core`'da. Python ikizi tools/analysis/anonymize.py.

import com.kuryelog.logic.capture.CourierParser
import com.kuryelog.logic.capture.OfferRules
import com.kuryelog.logic.capture.OwnLines
import com.kuryelog.logic.core.Redactor

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Anonymous analysis export (SPEC §18.1, 2.1.0 note), next to the unchanged full export.
 * Each log file has an allowlist: anything not listed is dropped. Every `lat`/`lon` key at any
 * depth is rounded to 3 decimals, every `loc` object keeps only `{lat, lon, acc, age_s}`, free
 * text goes through [Redactor] and order/customer ids become keyed pseudonyms.
 *
 * One instance is one export: the secret is random (32 bytes from [SecureRandom]) and never
 * stored, so pseudonyms are consistent inside one export only. Not thread-safe.
 */
class AnalysisExport(secret: ByteArray = newSecret()) {
    private val r = Redactor(secret)

    /** How an export entry is treated; null from [kindOf] means the entry is left out. */
    enum class Kind { SCREENS, EVENTS, NOTIFS, ORDERS, ERRORS, KEEP_ALL, GPS, RESTAURANTS }

    /** Redacted content of one entry ([path] as in the full export: `YYYY-MM-DD/<file>` or `restaurants.json`), or null when it is excluded. */
    fun text(path: String, content: String): String? {
        val kind = kindOf(path) ?: return null
        if (kind == Kind.RESTAURANTS) return restaurants(content)?.first
        val sb = StringBuilder()
        content.reader().buffered().use { redact(kind, it, sb) }
        return sb.toString()
    }

    /** In-memory export: path → redacted content, excluded entries left out, [MARKER] added last. */
    fun texts(entries: Map<String, String>, versionName: String, createdAtMs: Long): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        val counts = LinkedHashMap<String, Int>()
        for ((path, content) in entries) {
            val kind = kindOf(path) ?: continue
            if (kind == Kind.RESTAURANTS) {
                val (text, n) = restaurants(content) ?: continue
                out[path] = text; counts[path] = n
                continue
            }
            val sb = StringBuilder()
            counts[path] = content.reader().buffered().use { redact(kind, it, sb) }
            out[path] = sb.toString()
        }
        out[MARKER] = marker(versionName, createdAtMs, counts).toString()
        return out
    }

    /**
     * Streams the export as a ZIP into [out] (closed afterwards): the included entries in the given
     * order (`path` → source file), then [MARKER] at the root. Returns the marker.
     */
    fun zip(entries: List<Pair<String, File>>, out: OutputStream, versionName: String, createdAtMs: Long): JSONObject {
        val counts = LinkedHashMap<String, Int>()
        return ZipOutputStream(out).use { z ->
            val w = OutputStreamWriter(z, Charsets.UTF_8)
            for ((path, file) in entries) {
                val kind = kindOf(path)?.takeIf { path !in counts } ?: continue // a ZIP entry name may occur once
                if (kind == Kind.RESTAURANTS) {
                    val (text, n) = restaurants(file.readText(Charsets.UTF_8)) ?: continue
                    z.putNextEntry(ZipEntry(path)); w.write(text); w.flush(); z.closeEntry(); counts[path] = n
                    continue
                }
                z.putNextEntry(ZipEntry(path))
                counts[path] = file.bufferedReader(Charsets.UTF_8).use { redact(kind, it, w) }
                w.flush(); z.closeEntry()
            }
            val m = marker(versionName, createdAtMs, counts)
            z.putNextEntry(ZipEntry(MARKER)); w.write(m.toString()); w.flush(); z.closeEntry()
            m
        }
    }

    /** Copies the redacted rows of one line-based file; returns the number of rows written. Unreadable rows are dropped. */
    fun redact(kind: Kind, input: BufferedReader, out: Appendable): Int {
        if (kind == Kind.GPS) return gps(input, out)
        var n = 0
        while (true) {
            val line = input.readLine() ?: break
            if (line.isBlank()) continue
            val row = runCatching { row(kind, JSONObject(line)) }.getOrNull() ?: continue
            out.append(row.toString()).append('\n'); n++
        }
        return n
    }

    /** One JSON row of a file of [kind], or null when such rows are not exported. */
    fun row(kind: Kind, j: JSONObject): JSONObject? = when (kind) {
        Kind.SCREENS -> screen(j)
        Kind.EVENTS -> event(j)
        Kind.NOTIFS -> notif(j)
        Kind.ORDERS -> order(j)
        Kind.ERRORS -> error(j)
        Kind.KEEP_ALL -> j
        Kind.GPS, Kind.RESTAURANTS -> null
    }?.let { geo(it) as JSONObject }

    /**
     * `screens.jsonl`: panel lines removed before and after [Redactor.lines] (a panel line between a label and its value would
     * shift the label modes). `actions` repeat raw `texts` entries: WatchService.walk adds every clickable node with its whole
     * subtree, so a clickable drop-off card puts the customer's name, address and note there. Each action is therefore a known
     * label or action through [Redactor.free], else the exported line of the identical `texts` entry (left out with it when it
     * is a panel line), else [CARD].
     */
    fun screen(j: JSONObject): JSONObject {
        val o = pick(j, SCREEN_KEYS)
        val pkg = j.opt("pkg") as? String ?: ""
        val exported = HashMap<String, String?>() // raw texts entry → its exported line (first occurrence), null when removed as a panel line
        j.optJSONArray("texts")?.let { a ->
            val kept = ArrayList<String>(); val keptRaw = ArrayList<String>()
            for (t in strings(a)) { val c = withoutPanel(listOf(t)).firstOrNull(); if (c == null) exported[t] = null else { kept.add(c); keptRaw.add(t) } }
            val lines = r.lines(pkg, kept).map { withoutPanel(listOf(it)).firstOrNull() }
            keptRaw.forEachIndexed { i, t -> if (t !in exported) exported[t] = lines[i] }
            o.put("texts", JSONArray(lines.filterNotNull()))
        }
        j.optJSONArray("actions")?.let { a -> o.put("actions", JSONArray(strings(a).mapNotNull { action(it, exported) })) }
        return o
    }

    /** One `actions` entry of a screen row (see [screen]); null leaves it out. */
    private fun action(a: String, exported: Map<String, String?>): String? = when {
        isKnownAction(a) -> r.free(a)
        a in exported -> exported[a]
        withoutPanel(listOf(a)).isEmpty() -> null
        else -> CARD
    }

    /**
     * `events.jsonl` (rows of `Store.event`). Two rules are stricter than the WP3 table (`text` → `free()`), because
     * [Redactor.free] masks only phones, door codes and order ids: a `click` text joins up to 25 labels of the tapped node's
     * subtree (a drop-off card click carries the customer's name and address), so it is kept only as [clickText]; a
     * `notification_hint` text joins the notification's title/text/big/sub (a customer chat carries name, message and
     * address), so it becomes `hint` = [notificationHint], the rule of `notifs.jsonl`.
     */
    fun event(j: JSONObject): JSONObject {
        val type = j.opt("type") as? String ?: ""
        val o = pick(j, listOf("t", "type", "loc"))
        when {
            type == "click" -> { pick(j, listOf("pkg"), o); (j.opt("text") as? String)?.let { o.put("text", clickText(it)) } }
            type == "notification_hint" -> { pick(j, listOf("pkg", "observed_at"), o); o.put("hint", notificationHint(j.opt("text") as? String ?: "")) }
            type == "customer_coordinate_unavailable" -> { pick(j, listOf("trip"), o); (j.opt("customer") as? String)?.let { o.put("customer", customerId(it)) } }
            type == "cancellation_user_reported" -> pick(j, listOf("pkg", "platformActionPerformed"), o)
            type.startsWith("shift_") || type.startsWith("gps_") -> {
                for (k in keys(j)) { val v = j.opt(k); if (k !in EVENT_BASE && (v is Number || v is Boolean)) o.put(k, v) }
            }
            else -> pick(j, listOf("pkg"), o)
        }
        return o
    }

    /** `notifs.jsonl`: texts and key dropped; `hint` tells what kind of notification it was. */
    fun notif(j: JSONObject): JSONObject = pick(j, NOTIF_KEYS)
        .put("hint", notificationHint(listOf("title", "text", "big", "sub").mapNotNull { j.opt(it) as? String }.joinToString("\n")))

    /** `orders.jsonl`: event rows and `record_snapshot` rows. */
    fun order(j: JSONObject): JSONObject {
        if (j.opt("type") == "record_snapshot") {
            val o = pick(j, listOf("t", "type"))
            j.optJSONObject("record")?.let { o.put("record", trip(it)) }
            return o
        }
        val o = pick(j, ORDER_KEYS)
        j.optJSONObject("offer")?.let { o.put("offer", offer(it)) }
        j.optJSONObject("record")?.let { o.put("record", trip(it)) }
        return o
    }

    /** `RecordJson.offer`. */
    fun offer(j: JSONObject): JSONObject {
        val o = pick(j, OFFER_KEYS)
        j.optJSONObject("data")?.let { o.put("data", observation(it)) }
        return o
    }

    /** `RecordJson.observation`: `raw`, `actions`, `customer` and `customer_address` dropped; ids keep their shape. */
    fun observation(j: JSONObject): JSONObject {
        val o = pick(j, OBSERVATION_KEYS)
        j.optJSONArray("ids")?.let { o.put("ids", JSONArray(strings(it).map(::orderId))) }
        return o
    }

    /** `RecordJson.trip`: customer details dropped, one pseudonym per customer id. */
    fun trip(j: JSONObject): JSONObject {
        val o = pick(j, TRIP_KEYS)
        j.optJSONObject("offer")?.let { o.put("offer", offer(it)) }
        j.optJSONObject("latest")?.let { o.put("latest", observation(it)) }
        for (k in listOf("current_customer", "deliver_seen_for")) (j.opt(k) as? String)?.let { o.put(k, customerId(it)) }
        j.optJSONArray("pickups")?.let { a ->
            o.put("pickups", JSONArray().also { out -> objects(a).forEach { p ->
                val q = pick(p, PICKUP_KEYS)
                freeText(p, "note", q)
                (p.opt("code") as? String)?.let { q.put("code", orderId(it)) }
                p.optJSONArray("items")?.let { q.put("itemsCount", it.length()) }
                out.put(q)
            } })
        }
        j.optJSONArray("customers")?.let { a ->
            o.put("customers", JSONArray().also { out -> objects(a).forEach { c ->
                val q = pick(c, CUSTOMER_KEYS)
                (c.opt("id") as? String)?.let { q.put("id", customerId(it)) }
                out.put(q)
            } })
        }
        return o
    }

    /** `errors.jsonl`: the message through [Redactor.free], at most 200 characters. */
    fun error(j: JSONObject): JSONObject {
        val o = pick(j, listOf("t", "source"))
        (j.opt("error") as? String)?.let { o.put("error", clip(r.free(it), 200)) }
        return o
    }

    /** Order ids (`#8P6D2`, pickup codes): shape-preserving pseudonym, the same one [Redactor.free] gives inside screen text. */
    fun orderId(id: String): String = if (id.startsWith("#")) "#" + r.shape("id", id.substring(1)) else r.shape("id", id)

    /** Customer keys of a trip: order ids as [orderId]; `name:<customer>` and other keys as a keyed hash. */
    fun customerId(id: String): String = when {
        id.startsWith("#") -> orderId(id)
        id.startsWith("name:") -> "name:" + r.h("customer", id.substring(5), 8)
        else -> "c:" + r.h("customer", id, 8)
    }

    /** A click's text: [Redactor.free] of it when the whole text is a known label or action ([isKnownAction]), else [CARD]. */
    fun clickText(text: String): String = if (isKnownAction(text)) r.free(text) else CARD

    private fun freeText(src: JSONObject, key: String, out: JSONObject) { (src.opt(key) as? String)?.let { out.put(key, r.free(it)) } }

    /** `gps.csv`: header kept, `lat`/`lon` columns rounded. A file without both columns keeps only its header. */
    private fun gps(input: BufferedReader, out: Appendable): Int {
        val header = input.readLine() ?: return 0
        out.append(header).append('\n')
        val cols = header.split(',').map { it.trim() }
        val lat = cols.indexOf("lat"); val lon = cols.indexOf("lon")
        if (lat < 0 || lon < 0) return 0
        var n = 0
        while (true) {
            val line = input.readLine() ?: break
            if (line.isBlank() || line == header) continue
            val f = line.split(',').toMutableList()
            for (i in listOf(lat, lon)) if (i < f.size) f[i] = f[i].trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::plain3) ?: ""
            out.append(f.joinToString(",")).append('\n'); n++
        }
        return n
    }

    /** `restaurants.json` (business data): kept, coordinates rounded; null when it is not a JSON object. */
    private fun restaurants(content: String): Pair<String, Int>? {
        val j = runCatching { JSONObject(content) }.getOrNull() ?: return null
        return (geo(j) as JSONObject).toString() to j.length()
    }

    /** The `ANALIZ_ANONIM.json` marker that WP4's loader checks. */
    fun marker(versionName: String, createdAtMs: Long, files: Map<String, Int>): JSONObject {
        val days = files.keys.map { it.substringBefore('/', "") }.filter { DAY.matches(it) }.distinct().sorted()
        val counts = JSONObject()
        files.forEach { (k, v) -> counts.put(k, v) }
        return JSONObject().put("schema", SCHEMA).put("app", "com.kuryelog.app").put("versionName", versionName).put("createdAtMs", createdAtMs)
            .put("days", JSONArray(days)).put("files", counts)
            .put("redaction", JSONObject().put("locDecimals", LOC_DECIMALS).put("secretStored", false).put("panelLinesRemoved", true))
    }

    companion object {
        const val MARKER = "ANALIZ_ANONIM.json"
        const val SCHEMA = "kuryeplus-analysis-export/1"
        const val LOC_DECIMALS = 3
        /** Stands for a click or screen action whose text is not a known label or action (it may hold customer data). */
        const val CARD = "[card]"

        fun newSecret(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

        private val DAY = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
        private val ECONOMICS = Regex("economics-[a-z0-9_.-]+\\.jsonl")
        private val SCOOTER = Regex("scooter[a-z0-9_-]*\\.jsonl")

        /** How [path] is exported, or null when it is left out (unknown files, other folders). */
        fun kindOf(path: String): Kind? {
            if (path == "restaurants.json") return Kind.RESTAURANTS
            val parts = path.split('/')
            if (parts.size != 2 || !DAY.matches(parts[0])) return null
            return when (val name = parts[1]) {
                "screens.jsonl" -> Kind.SCREENS
                "events.jsonl" -> Kind.EVENTS
                "notifs.jsonl" -> Kind.NOTIFS
                "orders.jsonl" -> Kind.ORDERS
                "errors.jsonl" -> Kind.ERRORS
                "gps.csv" -> Kind.GPS
                "platforms.jsonl", "online-sessions.jsonl" -> Kind.KEEP_ALL
                else -> if (ECONOMICS.matches(name) || SCOOTER.matches(name)) Kind.KEEP_ALL else null
            }
        }

        private val SCREEN_KEYS = listOf("t", "pkg", "window", "bounds", "source", "offerLike", "stage", "phone_battery_pct", "loc")
        private val EVENT_BASE = setOf("t", "type", "loc")
        private val NOTIF_KEYS = listOf("t", "posted", "ev", "pkg", "ongoing", "channel", "loc")
        private val ORDER_KEYS = listOf("t", "type", "pkg", "offer_id", "id", "reason", "session_id", "gross", "net", "total_min", "wait_min", "km", "restaurant",
            "phone_battery_pct", "loc")
        private val OFFER_KEYS = listOf("id", "pkg", "day", "at", "battery_pct", "lat", "lon", "gps_accuracy_m", "gps_age_ms", "scooter_battery_pct",
            "scooter_observed_at", "scooter_source", "session_id", "last_seen_at", "decline_click_at", "boost_value", "boost_unit", "boost_source", "boost_seen_at",
            "weather_source", "weather_fetched_at", "weather_temp_c", "weather_feels_c", "weather_rain_mm", "weather_rain_pct", "weather_wind_kmh", "weather_gust_kmh", "weather_code",
            "fee_at", "fee_gap_ms", "fee_ocr_ms", "notified_at")
        private val OBSERVATION_KEYS = listOf("pkg", "at", "stage", "complete", "source", "gross", "km", "min_lo", "min_hi", "stops", "quantity", "restaurant",
            "restaurant_address", "ready_in", "pickup_clock", "dropoff_clock", "travel_eta", "arrived", "ready", "delivered", "deliver_control")
        private val TRIP_KEYS = listOf("id", "pkg", "day", "accepted_at", "phase", "completed_at", "expected_orders", "distance_m", "used_wh", "recovered_wh",
            "energy_covered_ms", "energy_shared", "tip", "commission_pct", "price_is_net", "missed_offer", "pickup_intent_at", "finish_intent_at", "deliver_seen_at", "session_id",
            "delivered_inferred_at", "delivered_inferred_by", "delivered_inferred_reason", "deliver_spinner_at",
            "payment_method", "payment_source", "cash_amount_azn", "payment_at", "tip_source", "cash_no_change", "cash_change_azn", "cash_checked_at",
            "verified_by", "verified_at", "cash_collected_at", "cash_collected_azn", "extra_wait_azn", "platform_tip_azn", "energy_partial_ms")
        private val PICKUP_KEYS = listOf("restaurant", "address", "ready_at", "arrived_at", "picked_at", "arrival_source", "ready_observed_at", "ready_hint_at",
            "preparing_at", "reminder_at")
        private val CUSTOMER_KEYS = listOf("arrived_at", "delivered_at", "coordinate_source", "reminder_at", "details_at", "deadline_clock", "deadline_at", "lat", "lon", "arrival_source")
        private val LOC_KEYS = listOf("lat", "lon", "acc", "age_s")

        /** Copies the listed keys: scalars as they are, `loc` objects (normalised later by [geo]); nested values of other keys are never copied. */
        private fun pick(src: JSONObject, keys: List<String>, out: JSONObject = JSONObject()): JSONObject {
            for (k in keys) {
                if (!src.has(k)) continue
                val v = src.opt(k)
                if (v is Number || v is Boolean || v is String || v == JSONObject.NULL || (k == "loc" && v is JSONObject)) out.put(k, v)
            }
            return out
        }

        private fun keys(j: JSONObject): List<String> { val it = j.keys(); val out = ArrayList<String>(); while (it.hasNext()) out.add(it.next()); return out }
        private fun strings(a: JSONArray): List<String> = (0 until a.length()).mapNotNull { i -> a.opt(i)?.takeIf { it != JSONObject.NULL }?.toString() }
        private fun objects(a: JSONArray): List<JSONObject> = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        private fun clip(s: String, max: Int): String = if (s.length <= max) s else s.substring(0, if (Character.isHighSurrogate(s[max - 1])) max - 1 else max)

        /** Python-compatible rounding (HALF_EVEN on the exact binary value) to [LOC_DECIMALS]. */
        fun round3(d: Double): Double = BigDecimal(d).setScale(LOC_DECIMALS, RoundingMode.HALF_EVEN).toDouble()
        private fun plain3(d: Double): String = BigDecimal(d).setScale(LOC_DECIMALS, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString()

        private fun coordinate(v: Any?): Any? = when {
            v == null || v == JSONObject.NULL -> v
            v is Number -> v.toDouble().takeIf { it.isFinite() }?.let(::round3)
            v is String -> v.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::round3)
            else -> null
        }

        /** Every `lat`/`lon` at any depth rounded; every `loc` object reduced to `{lat, lon, acc, age_s}`. */
        fun geo(v: Any?): Any? = when (v) {
            is JSONObject -> {
                val out = JSONObject()
                for (k in keys(v)) {
                    val x = v.opt(k)
                    when {
                        k == "lat" || k == "lon" -> coordinate(x)?.let { out.put(k, it) }
                        k == "loc" -> if (x is JSONObject) out.put(k, loc(x)) else if (x == JSONObject.NULL) out.put(k, x)
                        else -> out.put(k, geo(x))
                    }
                }
                out
            }
            is JSONArray -> JSONArray().also { a -> for (i in 0 until v.length()) a.put(geo(v.opt(i))) }
            else -> v
        }

        private fun loc(l: JSONObject): JSONObject {
            val out = JSONObject()
            for (k in LOC_KEYS) {
                if (!l.has(k)) continue
                val x = l.opt(k)
                if (k == "lat" || k == "lon") coordinate(x)?.let { out.put(k, it) } else if (x is Number) out.put(k, x)
            }
            return out
        }

        // Known labels and actions. DeliveryCore.click's generic confirms and "return to offer details", CourierParser's go online/offline labels.
        private val OTHER_ACTIONS = setOf("confirm", "complete", "finish", "done", "✓", "✔", "teslim et", "tehvil ver", "return to offer details",
            "go online", "go offline", "slide to go online", "slide to go offline", "cevrim ici ol")
        private const val UNIT_NUMBER = "[0-9]+(?:[.,][0-9]{1,2})?"
        /** The Bolt accept button ("22 min, 4,49 ₼", "Accept 22 min, 4,49 ₼"): only accept words, amounts/minutes/km with their unit, separators. */
        private val ACCEPT_BUTTON = Regex("[\\s\\p{Z},·•|/()+\\-–]*(?:(?:" + listOf("accept order", "accept delivery", "accept", "qəbul etmək", "qəbul et", "qəbul",
            "kabul et", "kabul", "принять заказ", "принять").map { Regex.escape(CourierParser.norm(it)) }.joinToString("|") +
            "|$UNIT_NUMBER(?:[\\s\\p{Z}]*[\\-–][\\s\\p{Z}]*$UNIT_NUMBER)?[\\s\\p{Z}]*(?:₼|azn|manat|min|deq|dk|km)|(?:₼|azn)[\\s\\p{Z}]*$UNIT_NUMBER)" +
            "(?![\\p{L}\\p{N}_])[\\s\\p{Z},·•|/()+\\-–]*)+")

        /**
         * The whole [text] is a known courier-app label ([Redactor.isLabel]) or an action the parser and DeliveryCore.click react
         * to (accept, decline, collect, deliver, arrive, confirm, go online/offline). Never true for a text with customer data:
         * every rule is an exact label match except the accept-button shape, which admits no other words.
         */
        fun isKnownAction(text: String): Boolean {
            val n = CourierParser.norm(text)
            return n.isNotEmpty() && (Redactor.isLabel(text) || n in OTHER_ACTIONS || OfferRules.isAccept(text) || CourierParser.isDecline(text) ||
                CourierParser.isCollect(text) || CourierParser.isDeliver(text) || CourierParser.isArrive(text) ||
                (CourierParser.isAccept(text) && ACCEPT_BUTTON.matches(n)))
        }

        // Notification hints. Readiness: the patterns DeliveryCore.readinessNotice applies to CourierParser.norm(text).
        private val READY_IN = Regex("ready in[\\s\\p{Z}]*[0-9]+[\\s\\p{Z}]*min")
        private val READY = Regex("ready for pickup|order is ready|is ready|hazirdir")
        private val MONEY = Regex("₼[\\s\\p{Z}]*[0-9]|(?<![\\p{L}\\p{N}_])[0-9]+(?:[.,][0-9]{1,2})?[\\s\\p{Z}]*(?:₼|azn|manat)(?![\\p{L}\\p{N}_])|(?<![\\p{L}\\p{N}_])azn[\\s\\p{Z}]*[0-9]")
        private val OFFER_WORDS = Regex("(?<![\\p{L}\\p{N}_])(?:" + listOf("new order", "new offer", "new delivery", "new task", "new request", "order request",
            "delivery request", "offer", "teklif", "təklif", "yeni sipariş", "yeni sifariş", "новый заказ", "новое предложение")
            .map { Regex.escape(CourierParser.norm(it)) }.distinct().joinToString("|") + ")(?![\\p{L}\\p{N}_])")

        /** `ready` | `offer` | `other` for a notification's title/text/big/sub. */
        fun notificationHint(text: String): String {
            val n = CourierParser.norm(text)
            return when {
                READY_IN.containsMatchIn(n) || READY.containsMatchIn(n) -> "ready"
                MONEY.containsMatchIn(n) || OFFER_WORDS.containsMatchIn(n) -> "offer"
                else -> "other"
            }
        }

        /**
         * A line KuryePlus draws itself (overlay badge, including the offer net line), as the accessibility tree or OCR may read it.
         * The same recogniser [CourierParser] uses ([OwnLines.isOwn]), so the export and the parser cannot drift apart.
         */
        fun isPanelLine(line: String): Boolean = OwnLines.isOwn(line)

        /** [texts] without panel lines; a multi-line text keeps its other lines ([OwnLines.withoutOwn]). */
        fun withoutPanel(texts: List<String>): List<String> = OwnLines.withoutOwn(texts)
    }
}
