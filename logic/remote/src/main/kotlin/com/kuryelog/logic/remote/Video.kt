package com.kuryelog.logic.remote

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Ayarlar (REMOTE.md 1.2–1.5); testler kısaltır. */
data class RemoteConfig(
    val port: Int = PORT,
    val askTtlMs: Long = RemoteControl.TTL_MS,
    val offerWaitMs: Long = 15_000,
    val consentWaitMs: Long = 60_000,
    val idleStopMs: Long = 10 * 60_000L,
    val authTimeoutMs: Long = 5_000,
    val deadAfterMs: Long = 5_000,
    val pingAfterMs: Long = 1_000,
    val ackEveryMs: Long = 1_000,
    val ackFastMs: Long = 50,
    val keyRequestGapMs: Long = 500,
    val reconnectMs: List<Long> = listOf(1_000, 2_000, 4_000),
    val maxSide: Int = 1280,
    /** Dokunuş (REMOTE.md 2.3): parça gelmeden bu kadar geçerse basılı parmak kalkar; bir hareket en çok [maxGestureMs]. */
    val touchStallMs: Long = 1_000,
    val maxGestureMs: Long = 30_000,
) {
    companion object {
        const val PORT = 47312
    }
}

/** Kodlama boyutu ve başlangıç bit hızı. */
data class Geometry(val width: Int, val height: Int, val bitrate: Int)

/**
 * Yakalama boyutu (REMOTE.md 1.5): en uzun kenar `min(maxSide, 1280)`'e iner, oran korunur, kenarlar 16'nın katına aşağı yuvarlanır.
 * Kodlayıcı açılamazsa [attempt] artar: 1280 → 1024 → 800 → 640; bitince null.
 */
object CaptureGeometry {
    val SIDES = listOf(1280, 1024, 800, 640)
    const val MIN_BPS = 500_000
    const val MAX_BPS = 6_000_000
    const val FPS = 30

    fun of(screenW: Int, screenH: Int, maxSide: Int, attempt: Int): Geometry? {
        if (screenW <= 0 || screenH <= 0) return null
        val cap = min(maxSide, SIDES.first())
        val sides = SIDES.filter { it <= cap }.ifEmpty { listOf(SIDES.last()) }
        val side = sides.getOrNull(attempt) ?: return null
        val scale = min(1.0, side.toDouble() / max(screenW, screenH))
        val w = floor16(screenW * scale)
        val h = floor16(screenH * scale)
        return Geometry(w, h, startBitrate(w, h))
    }

    fun startBitrate(w: Int, h: Int): Int = (0.1 * w * h * FPS).roundToInt().coerceIn(MIN_BPS, MAX_BPS)

    private fun floor16(x: Double) = max(16, (x.toInt() / 16) * 16)
}

/** H.264 Annex-B: başlangıç kodlarıyla ayrılmış NAL birimleri. */
object AnnexB {
    const val SPS = 7
    const val PPS = 8
    const val IDR = 5
    private val START = byteArrayOf(0, 0, 0, 1)

    /** NAL birimleri (başlangıç kodu olmadan). */
    fun units(b: ByteArray): List<ByteArray> {
        val starts = ArrayList<IntArray>()
        var i = 0
        while (i + 2 < b.size) {
            if (b[i].toInt() == 0 && b[i + 1].toInt() == 0) {
                if (b[i + 2].toInt() == 1) { starts += intArrayOf(i, i + 3); i += 3; continue }
                if (i + 3 < b.size && b[i + 2].toInt() == 0 && b[i + 3].toInt() == 1) { starts += intArrayOf(i, i + 4); i += 4; continue }
            }
            i++
        }
        return starts.mapIndexedNotNull { k, s ->
            val end = if (k + 1 < starts.size) starts[k + 1][0] else b.size
            if (end > s[1]) b.copyOfRange(s[1], end) else null
        }
    }

    fun type(nal: ByteArray): Int = if (nal.isEmpty()) -1 else nal[0].toInt() and 0x1F

    /** Kodlayıcının yapılandırma tamponundan `csd-0` (SPS) ve `csd-1` (PPS), başlangıç koduyla; biri yoksa null. */
    fun parameterSets(csd: ByteArray): Pair<ByteArray, ByteArray>? {
        val u = units(csd)
        val sps = u.firstOrNull { type(it) == SPS } ?: return null
        val pps = u.firstOrNull { type(it) == PPS } ?: return null
        return (START + sps) to (START + pps)
    }
}

/**
 * Paylaşanda akış denetimi (REMOTE.md 1.5). Yolda = yollanan kare baytı − izleyenin `ACK`'teki alınan baytı. Sınırı aşınca P kare
 * atılır ve anahtar kare istenir; anahtar kare yollanana dek P kare gitmez. Atma olunca bit hızı ×0,7 (en çok 1 sn'de bir); 3 sn
 * atmasız geçince +250 kb/sn.
 */
class FlowControl(startBitrate: Int, private val minBps: Int = CaptureGeometry.MIN_BPS, private val maxBps: Int = CaptureGeometry.MAX_BPS) {
    class Decision(val send: Boolean, val requestKey: Boolean, val bitrate: Int?)

    var bitrate: Int = startBitrate.coerceIn(minBps, maxBps)
        private set
    private var sentBytes = 0L
    private var ackedBytes = 0L
    private var waitingKey = false
    private var lastDropMs = Long.MIN_VALUE / 2
    private var lastDecreaseMs = Long.MIN_VALUE / 2
    private var lastIncreaseMs = Long.MIN_VALUE / 2
    private var lastKeyRequestMs = Long.MIN_VALUE / 2

    /** Atılan kare sayısı (tanı). */
    var dropped = 0L
        private set

    val inFlight: Long get() = sentBytes - ackedBytes
    val limit: Long get() = max(MIN_WINDOW, bitrate / 8L / 2)

    /** Yeni akış: sayaçlar sıfır, ilk kare anahtar kare olmalı. */
    fun reset(now: Long) {
        sentBytes = 0
        ackedBytes = 0
        waitingKey = true
        lastDropMs = now
        lastIncreaseMs = now
    }

    /**
     * Aynı bağlantıda yeni kodlayıcı (döndürme, kodlayıcı hatası): ilk kare anahtar kare olmalı. Bayt sayaçları bağlantınındır,
     * sıfırlanmaz; izleyenin `ACK`'teki toplamı bağlantı açıldığından beri sayar.
     */
    fun restart() {
        waitingKey = true
    }

    fun admit(key: Boolean, bytes: Int, now: Long): Decision {
        val congested = inFlight + bytes > limit && inFlight > 0
        if (!congested && (key || !waitingKey)) {
            if (key) waitingKey = false
            sentBytes += bytes
            return Decision(true, false, null)
        }
        dropped++
        var req = false
        if (!waitingKey || now - lastKeyRequestMs >= KEY_RETRY_MS) {
            req = true
            lastKeyRequestMs = now
        }
        waitingKey = true
        var newRate: Int? = null
        if (congested) {
            lastDropMs = now
            if (now - lastDecreaseMs >= DECREASE_GAP_MS) {
                val r = max(minBps, (bitrate * 7L / 10).toInt())
                lastDecreaseMs = now
                if (r != bitrate) { bitrate = r; newRate = r }
            }
        }
        return Decision(false, req, newRate)
    }

    fun ack(receivedBytes: Long) {
        if (receivedBytes in ackedBytes..sentBytes) ackedBytes = receivedBytes
    }

    /** Artış zamanı geldiyse yeni bit hızı. */
    fun tick(now: Long): Int? {
        if (now - lastDropMs < INCREASE_AFTER_MS || now - lastIncreaseMs < INCREASE_AFTER_MS || bitrate >= maxBps) return null
        lastIncreaseMs = now
        bitrate = min(maxBps, bitrate + STEP_BPS)
        return bitrate
    }

    companion object {
        const val MIN_WINDOW = 256 * 1024L
        const val DECREASE_GAP_MS = 1_000L
        const val INCREASE_AFTER_MS = 3_000L
        const val STEP_BPS = 250_000
        const val KEY_RETRY_MS = 1_000L
    }
}
