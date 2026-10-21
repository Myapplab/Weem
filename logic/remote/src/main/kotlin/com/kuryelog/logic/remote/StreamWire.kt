package com.kuryelog.logic.remote

import java.nio.ByteBuffer
import java.util.UUID

/** Akışı kapatan hata (REMOTE.md 1.4): bozuk çerçeve, yanlış oturum, çözülemeyen ileti, zaman aşımı. */
class StreamException(val reason: CloseReason, message: String) : Exception(message)

/** Akışın kapanma nedeni (yalnız kod içi ve sayaç; görüntü verisi taşımaz). */
enum class CloseReason { PROTOCOL, SESSION, AUTH, TIMEOUT, REPLACED, ENDED, PEER }

/** Akış çerçevesi: `[uzunluk u32 BE = 1 + gövde][tür u8][gövde]` (REMOTE.md 1.4). */
object StreamFrames {
    const val HELLO = 1
    const val SEALED = 2
    const val MAX_LEN = 4 * 1024 * 1024

    class Frame(val type: Int, val body: ByteArray)

    fun encode(type: Int, body: ByteArray): ByteArray {
        require(body.size + 1 <= MAX_LEN) { "frame too big" }
        return ByteBuffer.allocate(5 + body.size).putInt(body.size + 1).put(type.toByte()).put(body).array()
    }

    /** Parça parça gelen baytları çerçevelere böler; bozuk uzunlukta ya da bilinmeyen türde [StreamException]. */
    class Decoder {
        private var buf = ByteArray(64 * 1024)
        private var start = 0
        private var end = 0

        fun feed(bytes: ByteArray, off: Int = 0, len: Int = bytes.size): List<Frame> {
            append(bytes, off, len)
            val out = ArrayList<Frame>(2)
            while (end - start >= 4) {
                val n = ByteBuffer.wrap(buf, start, 4).int
                if (n < 1 || n > MAX_LEN) throw StreamException(CloseReason.PROTOCOL, "bad length $n")
                if (end - start - 4 < n) break
                val type = buf[start + 4].toInt() and 0xFF
                if (type != HELLO && type != SEALED) throw StreamException(CloseReason.PROTOCOL, "bad type $type")
                out += Frame(type, buf.copyOfRange(start + 5, start + 4 + n))
                start += 4 + n
            }
            if (start == end) { start = 0; end = 0 }
            return out
        }

        private fun append(b: ByteArray, off: Int, len: Int) {
            if (end + len > buf.size) {
                val live = end - start
                val need = live + len
                if (need > buf.size || start == 0) {
                    val grown = ByteArray(maxOf(buf.size * 2, need))
                    System.arraycopy(buf, start, grown, 0, live)
                    buf = grown
                } else {
                    System.arraycopy(buf, start, buf, 0, live)
                }
                start = 0
                end = live
            }
            System.arraycopy(b, off, buf, end, len)
            end += len
        }
    }
}

/** Düz el sıkışma iletisi: `WRS1` · oturum 16 · nonce 16 (36 bayt). */
class StreamHello(val session: UUID, nonce: ByteArray) {
    val nonce: ByteArray = nonce.copyOf()

    init { require(nonce.size == NONCE) }

    fun encode(): ByteArray = ByteBuffer.allocate(SIZE).put(MAGIC).putLong(session.mostSignificantBits).putLong(session.leastSignificantBits).put(nonce).array()

    companion object {
        const val NONCE = 16
        const val SIZE = 4 + 16 + NONCE
        private val MAGIC = "WRS1".toByteArray(Charsets.US_ASCII)

        fun decode(b: ByteArray): StreamHello? {
            if (b.size != SIZE || !b.copyOfRange(0, 4).contentEquals(MAGIC)) return null
            val bb = ByteBuffer.wrap(b, 4, 16)
            return StreamHello(UUID(bb.long, bb.long), b.copyOfRange(20, SIZE))
        }
    }
}

/**
 * Şifreli iç iletiler (`tür u8 · gövde`; REMOTE.md 1.4, 2.2). Paylaşan → izleyen: [Config], [Frame], [Ping], [Bye], [Control];
 * izleyen → paylaşan: [Ack], [KeyRequest], [Bye], [Touch], [Action]. Bilinmeyen tür [Unknown] olur (yeni sürüm iletisi; A9.1 sürümü
 * A9.2'nin dokunuşunu böyle yok sayar).
 */
sealed interface StreamMsg {
    class Config(val width: Int, val height: Int, val csd: ByteArray) : StreamMsg
    class Frame(val seq: Long, val ptsUs: Long, val key: Boolean, val data: ByteArray) : StreamMsg
    object Ping : StreamMsg
    class Bye(val reason: Int) : StreamMsg
    class Ack(val seq: Long, val bytes: Long) : StreamMsg
    object KeyRequest : StreamMsg
    /** Paylaşanda dokunuş servisi açık mı (izleyen düğmeleri buna göre gösterir). */
    class Control(val touch: Boolean) : StreamMsg
    class Touch(val part: TouchPart) : StreamMsg
    /** Sistem tuşu; [code] [GlobalAction] (bilinmeyen kod paylaşanda yok sayılır). */
    class Action(val code: Int) : StreamMsg
    class Unknown(val type: Int) : StreamMsg

    companion object {
        const val CONFIG = 10
        const val FRAME = 11
        const val PING = 12
        const val BYE = 13
        const val CONTROL = 14
        const val ACK = 20
        const val KEY = 21
        const val VIEWER_BYE = 22
        const val TOUCH = 23
        const val ACTION = 24

        /** [Bye] nedenleri. */
        const val BYE_ENDED = 1
        const val BYE_REPLACED = 2
        const val BYE_ERROR = 3

        /** [fromHost]: paylaşanın `BYE`'ı 13, izleyeninki 22 (yön karışmasın). */
        fun encode(m: StreamMsg, fromHost: Boolean): ByteArray = when (m) {
            is Config -> ByteBuffer.allocate(5 + m.csd.size).put(CONFIG.toByte()).putShort(m.width.toShort()).putShort(m.height.toShort()).put(m.csd).array()
            is Frame -> ByteBuffer.allocate(14 + m.data.size).put(FRAME.toByte()).putInt(m.seq.toInt()).putLong(m.ptsUs)
                .put(if (m.key) 1 else 0).put(m.data).array()
            Ping -> byteArrayOf(PING.toByte())
            is Bye -> byteArrayOf((if (fromHost) BYE else VIEWER_BYE).toByte(), m.reason.toByte())
            is Ack -> ByteBuffer.allocate(13).put(ACK.toByte()).putInt(m.seq.toInt()).putLong(m.bytes).array()
            KeyRequest -> byteArrayOf(KEY.toByte())
            is Control -> byteArrayOf(CONTROL.toByte(), (if (m.touch) 1 else 0).toByte())
            is Touch -> encodeTouch(m.part)
            is Action -> byteArrayOf(ACTION.toByte(), m.code.toByte())
            is Unknown -> byteArrayOf(m.type.toByte())
        }

        /** Bozuk gövdede null (akış kapanır); [fromHost] = ileti paylaşandan mı geldi (yöne uymayan tür bozuk sayılır). */
        fun decode(b: ByteArray, fromHost: Boolean): StreamMsg? {
            if (b.isEmpty()) return null
            val t = b[0].toInt() and 0xFF
            val bb = ByteBuffer.wrap(b, 1, b.size - 1)
            return when {
                fromHost && t == CONFIG -> if (b.size < 5) null else {
                    val w = bb.short.toInt() and 0xFFFF
                    val h = bb.short.toInt() and 0xFFFF
                    if (w == 0 || h == 0) null else Config(w, h, b.copyOfRange(5, b.size))
                }
                fromHost && t == FRAME -> if (b.size < 14) null else {
                    val seq = bb.int.toLong() and 0xFFFFFFFFL
                    val pts = bb.long
                    Frame(seq, pts, (bb.get().toInt() and 1) == 1, b.copyOfRange(14, b.size))
                }
                fromHost && t == PING -> if (b.size == 1) Ping else null
                fromHost && t == BYE || !fromHost && t == VIEWER_BYE -> if (b.size == 2) Bye(b[1].toInt() and 0xFF) else null
                !fromHost && t == ACK -> if (b.size != 13) null else Ack(bb.int.toLong() and 0xFFFFFFFFL, bb.long)
                !fromHost && t == KEY -> if (b.size == 1) KeyRequest else null
                fromHost && t == CONTROL -> if (b.size == 2) Control((b[1].toInt() and 1) == 1) else null
                !fromHost && t == TOUCH -> decodeTouch(bb)?.let(::Touch)
                !fromHost && t == ACTION -> if (b.size == 2) Action(b[1].toInt() and 0xFF) else null
                t in KNOWN -> null
                else -> Unknown(t)
            }
        }

        private val KNOWN = setOf(CONFIG, FRAME, PING, BYE, CONTROL, ACK, KEY, VIEWER_BYE, TOUCH, ACTION)

        /**
         * `TOUCH` (REMOTE.md 2.2): hareket u16 · parça u16 · görüntü G u16 · Y u16 · süre ms u16 · çizgi sayısı u8, her çizgi:
         * kimlik u8 · bayrak u8 (bit 0 başlar, bit 1 biter) · nokta sayısı u8 · noktalar (x u16, y u16, t u16).
         */
        private fun encodeTouch(p: TouchPart): ByteArray {
            val size = 1 + 11 + p.strokes.sumOf { 3 + 6 * it.points.size }
            val bb = ByteBuffer.allocate(size).put(TOUCH.toByte()).putShort(p.gesture.toShort()).putShort(p.index.toShort())
                .putShort(p.frameW.toShort()).putShort(p.frameH.toShort()).putShort(p.durationMs.toShort()).put(p.strokes.size.toByte())
            for (s in p.strokes) {
                bb.put(s.id.toByte()).put(((if (s.begins) 1 else 0) or (if (s.ends) 2 else 0)).toByte()).put(s.points.size.toByte())
                for (pt in s.points) bb.putShort(pt.x.toShort()).putShort(pt.y.toShort()).putShort(pt.t.toShort())
            }
            return bb.array()
        }

        /** Yapı bozuksa null (anlam denetimi paylaşanda, [TouchPlayer]). */
        private fun decodeTouch(bb: ByteBuffer): TouchPart? {
            if (bb.remaining() < 11) return null
            fun u16() = bb.short.toInt() and 0xFFFF
            fun u8() = bb.get().toInt() and 0xFF
            val gesture = u16()
            val index = u16()
            val w = u16()
            val h = u16()
            val duration = u16()
            val n = u8()
            if (n == 0 || n > TouchLimits.MAX_POINTERS) return null
            val strokes = ArrayList<TouchStroke>(n)
            repeat(n) {
                if (bb.remaining() < 3) return null
                val id = u8()
                val flags = u8()
                val count = u8()
                if (count == 0 || count > TouchLimits.MAX_POINTS || bb.remaining() < 6 * count) return null
                val pts = List(count) { TouchPoint(u16(), u16(), u16()) }
                strokes += TouchStroke(id, flags and 1 != 0, flags and 2 != 0, pts)
            }
            if (bb.hasRemaining()) return null
            return TouchPart(gesture, index, w, h, duration, strokes)
        }
    }
}
