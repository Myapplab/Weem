package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.input.tl
import com.kuryelog.logic.map.nav.RoadInputs
import com.kuryelog.logic.map.route.BadSpots
import com.kuryelog.logic.map.route.QualityRating
import com.kuryelog.logic.map.route.StreetList
import com.kuryelog.logic.map.route.StreetPrefsStore
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.surface.MarkStore
import com.kuryelog.logic.map.surface.SurfaceLabel
import com.kuryelog.logic.map.surface.SurfaceMark
import com.kuryelog.logic.map.ui.MapToast
import com.kuryelog.logic.map.ui.ToastTone
import com.kuryelog.logic.map.ui.Toasts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** `files/map/roads/<anahtar>.json` okuma/yazma (MAP.md 9.10): atomik yazım Android `:map`'te, testte bellek içi. */
interface RoadFiles {
    fun read(name: String): String?
    fun write(name: String, text: String)
}

/** Açık "Ters yönde sorun yaşadın mı?" sorusu. */
class WwQuestion(val pass: WwPass, val askedAtMs: Long = 0L)

/** Titreşim düzeyleri (MAP.md 9.4.4): soru/kalite açılınca güçlü, cevap ve ±10'da seçim, kayıtta orta, hatada kısa titreşim. */
enum class RoadHaptic { HEAVY, SELECTION, MEDIUM, VIBRATE }

/** UI'ın izlediği görüntü; bilgi yerinde değiştiği için sayaçlar artar. */
class RoadState(
    val k: RoadKnowledge,
    val question: WwQuestion? = null,
    /** Onaylı sokak kümesi her değiştiğinde artar (harita katmanı yenilemesi, yeniden rota). */
    val approvedRev: Int = 0,
    /** Elle değişiklikte artar (yol kalitesi katmanı yenilemesi); otomatik kayıt artırmaz. */
    val statsRev: Int = 0,
    /** "Yol yok" ya da ww kesimi değişti. */
    val ghostRev: Int = 0,
    /** Rota girdisini değiştiren elle eylem (puan, ww sayacı, hayalet, kötü nokta, "Sorun yok" onayı): açık rota yeniden hesaplanır. */
    val routeRev: Int = 0,
    val rev: Int = 0,
    /** Onaylı sokakta süren ters yön geçişi. */
    val live: RoadLearner.LiveWw? = null,
    val marks: List<SurfaceMark> = emptyList(),
)

/**
 * `RoadController`'ın Riverpod'suz çekirdeği (MAP.md 9.3.3–9.3.5, 9.6, 9.12): vardiya kapsamı ([startDay]/[endDay]), [onFix], soru ve
 * zaman aşımı ([tick], 5 sn), cevap, "Sorun yaşadım", elle eylemler, kötü noktalar, kayıt politikası (30 sn kısıtı, "aynıysa yazma",
 * [onPause] zorla kayıt), gün silme ve rota girdileri ([forPlan]). Bütün genel işlevler eş zamanlı çağrılabilir (tek kilit).
 */
class RoadBook(
    private val files: RoadFiles,
    internal val streets: StreetPrefsStore,
    internal val toasts: Toasts,
    private val settings: () -> MapSettings,
    internal val clock: Clock,
    internal val lang: () -> Lang = { Lang.TR },
) {
    /** Sürüş günlüğü olayı (MAP.md 9.10 "Olaylar"; koordinat yazılmaz). */
    @Volatile
    var log: (String, Map<String, Any?>) -> Unit = { _, _ -> }

    @Volatile
    var haptic: (RoadHaptic) -> Unit = {}

    private val lock = Any()
    private val written = HashMap<String, String>()
    private val k: RoadKnowledge
    private var learner: RoadLearner
    private var contributions: RoadDayContributions
    internal val markStore: MarkStore
    private var learningDay: String? = null
    private var rev = 0
    private var ghostRev = 0
    private var statsRev = 0
    private var approvedRev = 0
    private var routeRev = 0
    private var question: WwQuestion? = null
    private var cleanRun: WwRun? = null
    private var recent: WwPass? = null
    private var recentAdded = 0.0
    private var dirty = false
    private var lastSaveMs: Long
    private var liveWay: Long? = null
    private var ghostCache: Triple<RoadGraph, Int, Pair<Set<Int>, Set<Int>>>? = null
    private val flow: MutableStateFlow<RoadState>

    init {
        val now = clock.wallMs()
        fun raw(name: String): String? = files.read(name)?.also { written[name] = it }
        /** Okunamayan dosya sessizce boşaltılmaz: `<ad>.damaged-<ms>` kopyası (MAP.md 9.10, 9.13). */
        fun <T> load(name: String, decode: (String?) -> T?, empty: T): T {
            val r = raw(name)
            val v = decode(r)
            if (v == null) {
                files.write("$name.damaged-$now", r ?: "")
                written.remove(name)
                return empty
            }
            return v
        }
        k = RoadKnowledge(
            ways = HashMap(load(WAYS, { RoadKnowledge.decodeMapOrNull(it, WayStats::fromJson) }, emptyMap())),
            ww = HashMap(load(WW, { RoadKnowledge.decodeMapOrNull(it, WwStats::fromJson) }, emptyMap())),
            ghosts = ArrayList(load(GHOST, RoadKnowledge::decodeGhostsOrNull, emptyList())),
            wwSegs = ArrayList(load(WWSEG, RoadKnowledge::decodeGhostsOrNull, emptyList())),
            ratings = HashMap(load(RATE, { RoadKnowledge.decodeMapOrNull(it) { v -> if (v is Number) QualityRating.migrate(v.toInt()) else 0 } }, emptyMap())),
            names = HashMap(load(NAMES, { RoadKnowledge.decodeMapOrNull(it) { v -> v as? String ?: "" } }, emptyMap())),
        )
        learner = RoadLearner(k)
        val days = raw(DAYS)
        contributions = try {
            RoadDayContributions(k, days, clock.zone())
        } catch (e: Exception) {
            // Okunamayan gün defteri: ham kopya saklanır, okunabilen toplamlar taban olur; geçmiş gün üyeliği uydurulmaz.
            files.write(DAYS_DAMAGED, days ?: "")
            written.remove(DAYS)
            RoadDayContributions(k, null, clock.zone())
        }
        markStore = MarkStore(files) { clock.wallMs() }
        lastSaveMs = now
        flow = MutableStateFlow(view())
    }

    val state: RoadState get() = flow.value
    val states: StateFlow<RoadState> get() = flow

    /** Öğrenme kapsamı açık mı (vardiya açık). */
    val learning: Boolean get() = synchronized(lock) { learningDay != null }

    // ---------------------------------------------------------------- Kapsam

    /** Vardiya açıldı: öğrenme kapsamı gün kimliğine (`yolgo-<startedAtMs>`) bağlanır. */
    fun startDay(dayId: String) = synchronized(lock) { learningDay = dayId }

    /** Vardiya kapandı: süren geçiş kapanır, katkı yakalanır; soru, son geçiş, canlı ve "temiz sürüş" sıfırlanır; zorla kayıt. */
    fun endDay() = synchronized(lock) {
        if (learningDay == null) return@synchronized
        learner.flush()
        captureAutomatic()
        learningDay = null
        learner = RoadLearner(k)
        question = null
        recent = null
        liveWay = null
        cleanRun = null
        save(force = true)
    }

    // ---------------------------------------------------------------- Öğrenme

    /** Her konum fix'inde (yol eşleşmesiyle); vardiya kapalıysa yok sayılır. */
    fun onFix(f: LearnFix) = synchronized(lock) {
        if (learningDay == null) return@synchronized
        val passes = learner.feed(f)
        dirty = true
        var emit = false
        for (p in passes) {
            onPass(p)
            emit = true
        }
        val lw = learner.liveWw?.way
        if (lw != liveWay) {
            liveWay = lw
            emit = true
        }
        if (emit) emit()
        captureAutomatic()
        save()
    }

    private fun onPass(p: WwPass) {
        log("ww_pass", buildMap { put("way", p.way); put("m", Math.round(p.meters)); put("inc", p.incidents); if (p.reported) put("reported", true) })
        recent = p
        recentAdded = if (p.reported) 1.0 else 0.0
        if (p.reported) return // geçişte zaten "sorun" denildi
        // "Sorun yok" denilen sürüşün devamı (yeni onaylanan parçada): yeniden sorulmaz.
        if (p.incidents == 0 && p.run != null && p.run === cleanRun) return
        if (p.incidents > 0 || settings().wwAskAlways) {
            expireQuestion()
            question = WwQuestion(p, clock.wallMs())
            haptic(RoadHaptic.HEAVY)
        }
    }

    /** 5 sn'lik tik: 20 sn'yi geçen soru zaman aşımına uğrar; kayıt politikası. */
    fun tick() = synchronized(lock) {
        val q = question
        if (q != null && (clock.wallMs() - q.askedAtMs) / 1000 >= ASK_TIMEOUT_S) {
            expireQuestion()
            emit()
        }
        save()
    }

    /** Cevapsız soru: sert fren / durma algılandıysa yarım sorun sayılır. */
    private fun expireQuestion() {
        val q = question ?: return
        question = null
        if (q.pass.incidents > 0) {
            k.wwStats(q.pass.way).problems += 0.5
            if (recent === q.pass) recentAdded = 0.5
            dirty = true
            captureAutomatic()
        }
    }

    /** Ses+ = "evet, sorun" ([problem] true), Ses− = "hayır". Soru açıksa true (ses tuşu sonraki önceliğe geçmez). */
    fun answer(problem: Boolean): Boolean = synchronized(lock) {
        val q = question ?: return false
        question = null
        var approved: Map<Long, String> = emptyMap()
        if (problem) {
            k.wwStats(q.pass.way).problems += 1
            if (recent === q.pass) recentAdded = 1.0
        } else {
            if (recent === q.pass) recent = null // "sorun yok" dendi; sonradan eklenmesin
            approved = approveClean(q.pass)
        }
        dirty = true
        captureAutomatic()
        log("ww_answer", buildMap { put("way", q.pass.way); put("problem", problem); if (approved.isNotEmpty()) put("approved", approved.keys.toList()) })
        haptic(RoadHaptic.SELECTION)
        emit()
        save(force = true)
        true
    }

    /**
     * YG-Y9: "Sorun yok" → geçişin sokağı ve aynı kesintisiz ters yön sürüşündeki parçalar hemen onaylı olur (kara listedeki ve ana cadde
     * hariç). Dönüş: yeni onaylananlar. Toast "Onaylandı: …" + "Geri al".
     */
    private fun approveClean(p: WwPass): Map<Long, String> {
        cleanRun = p.run
        val prefs = streets.current
        val added = LinkedHashMap<Long, String>()
        for ((id, name) in RoadLearner.wwCleanApprovals(p)) {
            if (id in prefs.approved || id in prefs.blocked) continue
            streets.approve(id, name)
            added[id] = name
        }
        if (added.isEmpty()) return added
        approvedRev++
        routeRev++
        val l = lang()
        val names = added.values.map { it.ifEmpty { tl(l, "isimsiz yol", "adsız yol") } }.distinct().joinToString(", ")
        toasts.show(
            MapToast(
                tl(l, "Onaylandı: $names (iki yön)", "Təsdiqləndi: $names (iki tərəfli)"), ToastTone.GREEN, 6,
                actionLabel = tl(l, "Geri al", "Geri qaytar"),
                onAction = {
                    synchronized(lock) {
                        for (id in added.keys) streets.remove(StreetList.APPROVED, id)
                        log("undo", mapOf("way" to p.way))
                        toasts.hide()
                        approvedRev++
                        routeRev++
                        emit()
                    }
                },
            ),
        )
        return added
    }

    /** "Sorun yaşadım": açık soru, süren geçiş ya da 90 sn içinde biten geçiş. Kaydedilirse toast "Ters yön sorunu kaydedildi". */
    fun reportProblem(): Boolean = synchronized(lock) {
        if (question != null) return answer(true)
        val done = reportUnlocked()
        if (done) toasts.show(MapToast(tl(lang(), "Ters yön sorunu kaydedildi", "Əks istiqamət problemi qeyd olundu"), ToastTone.ORANGE, 4))
        done
    }

    private fun reportUnlocked(): Boolean {
        val live = learner.liveWw
        if (live != null && learner.markLiveReported()) {
            log("ww_report", mapOf("way" to live.way))
            haptic(RoadHaptic.SELECTION)
            emit()
            return true
        }
        val r = recent
        if (r != null && recentAdded < 1 && (clock.wallMs() - r.endMs) / 1000 <= RECENT_S) {
            k.wwStats(r.way).problems += 1 - recentAdded
            recentAdded = 1.0
            dirty = true
            captureAutomatic()
            log("ww_report", mapOf("way" to r.way))
            haptic(RoadHaptic.SELECTION)
            emit()
            save(force = true)
            return true
        }
        return false
    }

    /** Son geçiş "Sorun yaşadım" ile hâlâ işaretlenebilir mi? */
    fun canReportRecent(): Boolean = synchronized(lock) {
        val r = recent
        r != null && recentAdded < 1 && (clock.wallMs() - r.endMs) / 1000 <= RECENT_S
    }

    // ---------------------------------------------------------------- Kalite

    /** Elle kalite puanı (%; [QualityRating.NONE] kaldırır): katmanı yeniler, `roads.rate.v1.json`'a yazar. */
    fun rate(way: Long, percent: Int, name: String = "") = synchronized(lock) {
        if (name.isNotEmpty()) k.names[way] = name
        if (percent == QualityRating.NONE) k.ratings.remove(way) else k.ratings[way] = percent
        changed()
    }

    /** "Öğrenileni sıfırla": gün defterinden, otomatik istatistikten ve elle puandan siler. */
    fun resetQuality(way: Long) = synchronized(lock) {
        contributions.resetWay(way)
        k.ways.remove(way)
        k.ratings.remove(way)
        changed()
    }

    fun qualityOf(way: Long, g: RoadGraph? = null): Double = synchronized(lock) { k.qualityOf(way, g) }

    // ---------------------------------------------------------------- Ters yön (elle)

    fun addWwPass(way: Long, problem: Boolean, name: String = "") = synchronized(lock) {
        if (name.isNotEmpty()) k.names[way] = name
        val st = k.wwStats(way)
        st.passes++
        if (problem) st.problems += 1
        st.lastAt = clock.wallMs()
        contributions.manual(k)
        changed()
    }

    fun resetWw(way: Long) = synchronized(lock) {
        contributions.resetWay(way, wrongWay = true)
        k.ww.remove(way)
        changed()
    }

    // ---------------------------------------------------------------- Yol yok, ww kesimi

    /** Kenarı (ya da sokağın tamamını) "haritada var, gerçekte yok" işaretler; sokağın tamamı zaten işaretliyse onu döner. */
    fun markGhost(g: RoadGraph, edge: Int, all: Boolean = false): GhostMark = synchronized(lock) {
        val way = g.osmIdOf(edge)
        k.ghosts.firstOrNull { it.way == way && it.all }?.let { return it }
        val mid = g.geometry(edge, 0.5, 0.5).first()
        val name = g.nameOf(edge)
        if (name.isNotEmpty()) k.names[way] = name
        val mark = GhostMark(way, mid.lat, mid.lon, name, clock.wallMs(), all)
        k.ghosts.removeAll { it.way == way && (all || Geo.haversine(it.lat, it.lon, mid.lat, mid.lon) < 15) }
        k.ghosts += mark
        log("ghost", buildMap { put("way", way); if (all) put("all", true) })
        ghostChanged()
        mark
    }

    /** Ana caddenin bu kesiminde ters yöne izin ver ([allow] false: izni kaldır). */
    fun setWwSegment(g: RoadGraph, edge: Int, allow: Boolean) = synchronized(lock) {
        val way = g.osmIdOf(edge)
        val mid = g.geometry(edge, 0.5, 0.5).first()
        val name = g.nameOf(edge)
        if (name.isNotEmpty()) k.names[way] = name
        k.wwSegs.removeAll { it.way == way && Geo.haversine(it.lat, it.lon, mid.lat, mid.lon) < 15 }
        if (allow) k.wwSegs += GhostMark(way, mid.lat, mid.lon, name, clock.wallMs())
        log("ww_segment", mapOf("way" to way, "allow" to allow))
        ghostChanged()
    }

    fun wwSegAllowed(g: RoadGraph, edge: Int): Boolean = synchronized(lock) { k.wwSegAt(g, edge) != null }

    fun removeGhost(m: GhostMark) = synchronized(lock) {
        k.ghosts.remove(m)
        ghostChanged()
    }

    fun clearGhosts() = synchronized(lock) {
        k.ghosts.clear()
        ghostChanged()
    }

    private fun ghostChanged() {
        ghostRev++
        changed()
    }

    // ---------------------------------------------------------------- Kötü noktalar (Ses−, SORU-Q1)

    /** Ses− tek basış: buranın ±25 m'si kötü nokta. */
    fun addBadSpot(way: Long, lat: Double, lon: Double): SurfaceMark = synchronized(lock) {
        val now = clock.wallMs()
        val m = SurfaceMark("spot-$now", way, lat, lon, now, SurfaceLabel.ROUGH)
        markStore.save(m)
        log("bad_spot", mapOf("way" to way))
        routeRev++
        emit()
        m
    }

    /** Aynı yolda [lat]/[lon]'a ≤ [radiusM] etkin kötü noktalar silinir (geri alınabilir); silinenler döner. */
    fun clearBadSpots(way: Long, lat: Double, lon: Double, radiusM: Double = 40.0): List<SurfaceMark> = synchronized(lock) {
        val spots = markStore.marks.filter { BadSpots.isBadSpot(it) && it.way == way && Geo.haversine(it.lat, it.lon, lat, lon) <= radiusM }
        if (spots.isEmpty()) return@synchronized spots
        for (m in spots) markStore.setRemoved(m.id, true)
        log("bad_spot_clear", mapOf("way" to way, "n" to spots.size))
        routeRev++
        emit()
        spots
    }

    /** Kalite sekmesi: sil / geri al. */
    fun setSpotRemoved(id: String, removed: Boolean) = synchronized(lock) {
        markStore.setRemoved(id, removed)
        routeRev++
        emit()
    }

    /** Ses− uzun, nokta yoksa: kara listeden çıkar, puan ≤ 25 ise silinir. İşaret yoksa false. */
    fun clearBadRoad(way: Long): Boolean = synchronized(lock) {
        val wasBlocked = way in streets.current.blocked
        val rating = k.ratings[way]
        if (!wasBlocked && (rating == null || rating > QualityRating.BAD)) return@synchronized false
        streets.remove(StreetList.BLOCKED, way)
        if (rating != null && rating <= QualityRating.BAD) {
            k.ratings.remove(way)
            statsRev++
            dirty = true
            save(force = true)
        }
        log("bad_road_clear", mapOf("way" to way))
        routeRev++
        emit()
        true
    }

    /** Ses± ile sokak kararı (Y8: `approve`, `steep`, `undo`): olay, onaylı kümesi değiştiyse katman ve rota sayaçları. */
    fun streetChanged(event: String, way: Long) = synchronized(lock) {
        log(event, mapOf("way" to way))
        approvedRev++
        routeRev++
        emit()
    }

    // ---------------------------------------------------------------- Rota girdileri

    /** Planlayıcı girdileri (MAP.md 9.9): elle puan, ww oranları/kaçınma, hayalet ve ww kesimi kenarları (önbellekli), kötü noktalar. */
    fun forPlan(g: RoadGraph?): RoadInputs = synchronized(lock) {
        val s = settings()
        val edges = if (g == null) emptySet<Int>() to emptySet() else {
            val c = ghostCache
            if (c != null && c.first === g && c.second == ghostRev) c.third else {
                val v = k.ghostEdges(g) to k.wwSegEdges(g)
                ghostCache = Triple(g, ghostRev, v)
                v
            }
        }
        RoadInputs(
            ghost = edges.first,
            learned = k.learnedForRouting(),
            marks = markStore.marks,
            wwRatio = k.wrongWayRatios(),
            wwAvoid = k.wrongWayAvoid(s.wwMaxProblemPct / 100),
            wrongWayEdges = edges.second,
        )
    }

    // ---------------------------------------------------------------- Kayıt

    /** Uygulama arka plana geçti: zorunlu kayıt (katman sayacını artırmaz). */
    fun onPause() = synchronized(lock) { save(force = true) }

    /** Y17/R12 gün silme kancası: önce yakala, sonra o tarihin kovaları silinir, zorla kayıt; silinen gün kimlikleri döner. */
    fun deleteContributions(date: String): Set<String> = synchronized(lock) {
        captureAutomatic()
        val removed = contributions.deleteDate(k, date)
        dirty = true
        save(force = true)
        removed
    }

    private fun changed() {
        statsRev++
        routeRev++
        dirty = true
        save(force = true, emit = false)
        emit()
    }

    private fun save(force: Boolean = false, emit: Boolean = true) {
        if (!dirty && !force) return
        val now = clock.wallMs()
        if (!force && (now - lastSaveMs) / 1000 < SAVE_S) return
        lastSaveMs = now
        dirty = false
        fun put(name: String, value: String) {
            if (written[name] == value) return
            written[name] = value
            files.write(name, value)
        }
        put(DAYS, contributions.encode())
        put(WAYS, RoadKnowledge.encodeMap(k.ways) { it.toJson() })
        put(WW, RoadKnowledge.encodeMap(k.ww) { it.toJson() })
        put(GHOST, RoadKnowledge.encodeGhosts(k.ghosts))
        put(WWSEG, RoadKnowledge.encodeGhosts(k.wwSegs))
        put(RATE, RoadKnowledge.encodeMap(k.ratings) { it })
        put(NAMES, RoadKnowledge.encodeMap(k.names) { it })
        if (emit) emit()
    }

    private fun captureAutomatic() {
        learningDay?.let { contributions.capture(k, it) }
    }

    private fun view() = RoadState(k, question, approvedRev, statsRev, ghostRev, routeRev, rev, learner.liveWw, markStore.marks)

    private fun emit() {
        rev++
        flow.value = view()
    }

    private fun log(name: String, data: Map<String, Any?>) = try {
        log.invoke(name, data)
    } catch (e: Exception) {
        Unit
    }

    companion object {
        const val WAYS = "roads.ways.v1.json"
        const val WW = "roads.ww.v1.json"
        const val GHOST = "roads.ghost.v1.json"
        const val WWSEG = "roads.wwseg.v1.json"
        const val RATE = "roads.rate.v1.json"
        const val NAMES = "roads.names.v1.json"
        const val DAYS = "roads.days.v2.json"
        const val DAYS_DAMAGED = "roads.days.damaged.v2.json"
        const val ASK_TIMEOUT_S = 20
        const val RECENT_S = 90
        const val SAVE_S = 30
    }
}

/** "Ters yönde sorun yaşadın mı?" kartının metinleri (TR/AZ birebir; MAP.md 9.4.3). */
object WrongWayCardText {
    fun title(lang: Lang): String = tl(lang, "Ters yönde sorun yaşadın mı?", "Əks istiqamətdə problem oldu?")
    fun yes(lang: Lang): String = tl(lang, "Evet, sorun", "Hə, oldu")
    fun no(lang: Lang): String = tl(lang, "Hayır", "Xeyr")

    /** Alt satır: "<ad>[ · sert fren / durma algılandı] · Ses+ evet / Ses− hayır · ~<kalan> sn". */
    fun subtitle(lang: Lang, name: String, incidents: Int, leftS: Int): String {
        val n = name.ifEmpty { tl(lang, "isimsiz yol", "adsız yol") }
        val inc = if (incidents > 0) tl(lang, " · sert fren / durma algılandı", " · qəfil əyləc / dayanma oldu") else ""
        val left = leftS.coerceIn(0, 99)
        return tl(lang, "$n$inc · Ses+ evet / Ses− hayır · ~$left sn", "$n$inc · Səs+ hə / Səs− yox · ~$left san")
    }

    /** Soru yokken süren onaylı geçiş şeridi. */
    fun live(lang: Lang, name: String, ratioPct: Int?): String {
        val n = name.ifEmpty { tl(lang, "isimsiz yol", "adsız yol") }
        val r = ratioPct?.let { tl(lang, " · sorun %$it", " · problem %$it") } ?: ""
        return tl(lang, "Ters yön (onaylı): $n$r", "Əks istiqamət (təsdiqli): $n$r")
    }

    fun report(lang: Lang): String = tl(lang, "Sorun yaşadım", "Problem oldu")
}
