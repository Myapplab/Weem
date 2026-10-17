package com.kuryelog.logic.core

import com.kuryelog.logic.core.Msg.putN
import org.json.JSONObject

/**
 * `DaySummary` v1 (plan 4.4; RS `OrderHistory`, `OnlineSessions`): `:orders` → `:map`, `:economics`, `:link` (P → R), sipariş bitince,
 * en çok dakikada bir. Platform başına sipariş, brüt, net, çevrimiçi dakika; günün nakdi. Ölçülmeyen değer null.
 */
data class DaySummary(val day: String, val platforms: Map<CourierPlatform, PlatformDay> = emptyMap(), val cashAzn: Double? = null) {
    data class PlatformDay(val orders: Int, val grossAzn: Double? = null, val netAzn: Double? = null, val onlineMin: Double? = null)

    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("day", day)
        .put("platforms", JSONObject().apply {
            for ((p, d) in platforms) put(p.key, JSONObject().put("orders", d.orders).putN("grossAzn", d.grossAzn).putN("netAzn", d.netAzn).putN("onlineMin", d.onlineMin))
        })
        .putN("cashAzn", cashAzn)

    companion object {
        const val SCHEMA = 1

        fun parse(raw: String?): DaySummary? = Msg.obj(raw)?.let(::parse)
        fun parse(j: JSONObject): DaySummary? {
            if (Msg.version(j) != SCHEMA) return null
            val day = Msg.str(j, "day")?.let { runCatching { LogTime.checkedDay(it) }.getOrNull() } ?: return null
            val ps = j.optJSONObject("platforms")
            val platforms = CourierPlatform.entries.mapNotNull { p ->
                val d = ps?.optJSONObject(p.key) ?: return@mapNotNull null
                p to PlatformDay(Msg.int(d, "orders")?.takeIf { it >= 0 } ?: return@mapNotNull null, Msg.num(d, "grossAzn"), Msg.num(d, "netAzn"), Msg.num(d, "onlineMin"))
            }.toMap()
            return DaySummary(day, platforms, Msg.num(j, "cashAzn"))
        }
    }
}
