package com.kuryelog.logic.core

import com.kuryelog.logic.core.Msg.putN
import org.json.JSONObject

/** Navigasyon hedefinin türü (`X_protocol` P8). */
enum class NavKind(val key: String) {
    RESTAURANT("restaurant"), CUSTOMER("customer"), UNKNOWN("unknown");
    companion object { fun of(key: String?): NavKind? = entries.firstOrNull { it.key == key } }
}

/** Hedef nereden geldi: kurye uygulamasının "Navigasyon" isteği, pano, arama, harita, okunan sipariş (`X_protocol` P8; YolGo `map` dahil). */
enum class NavOrigin(val key: String) {
    INTENT("intent"), CLIPBOARD("clipboard"), SEARCH("search"), MAP("map"), ORDER("order");
    companion object { fun of(key: String?): NavOrigin? = entries.firstOrNull { it.key == key } }
}

private val packagePattern = Regex("[A-Za-z0-9_.]{1,100}")

/**
 * `NavTarget` v1 (plan 4.4; YolGo `IncomingLink` + nav `target`): Wolt/Bolt "Navigasyon" bağlantısı yakalanınca (yedek: okunan adres).
 * `:orders`/`:capture` → `:map` (P → R). [label] restoran adı ya da müşteri adres metni (S5); müşteri adı, telefonu, notları yok.
 */
data class NavTarget(
    val requestId: String,
    val lat: Double,
    val lon: Double,
    val kind: NavKind,
    val origin: NavOrigin,
    val sourcePackage: String?,
    val requestedAtMs: Long,
    val label: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("requestId", requestId).put("lat", lat).put("lon", lon)
        .put("kind", kind.key).put("origin", origin.key).putN("sourcePackage", sourcePackage).put("requestedAtMs", requestedAtMs)
        .putN("label", label?.take(MAX_LABEL))

    companion object {
        const val SCHEMA = 1
        const val MAX_LABEL = 1000

        fun parse(raw: String?): NavTarget? = Msg.obj(raw)?.let(::parse)
        fun parse(j: JSONObject): NavTarget? {
            if (Msg.version(j) != SCHEMA) return null
            return NavTarget(
                requestId = Msg.str(j, "requestId")?.takeIf { it.isNotEmpty() } ?: return null,
                lat = Msg.num(j, "lat")?.takeIf { it in -90.0..90.0 } ?: return null,
                lon = Msg.num(j, "lon")?.takeIf { it in -180.0..180.0 } ?: return null,
                kind = NavKind.of(Msg.str(j, "kind")) ?: return null,
                origin = NavOrigin.of(Msg.str(j, "origin")) ?: return null,
                sourcePackage = Msg.str(j, "sourcePackage")?.takeIf { packagePattern.matches(it) },
                requestedAtMs = Msg.positive(j, "requestedAtMs") ?: return null,
                label = Msg.str(j, "label")?.take(MAX_LABEL),
            )
        }
    }
}

/**
 * `RouteStatus` v1 (plan 4.4; bugünkü `nav/v1/current`, `X_protocol` P8, RS `YolGoNav.kt`): `:map` → `:orders` (varış, M20), `:link`
 * (R → P). Ev rotasında [target] null (ev koordinatı gönderilmez). Bozuk [target]/[route] tek başına atılır, gerisi kalır.
 */
data class RouteStatus(val target: Target?, val route: Route?, val updatedAtMs: Long) {
    data class Target(val lat: Double, val lon: Double, val kind: NavKind, val origin: NavOrigin, val requestedAtMs: Long, val sourcePackage: String?)
    /** [remainingM] 0–500 000 m; [remainingTurns] 0–1000 ya da bilinmiyor. */
    data class Route(val remainingM: Double, val remainingTurns: Int?, val measuredAtMs: Long)

    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA)
        .putN("target", target?.let { JSONObject().put("lat", it.lat).put("lon", it.lon).put("kind", it.kind.key).put("origin", it.origin.key)
            .put("requestedAtMs", it.requestedAtMs).putN("sourcePackage", it.sourcePackage) })
        .putN("route", route?.let { JSONObject().put("remainingM", it.remainingM).putN("remainingTurns", it.remainingTurns).put("measuredAtMs", it.measuredAtMs) })
        .put("updatedAtMs", updatedAtMs)

    /** Hedef en çok 90 dk, rota ölçümü en çok 30 sn geçerli (RS `YolGoNav.kt:22-26`). */
    fun freshTarget(now: Long): Target? = target?.takeIf { now - it.requestedAtMs in 0..TARGET_MAX_AGE_MS }
    fun freshRoute(now: Long): Route? = route?.takeIf { now - it.measuredAtMs in 0..ROUTE_MAX_AGE_MS }

    companion object {
        const val SCHEMA = 1
        const val TARGET_MAX_AGE_MS = 5_400_000L
        const val ROUTE_MAX_AGE_MS = 30_000L
        /** Bugünkü YolGo yükünün üst sınırı (`YolGoNav.MAX_CHARS`). */
        const val FEED_MAX_CHARS = 4000

        fun parse(raw: String?): RouteStatus? = Msg.obj(raw)?.let(::parse)
        fun parse(j: JSONObject): RouteStatus? {
            if (Msg.version(j) != SCHEMA) return null
            return RouteStatus(j.optJSONObject("target")?.let(::target), j.optJSONObject("route")?.let(::route), Msg.positive(j, "updatedAtMs") ?: return null)
        }

        /** Bugünkü `content://az.duzyol.app.nav/nav/v1/current` yükü (`source: "yolgo"`, `generatedAtMs` zorunlu, `updatedAtMs` yoksa o). */
        fun fromNavFeed(raw: String?): RouteStatus? {
            if (raw == null || raw.length > FEED_MAX_CHARS) return null
            val j = Msg.obj(raw) ?: return null
            if (Msg.version(j) != 1 || Msg.str(j, "source") != "yolgo") return null
            val generated = Msg.positive(j, "generatedAtMs") ?: return null
            return RouteStatus(j.optJSONObject("target")?.let(::target), j.optJSONObject("route")?.let(::route), Msg.positive(j, "updatedAtMs") ?: generated)
        }

        private fun target(j: JSONObject): Target? = Target(
            lat = Msg.num(j, "lat")?.takeIf { it in -90.0..90.0 } ?: return null,
            lon = Msg.num(j, "lon")?.takeIf { it in -180.0..180.0 } ?: return null,
            kind = NavKind.of(Msg.str(j, "kind")) ?: return null,
            origin = NavOrigin.of(Msg.str(j, "origin")) ?: return null,
            requestedAtMs = Msg.positive(j, "requestedAtMs") ?: return null,
            sourcePackage = Msg.str(j, "sourcePackage")?.takeIf { packagePattern.matches(it) },
        )

        private fun route(j: JSONObject): Route? {
            val m = Msg.num(j, "remainingM")?.takeIf { it in 0.0..500_000.0 } ?: return null
            val t = j.opt("remainingTurns")
            val turns = if (t == null || t == JSONObject.NULL) null else (t as? Number)?.toInt()?.takeIf { it in 0..1_000 } ?: return null
            return Route(m, turns, Msg.positive(j, "measuredAtMs") ?: return null)
        }
    }
}
