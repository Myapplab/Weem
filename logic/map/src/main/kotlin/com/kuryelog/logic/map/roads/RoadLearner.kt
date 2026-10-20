package com.kuryelog.logic.map.roads

/** Öğrenme girdisi: her GPS fix'i (yol eşleşmesiyle). [tMs] epoch ms. */
class LearnFix(
    val tMs: Long,
    val lat: Double,
    val lon: Double,
    val kmh: Double,
    val accuracy: Double = 5.0,
    /** Eşleşen sokak (yoksa null). */
    val way: Long? = null,
    val name: String = "",
    /** Tek yönün tersine gidiyor. */
    val wrongWay: Boolean = false,
    /** Sokak "iki yönlü onaylı". */
    val approved: Boolean = false,
    /** Son 1 sn titreşim RMS (m/s²). */
    val vib: Double = 0.0,
    /** Restoran / müşteri / hedef yakını: durmalar sayılmaz. */
    val excludeStops: Boolean = false,
    /** Ana cadde: ters yön onayı verilemez. */
    val mainRoad: Boolean = false,
)

/** Ters yönde kesintisiz bir sürüşün bir sokak parçası. */
class WwRunWay(var name: String, val main: Boolean) {
    var meters = 0.0
}

/** Ters yönde kesintisiz sürüş: içindeki bütün sokak parçaları, onaylı olsun olmasın (sürüş sırasıyla). */
class WwRun(val startMs: Long) {
    var lastMs = startMs
    var lastWay: Long? = null
    val ways = LinkedHashMap<Long, WwRunWay>()
}

/** Onaylı sokakta biten bir ters yön geçişi. */
class WwPass(
    val way: Long,
    val name: String,
    val meters: Double,
    val incidents: Int,
    val endMs: Long,
    val reported: Boolean = false,
    /** Geçişin içinde olduğu kesintisiz ters yön sürüşü (yoksa null). */
    val run: WwRun? = null,
)

class RoadLearner(val k: RoadKnowledge) {
    /** Son kesintisiz ters yön sürüşü (bitmiş olabilir). */
    val run: WwRun? get() = TODO("A6.11")

    /** Onaylı sokakta süren ters yön geçişi ("Sorun yaşadım" düğmesi). */
    val liveWw: LiveWw? get() = TODO("A6.11")

    /** Süren geçişe "sorun vardı" işaretle (geçiş bitince ayrıca sorulmaz). */
    fun markLiveReported(): Boolean = TODO("A6.11")

    /** Her fix'te çağrılır; biten ters yön geçişlerini döner (sayaçlar işlenmiş olarak). */
    fun feed(f: LearnFix): List<WwPass> = TODO("A6.11")

    /** Süren geçişi kapatır (uygulama kapanırken / gün biterken). */
    fun flush(): WwPass? = TODO("A6.11")

    class LiveWw(val way: Long, val name: String, val meters: Double)

    companion object {
        const val MOVING_KMH = 12.0
        const val SLOW_KMH = 8.0
        const val STOP_KMH = 5.0
        const val STOP_MIN_S = 3
        const val STOP_MAX_S = 150
        const val WW_MIN_M = 25.0
        const val BRAKE_KMH = 9.0
        const val RUN_GAP_S = 10

        /**
         * "Hayır" cevabında onaylanacak sokaklar: geçişin sokağı ve aynı kesintisiz sürüşte ters yönde sürülen parçalar (en az
         * [WW_MIN_M] ya da geçişle aynı adlı). Ana cadde onaylanmaz. way → ad.
         */
        fun wwCleanApprovals(p: WwPass): Map<Long, String> = TODO("A6.11")
    }
}
