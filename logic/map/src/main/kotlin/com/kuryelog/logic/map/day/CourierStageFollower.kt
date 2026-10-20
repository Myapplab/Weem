package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.TripStages
import org.json.JSONObject

/** Açık yolculuk (feed `activeTrips`): [stage] bilinmeyen aşamada null. */
data class FollowerTrip(val id: String, val platform: String, val stage: TripStages.Stage?)

/**
 * Aşama izleyicinin girdisi (MAP.md 11.7): [trips] açık yolculuklar (sıralı); [stages] Rawstats'ın gerçek saatli aşama satırları (yolculuk kimliğine göre),
 * yoksa null (eski kural: iki eksik okuma).
 */
data class FollowerFeed(val trips: List<FollowerTrip>, val stages: Map<String, TripStages.Trip>? = null)

/** `TripStages` (`:core`) → [FollowerFeed]; okuma yoksa (`OrderEvents` bağlı değil) null. */
object StageFeed {
    fun from(stages: TripStages?): FollowerFeed? = TODO("A6.8")

    /** `to_pickup` → toRestaurant, `at_pickup` → atRestaurant, `to_dropoff` → toCustomer, `at_dropoff` → atCustomer. */
    fun phaseOf(stage: TripStages.Stage): OrderPhase = TODO("A6.8")
}

/**
 * Ekran okumasının aşamalarını gün motoruna işler (Dart `CourierStageFollower`, MAP.md 11.7): elle basmadan sipariş açar/ilerletir/teslim eder; geri yön uygulanmaz;
 * teslim için iki eksik okuma (ya da bitiş saatli `stages`); gün kapalıyken izlenmez. Kalıcı durum `{"v":1,"trip","order","stage","at"?,"closed":[]}`.
 */
class CourierStageFollower {
    val linkedTrip: String? get() = TODO("A6.8")
    val linkedOrder: Int? get() = TODO("A6.8")

    /** İlk eksik okumadan sonra kısa yeniden okuma istenir. */
    val awaitingSecondRead: Boolean get() = TODO("A6.8")

    /** [feed] null = okuma yok (hiçbir şey değişmez). */
    fun sync(engine: ShiftEngine, feed: FollowerFeed?, atMs: Long, lat: Double? = null, lon: Double? = null): List<DayEvent> = TODO("A6.8")

    fun dayChanged(): Unit = TODO("A6.8")

    fun toJson(): JSONObject = TODO("A6.8")
    fun restore(j: JSONObject): Unit = TODO("A6.8")
}
