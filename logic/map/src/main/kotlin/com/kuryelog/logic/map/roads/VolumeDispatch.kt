package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.input.tl
import com.kuryelog.logic.map.nav.MatchInfo
import com.kuryelog.logic.map.nav.WrongWayEvent
import com.kuryelog.logic.map.ui.MapToast
import com.kuryelog.logic.map.ui.ToastTone

/** Ses tuşu kararlarının ihtiyaç duyduğu navigasyon bağlamı (Android'de `NavController`). */
interface VolumeEnv {
    /** Son oturtulan kenar (yoksa null). */
    fun match(): MatchInfo?

    /** Son taze konum. */
    fun here(): GeoPoint?

    /** 15 sn içindeki ters yön önerisi. */
    fun suggestion(): WrongWayEvent?
    fun clearSuggestion()

    /** Açık rota varsa yeniden hesapla (`replan('prefs')`). */
    fun replanIfRoute()

    object None : VolumeEnv {
        override fun match(): MatchInfo? = null
        override fun here(): GeoPoint? = null
        override fun suggestion(): WrongWayEvent? = null
        override fun clearSuggestion() = Unit
        override fun replanIfRoute() = Unit
    }
}

/**
 * Ses tuşu dağıtımı (MAP.md 9.5.2; `nav_controller.dart:578-751`). Öncelik: (1) ters yön sorusu açık → cevap (Ses+ evet, Ses− hayır)
 * · (2) kalite % kartı açık → ±10 · (3) Ses+: 450 ms içinde ikinci → kalite sorusu, tek → onay · (4) Ses−: ikinci → aşırı eğimli,
 * tek → kötü nokta ±25 m · (5) Ses− uzun ([onDownHold]) → sil. Tek basış 450 ms bekler: Android katmanı her tuştan [DOUBLE_MS] sonra
 * [due] çağırır (saat enjekte, `Clock.monoMs`).
 */
class VolumeDispatch(
    private val book: RoadBook,
    private val clock: Clock,
    private val prompt: QualityPrompt? = null,
    private val env: VolumeEnv = VolumeEnv.None,
) {
    private var upAt: Long? = null
    private var downAt: Long? = null

    /** Ses+ basışta, Ses− kısa basışta (bırakışta). */
    fun onKey(up: Boolean) {
        if (book.answer(problem = up)) return
        if (prompt?.onVolume(up) == true) return
        val now = clock.monoMs()
        due()
        if (up) {
            if (upAt != null) {
                upAt = null
                openQualityPrompt()
            } else {
                upAt = now
            }
            return
        }
        if (downAt != null) {
            downAt = null
            streetAction(StreetMark.STEEP)
        } else {
            downAt = now
        }
    }

    /** Bekleyen tek basışın süresi dolduysa eylemi yapar. */
    fun due() {
        val now = clock.monoMs()
        upAt?.let { if (now - it >= DOUBLE_MS) { upAt = null; streetAction(StreetMark.APPROVE) } }
        downAt?.let { if (now - it >= DOUBLE_MS) { downAt = null; streetAction(StreetMark.BAD_SPOT) } }
    }

    /** Bekleyen ilk basış var mı (Android [due] zamanlayıcısı için). */
    val pending: Boolean get() = upAt != null || downAt != null

    /** Ses− uzun (≥ 1 sn): buradaki kötü noktalar, yoksa kara liste / kötü puan işareti silinir. Bekleyen tek Ses− iptal. */
    fun onDownHold() {
        downAt = null
        val l = book.lang()
        val m = env.match()
        if (m == null) {
            toast(tl(l, "Yol eşleşmedi; kaldırmak için yolun üzerinde dene", "Yol tanınmadı; silmək üçün yolun üstündə yoxla"), ToastTone.ORANGE)
            return
        }
        val name = display(m.name)
        val here = env.here()
        val spots = if (here == null) emptyList() else book.clearBadSpots(m.osmId, here.lat, here.lon)
        if (spots.isNotEmpty()) {
            book.haptic(RoadHaptic.MEDIUM)
            toast(tl(l, "Kötü nokta silindi: $name", "Pis nöqtə silindi: $name"), ToastTone.GREEN)
            env.replanIfRoute()
            return
        }
        if (!book.clearBadRoad(m.osmId)) {
            toast(tl(l, "Burada kötü nokta işareti yok", "Burada pis nöqtə qeydi yoxdur"), ToastTone.ORANGE)
            return
        }
        book.haptic(RoadHaptic.MEDIUM)
        toast(tl(l, "Kötü yol işareti kaldırıldı: $name", "Pis yol qeydi silindi: $name"), ToastTone.GREEN)
        env.replanIfRoute()
    }

    private enum class StreetMark { APPROVE, STEEP, BAD_SPOT }

    private fun streetAction(mark: StreetMark) {
        val l = book.lang()
        val up = mark == StreetMark.APPROVE
        val sugg = env.suggestion()
        if (up && sugg != null && !sugg.mainRoad) {
            env.clearSuggestion()
            setStreet(sugg.osmId, display(sugg.name), mark)
            return
        }
        val m = env.match()
        if (m == null) {
            noMatch()
            return
        }
        if (up && m.cls.isMainRoad) {
            toast(tl(l, "Ana caddede ters yön onayı kapalı", "Əsas küçədə əks istiqamət təsdiqi olmur"), ToastTone.RED)
            book.haptic(RoadHaptic.VIBRATE)
            return
        }
        if (mark == StreetMark.BAD_SPOT) {
            badSpot(m.osmId, display(m.name))
            return
        }
        setStreet(m.osmId, display(m.name), mark)
    }

    /** Ses− tek basış: bulunduğun yerin ±25 m'si kötü nokta (bütün cadde değil). */
    private fun badSpot(way: Long, name: String) {
        val l = book.lang()
        val here = env.here()
        if (here == null) {
            toast(tl(l, "Konum yok — biraz daha sür", "Mövqe yoxdur — bir az da sür"), ToastTone.ORANGE)
            book.haptic(RoadHaptic.VIBRATE)
            return
        }
        book.addBadSpot(way, here.lat, here.lon)
        book.haptic(RoadHaptic.HEAVY)
        toast(tl(l, "Kötü nokta: $name · ±25 m (uzun bas: sil)", "Pis nöqtə: $name · ±25 m (uzun bas: sil)"), ToastTone.ORANGE)
        env.replanIfRoute()
    }

    private fun openQualityPrompt() {
        val m = env.match()
        if (m == null) {
            noMatch()
            return
        }
        prompt?.open(m.osmId, display(m.name))
    }

    /** Onay / eğim (Y8 `streets.*`) + "Geri al": önceki onay, kara liste, eğim ve saat izni geri yazılır. */
    private fun setStreet(way: Long, name: String, mark: StreetMark) {
        val l = book.lang()
        val streets = book.streets
        val before = streets.current
        val oldHours = before.reverseHours[way]
        val wasA = way in before.approved
        val wasB = way in before.blocked
        val wasS = way in before.steep
        if (mark == StreetMark.APPROVE) streets.approve(way, name) else streets.markSteep(way, name)
        book.streetChanged(if (mark == StreetMark.APPROVE) "approve" else "steep", way)
        book.haptic(RoadHaptic.HEAVY)
        val text = if (mark == StreetMark.APPROVE) {
            tl(l, "Onaylandı: $name (iki yön)", "Təsdiqləndi: $name (iki tərəfli)")
        } else {
            tl(l, "Aşırı eğimli: $name — yokuş yukarı kullanılmaz", "Çox dik: $name — yoxuşa çıxılmayacaq")
        }
        book.toasts.show(
            MapToast(
                text, if (mark == StreetMark.APPROVE) ToastTone.GREEN else ToastTone.PURPLE, 6,
                actionLabel = tl(l, "Geri al", "Geri qaytar"),
                onAction = {
                    streets.clearWay(way)
                    if (wasA) streets.approve(way, name)
                    if (wasB) streets.block(way, name)
                    if (wasS) streets.markSteep(way, name)
                    if (oldHours != null) streets.setHours(way, oldHours)
                    book.streetChanged("undo", way)
                    book.toasts.hide()
                    env.replanIfRoute()
                },
            ),
        )
        env.replanIfRoute()
    }

    private fun noMatch() {
        val l = book.lang()
        toast(tl(l, "Yol eşleşmedi — biraz daha sür", "Yol tanınmadı — bir az da sür"), ToastTone.ORANGE)
        book.haptic(RoadHaptic.VIBRATE)
    }

    private fun display(name: String) = name.ifEmpty { tl(book.lang(), "isimsiz yol", "adsız yol") }

    private fun toast(text: String, tone: ToastTone) = book.toasts.show(MapToast(text, tone, 4))

    companion object {
        const val DOUBLE_MS = 450L
        const val HOLD_MS = 1000L
    }
}
