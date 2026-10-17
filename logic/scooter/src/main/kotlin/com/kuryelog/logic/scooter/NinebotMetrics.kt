package com.kuryelog.logic.scooter

import java.util.Collections
import java.util.Locale
import java.util.TreeSet

/**
 * Only mapped register reads. Firmware-specific verification remains a field task (SCOOTER.md 3; NB `NinebotMetrics.java`, line
 * by line). Little-endian unsigned values (int16 when signed), `value = n × scale`, null outside `[min, max]`.
 */
class NinebotMetrics {
    /** One register. Identity matters: the session keeps reads in identity sets and lists (no `equals`, as in the original). */
    class Read(
        val board: Int,
        val index: Int,
        val size: Int,
        val key: String,
        val unit: String,
        internal val signed: Boolean,
        internal val scale: Double,
        internal val min: Double,
        internal val max: Double,
        val sweep: Boolean = false,
    )

    class Value internal constructor(val source: Read, val value: Double, val monoMs: Long)

    /** Opt-in discovery range: read [from, to] on one board as raw int16, logged with hex for field correlation. */
    class Sweep(val board: Int, val from: Int, val to: Int) {
        init {
            if (board < 0 || board > 255) throw IllegalArgumentException("sweep board")
            if (from < 0 || to > 255 || from > to || to - from > 255) throw IllegalArgumentException("sweep range")
        }
    }

    private val byKey = LinkedHashMap<String, Value>()

    fun accept(read: Read, data: ByteArray, now: Long): Value? {
        val v = decode(read, data) ?: return null
        val value = Value(read, v, now)
        byKey[read.key] = value
        return value
    }

    fun values(): Collection<Value> = Collections.unmodifiableCollection(byKey.values)

    fun clear() {
        byKey.clear()
    }

    companion object {
        fun vehicle(): MutableList<Read> = arrayListOf(
            Read(0x16, 0x55, 2, "battery_percent", "%", false, 1.0, 0.0, 100.0),
            Read(0x16, 0x5f, 2, "remaining_range_km", "km", false, .01, 0.0, 200.0),
            Read(0x16, 0x62, 4, "odometer_km", "km", false, .1, 0.0, 1000000.0),
            Read(0x16, 0x64, 4, "lifetime_awake_seconds", "s", false, 1.0, 0.0, 4294967295.0),
            Read(0x16, 0x66, 4, "lifetime_riding_seconds", "s", false, 1.0, 0.0, 4294967295.0),
            Read(0x16, 0x6b, 2, "vehicle_temperature_c", "°C", false, .1, 0.0, 120.0),
            // Candidate dashboard registers from the community G3 map (Ninebot-BLE-MQTT-Bridge): speed 0x57,
            // trip distance 0x68, trip time 0x6A. Board 0x16 matches the mapped VCU family above. Field log
            // 2026-10-07 (0.2.6, session 14) corrected two community scales: 0x68 is 0.1 km per step (raw 2 when the
            // official app showed 0.2 km and the odometer moved 4373.3->4373.5) and 0x6A counts seconds, not minutes
            // (raw 1->76 while lifetime riding seconds rose by 73). Power stays derived (voltage×current); the
            // official app's W value is not V×A and its source register is still unknown.
            Read(0x16, 0x57, 2, "scooter_speed_kmh", "km/h", false, .1, 0.0, 120.0),
            Read(0x16, 0x68, 2, "dashboard_trip_km", "km", false, .1, 0.0, 10000.0),
            Read(0x16, 0x6a, 2, "dashboard_ride_seconds", "s", false, 1.0, 0.0, 1000000.0),
        )

        /** Max G3 internal pack (field-verified 2026-10-07 and 2026-10-09). Any other answering pack is external. */
        const val PRIMARY_BMS = 7

        /** Opt-in, one-time, read-only probe for the external pack ("Battery 2"). BMS 0x22/0x23 never answered on the G3 even with
         *  the original external battery attached (field 2026-10-09). Known G3 boards are excluded: 0x00/0x16 VCU, 0x02 MCU,
         *  0x04 BLE, 0x07 internal BMS; 0x22/0x23 are read on every connection anyway. 0x08 first: next to the internal BMS. */
        val EXTERNAL_BMS_CANDIDATES = intArrayOf(
            0x08, 0x06, 0x09, 0x05, 0x0a, 0x03, 0x0b, 0x01, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x17, 0x18,
            0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20, 0x21, 0x24, 0x25,
        )

        /** A probed board is a pack candidate only with a plausible 48 V-class pack voltage (external pack read 43.7 V at 17 %). */
        const val PACK_MIN_V = 30.0
        const val PACK_MAX_V = 60.0

        /** An external pack counts in the power total only while its percent register answered recently (proves it is a battery). */
        const val PACK_CONFIRM_MS = 60000L

        fun packKey(board: Int, field: String): String = String.format(Locale.ROOT, "bms_%02x_%s", board, field)

        fun voltage(board: Int): Read =
            Read(board, 0x8c, 2, String.format(Locale.ROOT, "bms_%02x_voltage_v", board), "V", false, .01, 20.0, 80.0)

        fun battery(board: Int): List<Read> {
            val k = String.format(Locale.ROOT, "bms_%02x_", board)
            return listOf(
                voltage(board),
                Read(board, 0x8d, 2, k + "current_a_charge_positive", "A", true, .01, -200.0, 200.0),
                Read(board, 0x8f, 2, k + "percent", "%", false, 1.0, 0.0, 100.0),
                // Register 0x8A: the community Max G3 map calls it remaining capacity (mAh), but field 2026-10-09 (session 31) read a
                // constant 1217 on the internal pack at 81 % and 1000 on the external pack at 58 % (official app: 12750 / 10000 mAh total
                // capacity). It is not remaining charge; kept as a raw capacity-like value (×10 mAh?) until its meaning is confirmed.
                Read(board, 0x8a, 2, k + "reg8a_raw", "raw", false, 1.0, 0.0, 65535.0),
            )
        }

        fun sweep(spec: Sweep): MutableList<Read> {
            val list = ArrayList<Read>()
            for (i in spec.from..spec.to) {
                list.add(Read(spec.board, i, 2, String.format(Locale.ROOT, "sweep_%02x_%02x", spec.board, i), "raw", true, 1.0, -32768.0, 32767.0, true))
            }
            return list
        }

        /** Polled on every read pass (~1 s on a G3): pack voltage/current feed the power figure, plus the speed candidate. */
        fun fast(r: Read): Boolean =
            !r.sweep && (r.key.endsWith("_voltage_v") || r.key.endsWith("_current_a_charge_positive") || r.key == "scooter_speed_kmh")

        /** Lifetime counters and the unexplained pack register 0x8a: logged, never on the live panel, so read every few passes. */
        fun rare(r: Read): Boolean =
            !r.sweep && (r.key == "odometer_km" || r.key.startsWith("lifetime_") || r.key.endsWith("_reg8a_raw"))

        /** Fast registers refresh about every second; slow panel registers once per slow rotation (~4 s with seven), rare counters every RARE_EVERY passes. */
        const val FAST_FRESH_MS = 3000L
        const val SLOW_FRESH_MS = 15000L

        fun freshMs(r: Read): Long = if (fast(r)) FAST_FRESH_MS else SLOW_FRESH_MS

        /** Battery power (W), sign kept (+charge/−discharge): the internal pack's fresh voltage×current plus every external pack
         *  whose percent register confirmed it is a battery. Equals the internal pack alone until an external pack is read; the
         *  external pack feeds the internal one while riding (field 2026-10-09), so only the sum is the scooter's consumption. */
        fun powerW(values: Collection<Value>, now: Long, freshMs: Long): Double? {
            val primary = packPowerW(values, now, freshMs, PRIMARY_BMS) ?: return null
            val external = externalPowerW(values, now, freshMs)
            return if (external == null) primary else primary + external
        }

        /** One pack's fresh voltage×current, or null. */
        fun packPowerW(values: Collection<Value>, now: Long, freshMs: Long, board: Int): Double? {
            val v = freshValue(values, packKey(board, "voltage_v"), now, freshMs)
            val a = freshValue(values, packKey(board, "current_a_charge_positive"), now, freshMs)
            return if (v == null || a == null) null else v * a
        }

        /** Sum over confirmed external packs (fresh pair plus a percent reading within PACK_CONFIRM_MS), or null when none. */
        fun externalPowerW(values: Collection<Value>, now: Long, freshMs: Long): Double? {
            var sum: Double? = null
            for (board in packBoards(values)) {
                if (board == PRIMARY_BMS || freshValue(values, packKey(board, "percent"), now, PACK_CONFIRM_MS) == null) continue
                val w = packPowerW(values, now, freshMs, board)
                if (w != null) sum = if (sum == null) w else sum + w
            }
            return sum
        }

        internal fun packBoards(values: Collection<Value>): Set<Int> {
            val boards = TreeSet<Int>()
            for (v in values) if (!v.source.sweep && v.source.key.startsWith("bms_")) boards.add(v.source.board)
            return boards
        }

        private val VEHICLE_KEYS: Set<String> = vehicle().mapTo(HashSet()) { it.key }
        private val PACK_FIELD = Regex("bms_[0-9a-f]{2}_(voltage_v|current_a_charge_positive|percent|reg8a_raw)")

        /** Keys a filtered export may carry: mapped vehicle registers and battery pack fields of any board, never sweep registers. */
        fun exportable(key: String): Boolean = VEHICLE_KEYS.contains(key) || PACK_FIELD.matches(key)

        /** Persisted external pack boards as two-digit hex, e.g. "08". Unparseable, duplicate or reserved entries are skipped.
         *  `trim { it <= ' ' }` is Java's `String.trim()`; `toInt(16)` is `Integer.parseInt` (a leading sign is accepted). */
        fun parseBoards(text: String?): IntArray {
            val out = LinkedHashSet<Int>()
            if (text != null) for (part in text.split(",")) {
                try {
                    val b = part.trim { it <= ' ' }.toInt(16)
                    if (b > 0 && b < 256 && b != PRIMARY_BMS && b != 0x16 && b != 0x04) out.add(b)
                } catch (ignored: NumberFormatException) {
                }
            }
            return out.toIntArray()
        }

        fun addBoard(text: String?, board: Int): String {
            val all = TreeSet<Int>()
            for (b in parseBoards(text)) all.add(b)
            all.add(board)
            val out = StringBuilder()
            for (b in all) {
                if (out.isNotEmpty()) out.append(',')
                out.append(String.format(Locale.ROOT, "%02x", b))
            }
            return out.toString()
        }

        /** Freshest value for one key within freshMs, or null. Lets the dashboard summary read candidate registers. */
        fun freshValue(values: Collection<Value>, key: String, now: Long, freshMs: Long): Double? {
            var best: Value? = null
            for (v in values) {
                if (v.source.key != key) continue
                val age = now - v.monoMs
                if (age < 0 || age > freshMs) continue
                if (best == null || v.monoMs > best.monoMs) best = v
            }
            return best?.value
        }

        fun decode(r: Read, data: ByteArray): Double? {
            if (data.size != r.size || (r.size != 2 && r.size != 4)) return null
            var n = 0L
            for (i in data.indices) n = n or ((data[i].toLong() and 255) shl (i * 8))
            if (r.signed && r.size == 2) n = n.toShort().toLong()
            val value = n * r.scale
            return if (value >= r.min && value <= r.max) value else null
        }
    }
}
