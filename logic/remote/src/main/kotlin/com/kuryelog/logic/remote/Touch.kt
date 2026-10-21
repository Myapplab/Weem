package com.kuryelog.logic.remote

import kotlin.math.roundToInt

/**
 * Uzaktan dokunuş (REMOTE.md 2): izleyen, öteki telefonun görüntüsüne dokunur; dokunuş **parçalar** hâlinde (en çok
 * [TouchLimits.FLUSH_MS] aralıkla) akış üstünden gider; paylaşan onu ayrı erişilebilirlik servisiyle (`TouchService`) yeniden
 * oynatır. Konumlar görüntüye göre 0..[TouchLimits.NORM] (çözünürlükten bağımsız); zaman parçanın başından ms.
 */
object TouchLimits {
    const val NORM = 65535
    /** Aynı anda en çok parmak (dokunma, kaydırma, iki parmakla yakınlaştırma). */
    const val MAX_POINTERS = 2
    /** Parça başına parmak başına en çok nokta. */
    const val MAX_POINTS = 64
    /** Bir parça en çok bu kadar sürebilir (daha uzunu bozuk sayılır). */
    const val MAX_PART_MS = 1_000
    /** İzleyen, parça başından bu kadar geçince parçayı yollar (parmak hâlâ ekranda olsa da). */
    const val FLUSH_MS = 100
    /** Noktalar arasında en az (hızlı hareketlerde daha sık örnek gerekmez). */
    const val MIN_SAMPLE_MS = 8
    /** Tek parçada başlayıp biten (dokunma) çizginin en kısa süresi: çok kısa basışı bazı uygulamalar saymaz. */
    const val MIN_TAP_MS = 20
}

/** Görüntüye göre nokta: [x], [y] 0..NORM; [t] parça başından ms. */
data class TouchPoint(val x: Int, val y: Int, val t: Int)

/**
 * Bir parmağın bu parçadaki izi. [begins]: parmak bu parçada değdi; değilse önceki parçadan sürer ve ilk noktası `t = 0`'dadır.
 * [ends]: parmak bu parçada kalktı.
 */
data class TouchStroke(val id: Int, val begins: Boolean, val ends: Boolean, val points: List<TouchPoint>)

/** Bir hareketin ([gesture]) [index]. parçası; [frameW]×[frameH] izleyenin o an gördüğü görüntü boyutu. */
data class TouchPart(val gesture: Int, val index: Int, val frameW: Int, val frameH: Int, val durationMs: Int, val strokes: List<TouchStroke>)

/** Sistem tuşları (`performGlobalAction`). Bilinmeyen kod yok sayılır (yeni sürüm). */
enum class GlobalAction(val code: Int) {
    BACK(1), HOME(2), RECENTS(3), NOTIFICATIONS(4);

    companion object {
        fun of(code: Int): GlobalAction? = entries.firstOrNull { it.code == code }
    }
}

/**
 * İzleyende dokunuş kaydı (saf): ekrandaki parmak olayları → [TouchPart]. Zamanlar tek saatten (ms, monoton); konumlar 0..NORM.
 * Parça, başından [TouchLimits.FLUSH_MS] geçince (bir olayda ya da [tick]'te) ya da son parmak kalkınca çıkar. Parmak kıpırdamadan
 * dururken de parça çıkar (paylaşan basılı tutmayı sürdürsün).
 */
class TouchRecorder(private val flushMs: Int = TouchLimits.FLUSH_MS) {
    private class Track(val id: Int, var begins: Boolean) {
        val points = ArrayList<TouchPoint>()
        var pending: TouchPoint? = null
        var ends = false
        var lastX = 0
        var lastY = 0
    }

    private var gesture = 0
    private var index = 0
    private var active = false
    private var partStartMs = 0L
    private var frameW = 0
    private var frameH = 0
    private val tracks = LinkedHashMap<Int, Track>()
    private val ignored = HashSet<Int>()

    val inGesture: Boolean get() = active

    /** Parmak değdi; [frameW]×[frameH] o an görülen görüntü (yalnız hareketin ilk parmağında alınır). */
    fun down(id: Int, x: Int, y: Int, atMs: Long, frameW: Int, frameH: Int): List<TouchPart> {
        val out = ArrayList<TouchPart>(1)
        if (active) {
            flushIfDue(atMs, out)
            // Bu parçada kalkmış parmak yer tutuyor (aynı kimlik yeniden ya da sınır dolu): parça şimdi kapanır.
            if (tracks[id]?.ends == true || tracks.size >= TouchLimits.MAX_POINTERS && tracks.values.any { it.ends }) flush(atMs, out)
        }
        if (!active) {
            active = true
            gesture = (gesture + 1) and 0xFFFF
            index = 0
            partStartMs = atMs
            this.frameW = frameW
            this.frameH = frameH
            tracks.clear()
            ignored.clear()
        }
        if (id in tracks || id in ignored) return out
        if (tracks.size >= TouchLimits.MAX_POINTERS) {
            ignored += id
            return out
        }
        val t = Track(id, begins = true)
        tracks[id] = t
        add(t, x, y, atMs, exact = true)
        return out
    }

    fun move(id: Int, x: Int, y: Int, atMs: Long): List<TouchPart> {
        val out = ArrayList<TouchPart>(1)
        if (!active) return out
        // Nokta önce biten parçaya girer (parça sonu = bu an), sürdüren parça aynı noktadan başlar.
        tracks[id]?.takeIf { !it.ends }?.let { add(it, x, y, atMs, exact = false) }
        flushIfDue(atMs, out)
        return out
    }

    fun up(id: Int, x: Int, y: Int, atMs: Long): List<TouchPart> {
        val out = ArrayList<TouchPart>(1)
        if (!active) return out
        val t = tracks[id]?.takeIf { !it.ends }
        if (t == null) ignored -= id else {
            add(t, x, y, atMs, exact = true)
            t.ends = true
        }
        if (tracks.values.all { it.ends }) flush(atMs, out) else flushIfDue(atMs, out)
        return out
    }

    /** Sistem hareketi kesti (`ACTION_CANCEL`): bütün parmaklar son konumlarında kalkar. */
    fun cancel(atMs: Long): List<TouchPart> {
        val out = ArrayList<TouchPart>(1)
        if (!active) return out
        for (t in tracks.values) if (!t.ends) { add(t, t.lastX, t.lastY, atMs, exact = true); t.ends = true }
        flush(atMs, out)
        return out
    }

    fun tick(nowMs: Long): List<TouchPart> {
        val out = ArrayList<TouchPart>(1)
        if (active) flushIfDue(nowMs, out)
        return out
    }

    /** Akış koptu ya da denetim kapandı: kayıt atılır (paylaşan kendi tarafında parmakları kaldırır). */
    fun reset() {
        active = false
        tracks.clear()
        ignored.clear()
    }

    private fun rel(atMs: Long) = (atMs - partStartMs).coerceIn(0, TouchLimits.MAX_PART_MS.toLong()).toInt()

    private fun add(t: Track, x: Int, y: Int, atMs: Long, exact: Boolean) {
        val p = TouchPoint(x.coerceIn(0, TouchLimits.NORM), y.coerceIn(0, TouchLimits.NORM), rel(atMs))
        t.lastX = p.x
        t.lastY = p.y
        val last = t.points.lastOrNull()
        if (!exact && last != null && p.t - last.t < TouchLimits.MIN_SAMPLE_MS) {
            t.pending = p
            return
        }
        t.pending = null
        append(t, p)
    }

    private fun append(t: Track, p: TouchPoint) {
        if (t.points.size >= TouchLimits.MAX_POINTS) t.points[t.points.size - 1] = p else t.points += p
    }

    private fun flushIfDue(atMs: Long, out: MutableList<TouchPart>) {
        if (atMs - partStartMs >= flushMs) flush(atMs, out)
    }

    private fun flush(atMs: Long, out: MutableList<TouchPart>) {
        val duration = (atMs - partStartMs).coerceIn(1, TouchLimits.MAX_PART_MS.toLong()).toInt()
        val strokes = ArrayList<TouchStroke>(tracks.size)
        for (t in tracks.values) {
            t.pending?.let { append(t, it); t.pending = null }
            // Kalkmamış parmak parçanın sonuna dek yerinde durur.
            if (!t.ends && t.points.last().t < duration) append(t, TouchPoint(t.lastX, t.lastY, duration))
            strokes += TouchStroke(t.id, t.begins, t.ends, ArrayList(t.points))
        }
        out += TouchPart(gesture, index, frameW, frameH, duration, strokes)
        index = (index + 1) and 0xFFFF
        partStartMs = atMs
        tracks.values.removeAll { it.ends }
        for (t in tracks.values) {
            t.begins = false
            t.points.clear()
            t.points += TouchPoint(t.lastX, t.lastY, 0)
        }
        if (tracks.isEmpty()) {
            active = false
            ignored.clear()
        }
    }
}

/** Paylaşanın ekranında piksel nokta. */
data class Px(val x: Int, val y: Int)

/**
 * Yeniden oynatılacak çizgi (`GestureDescription.StrokeDescription`): [continues] önceki parçadaki aynı [id]'li çizgiyi sürdürür
 * (`continueStroke`), [willContinue] parmak parça sonunda kalkmaz.
 */
data class GestureStroke(val id: Int, val points: List<Px>, val startMs: Int, val durationMs: Int, val continues: Boolean, val willContinue: Boolean)

/** Tek `dispatchGesture` çağrısı; Android katmanı parçaları sırayla (biri bitince öteki) oynatır. */
data class GesturePart(val strokes: List<GestureStroke>)

/**
 * Paylaşanda dokunuş denetimi (saf): gelen [TouchPart]'ı doğrular, ekran piksellerine çevirir, sürekliliği tutar. Bozuk, sırası
 * kaçmış ya da yön değişmiş (döndürme) parçada hareket bırakılır ve basılı parmaklar kaldırılır; parça gelmeden [stallMs] geçerse ya
 * da hareket [maxGestureMs]'yi aşarsa da. WEEM kendiliğinden dokunmaz: yalnız gelen parçalar ve onları bitiren kaldırma.
 */
class TouchPlayer(private val stallMs: Long = 1_000, private val maxGestureMs: Long = 30_000) {
    private var gesture: Int? = null
    private var expected = 0
    private var dropped: Int? = null
    private var startedMs = 0L
    private var lastPartMs = 0L
    private val held = LinkedHashMap<Int, Px>()

    val holding: Boolean get() = held.isNotEmpty()

    fun part(p: TouchPart, screenW: Int, screenH: Int, nowMs: Long): List<GesturePart> {
        val out = ArrayList<GesturePart>(2)
        if (p.gesture == dropped) return out
        if (p.gesture != gesture) {
            lift(out)
            gesture = null
            if (p.index != 0) { dropped = p.gesture; return out }
            gesture = p.gesture
            expected = 0
            startedMs = nowMs
        }
        if (p.index != expected || !valid(p, screenW, screenH) || nowMs - startedMs > maxGestureMs) {
            drop(p.gesture, out)
            return out
        }
        expected = (expected + 1) and 0xFFFF
        lastPartMs = nowMs
        val strokes = ArrayList<GestureStroke>(p.strokes.size)
        for (s in p.strokes) {
            val pts = s.points.map { map(it, screenW, screenH) }.toMutableList()
            if (!s.begins) pts[0] = held.getValue(s.id)
            val first = s.points.first().t
            val span = s.points.last().t - first
            val min = if (s.begins && s.ends) TouchLimits.MIN_TAP_MS else 1
            strokes += GestureStroke(s.id, pts, first, span.coerceAtLeast(min), continues = !s.begins, willContinue = !s.ends)
            if (s.ends) held -= s.id else held[s.id] = pts.last()
        }
        out += GesturePart(strokes)
        if (held.isEmpty()) gesture = null
        return out
    }

    fun tick(nowMs: Long): List<GesturePart> {
        val out = ArrayList<GesturePart>(1)
        if (held.isNotEmpty() && (nowMs - lastPartMs >= stallMs || nowMs - startedMs > maxGestureMs)) gesture?.let { drop(it, out) }
        return out
    }

    /** Akış bitti / dokunuş kapandı: basılı parmaklar kalkar. */
    fun reset(): List<GesturePart> {
        val out = ArrayList<GesturePart>(1)
        lift(out)
        gesture = null
        dropped = null
        return out
    }

    private fun drop(g: Int, out: MutableList<GesturePart>) {
        lift(out)
        gesture = null
        dropped = g
    }

    private fun lift(out: MutableList<GesturePart>) {
        if (held.isEmpty()) return
        out += GesturePart(held.map { (id, at) -> GestureStroke(id, listOf(at), 0, 1, continues = true, willContinue = false) })
        held.clear()
    }

    private fun valid(p: TouchPart, w: Int, h: Int): Boolean {
        if (w <= 0 || h <= 0 || p.frameW <= 0 || p.frameH <= 0) return false
        // Görüntü öteki yöndeyse (döndürme sürerken) konumlar yanlış yere düşer.
        if (p.frameW != p.frameH && w != h && (p.frameW > p.frameH) != (w > h)) return false
        if (p.durationMs !in 1..TouchLimits.MAX_PART_MS || p.strokes.isEmpty() || p.strokes.size > TouchLimits.MAX_POINTERS) return false
        if (p.strokes.map { it.id }.toSet().size != p.strokes.size) return false
        // Basılı her parmak bu parçada sürmeli; yeni parmaklarla birlikte en çok MAX_POINTERS.
        val ids = p.strokes.map { it.id }.toSet()
        if (!ids.containsAll(held.keys)) return false
        for (s in p.strokes) {
            if (s.points.isEmpty() || s.points.size > TouchLimits.MAX_POINTS) return false
            if (s.begins == (s.id in held)) return false
            if (!s.begins && s.points.first().t != 0) return false
            if (s.points.zipWithNext().any { (a, b) -> b.t < a.t }) return false
            if (s.points.any { it.t !in 0..p.durationMs || it.x !in 0..TouchLimits.NORM || it.y !in 0..TouchLimits.NORM }) return false
        }
        return true
    }

    private fun map(p: TouchPoint, w: Int, h: Int) =
        Px((p.x.toDouble() * (w - 1) / TouchLimits.NORM).roundToInt(), (p.y.toDouble() * (h - 1) / TouchLimits.NORM).roundToInt())
}
