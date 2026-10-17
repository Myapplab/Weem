package com.kuryelog.logic.core

import com.kuryelog.logic.core.Msg.putList
import com.kuryelog.logic.core.Msg.putN
import org.json.JSONObject

/**
 * `TripStages` v1 (plan 4.4; bugünkü feed v2 `stages`, `X_protocol` P3, RS `TripStages.kt`): `:orders` → `:map`, `:scooter`, `:link`.
 * ≤3 açık + ≤3 biten (son 10 dk) sipariş; yalnız kimlik, paket, aşama ve saat — müşteri verisi yok (Receiver'a gidebilir).
 * Bugünkü feed sürümü `version` anahtarında; okuyucu ikisini de kabul eder, yazıcı `schemaVersion` yazar.
 */
data class TripStages(val trips: List<Trip> = emptyList()) {
    enum class Stage(val key: String) {
        TO_PICKUP("to_pickup"), AT_PICKUP("at_pickup"), TO_DROPOFF("to_dropoff"), AT_DROPOFF("at_dropoff");
        companion object { fun of(key: String?): Stage? = entries.firstOrNull { it.key == key } }
    }
    enum class Outcome(val key: String) {
        DELIVERED("delivered"), DELIVERED_UNVERIFIED("delivered_unverified"), CANCELLED("cancelled"), UNCONFIRMED("unconfirmed");
        companion object { fun of(key: String?): Outcome? = entries.firstOrNull { it.key == key } }
    }
    /** [pickup]: kaçıncı alış (0'dan); [source]: varışın kaynağı `gps` | `screen`. */
    data class Event(val stage: Stage, val atMs: Long, val pickup: Int? = null, val source: String? = null)
    /** Açıkken [stage] dolu, [outcome] null; bitmişte tersi. */
    data class Trip(
        val tripId: String, val platformPackage: String, val active: Boolean, val stage: Stage?, val outcome: Outcome?,
        val endedAtMs: Long?, val events: List<Event>,
    )

    /** Açık (aktif) sipariş var: ev kuralı vardiyayı bitirmez (F4.5b, CORE.md 1.3). */
    val busy: Boolean get() = trips.any { it.active }

    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).putList("trips", trips) { t ->
        JSONObject().put("tripId", t.tripId).put("platformPackage", t.platformPackage).put("active", t.active).putN("stage", t.stage?.key)
            .putN("outcome", t.outcome?.key).putN("endedAtMs", t.endedAtMs).putList("events", t.events) { e ->
                JSONObject().put("stage", e.stage.key).put("atMs", e.atMs).apply { e.pickup?.let { put("pickup", it) }; e.source?.let { put("source", it) } }
            }
    }

    companion object {
        const val SCHEMA = 1
        const val MAX_ACTIVE = 3
        const val MAX_ENDED = 3
        const val ENDED_KEEP_MS = 600_000L
        val EMPTY = TripStages()

        fun parse(raw: String?): TripStages? = Msg.obj(raw)?.let(::parse)
        fun parse(j: JSONObject): TripStages? {
            if ((Msg.version(j) ?: Msg.int(j, "version")) != SCHEMA) return null
            return TripStages(Msg.array(j, "trips").mapNotNull(::trip))
        }

        private fun trip(j: JSONObject): Trip? {
            val id = Msg.str(j, "tripId")?.takeIf { it.isNotEmpty() } ?: return null
            val pkg = Msg.str(j, "platformPackage") ?: return null
            val active = Msg.bool(j, "active") ?: return null
            val events = Msg.array(j, "events").mapNotNull { e ->
                val stage = Stage.of(Msg.str(e, "stage")) ?: return@mapNotNull null
                Event(stage, Msg.long(e, "atMs") ?: return@mapNotNull null, Msg.int(e, "pickup"), Msg.str(e, "source"))
            }
            return Trip(id, pkg, active, Stage.of(Msg.str(j, "stage")), Outcome.of(Msg.str(j, "outcome")), Msg.long(j, "endedAtMs"), events)
        }
    }
}
