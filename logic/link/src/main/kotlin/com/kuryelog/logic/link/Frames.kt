package com.kuryelog.logic.link

import java.nio.ByteBuffer
import java.util.UUID

/** Telefon kimliği (16 bayt UUID; bugün `DeviceId`). Sıralama küçük harfli metne göre: iki telefon aynı sonuca varır. */
class PeerId private constructor(val text: String) : Comparable<PeerId> {
    fun bytes(): ByteArray {
        val u = UUID.fromString(text)
        return ByteBuffer.allocate(16).putLong(u.mostSignificantBits).putLong(u.leastSignificantBits).array()
    }

    override fun compareTo(other: PeerId) = text.compareTo(other.text)
    override fun equals(other: Any?) = other is PeerId && other.text == text
    override fun hashCode() = text.hashCode()
    override fun toString() = text

    companion object {
        private val pattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        /** Geçerli UUID metni değilse `null`. */
        fun of(text: String?): PeerId? = text?.trim()?.lowercase()?.takeIf { pattern.matches(it) }?.let(::PeerId)

        fun fromBytes(b: ByteArray, offset: Int): PeerId {
            val buf = ByteBuffer.wrap(b, offset, 16)
            return PeerId(UUID(buf.long, buf.long).toString())
        }
    }
}

enum class FrameType(val code: Int) {
    HELLO(1), DATA(2), PING(3), PONG(4), BYE(5), NOISE(6);

    companion object {
        fun of(code: Int): FrameType? = entries.firstOrNull { it.code == code }
    }
}

/** `BYE` nedeni (LINK.md 1.4, 3.4: 8 eşleştirme modu uyuşmuyor, 9 kimlik doğrulanamadı). */
enum class ByeReason(val code: Int) {
    DUPLICATE(1), MOVED_TO_WIFI(2), SHUTDOWN(3), PROTOCOL(4), WRONG_PEER(5), VERSION(6), TIMEOUT(7), PAIRING(8), AUTH(9);

    companion object {
        fun of(code: Int): ByeReason? = entries.firstOrNull { it.code == code }
    }
}

class Frame(val type: FrameType, val payload: ByteArray) {
    override fun toString() = "Frame($type, ${payload.size} B)"
}

/**
 * Çerçeve (LINK.md 1.4): `[uzunluk u32 BE = 1 + yük][tür u8][yük]`.
 * `HELLO` yükü (v2, LINK.md 3.4): `WEEM` · bağlantı sürümü · kimlik 16 bayt · bayrak (bit 0 = eşleştirme). `PING`/`PONG`: 8 bayt.
 * `BYE`: 1 bayt neden. `NOISE`: el sıkışma iletisi. Açık kanalda `DATA` şifreli (3.5).
 */
object Frames {
    const val HEADER = 5
    const val LINK_VERSION = 2
    val MAGIC = byteArrayOf('W'.code.toByte(), 'E'.code.toByte(), 'E'.code.toByte(), 'M'.code.toByte())
    private const val HELLO_SIZE = 4 + 1 + 16 + 1
    private const val FLAG_PAIRING = 1

    fun encode(type: FrameType, payload: ByteArray = ByteArray(0)): ByteArray {
        val out = ByteBuffer.allocate(HEADER + payload.size)
        out.putInt(1 + payload.size).put(type.code.toByte()).put(payload)
        return out.array()
    }

    /** `HELLO` yükü; el sıkışmanın prologue'una da girer. */
    fun helloPayload(self: PeerId, pairing: Boolean): ByteArray =
        ByteBuffer.allocate(HELLO_SIZE).put(MAGIC).put(LINK_VERSION.toByte()).put(self.bytes())
            .put((if (pairing) FLAG_PAIRING else 0).toByte()).array()

    fun hello(self: PeerId, pairing: Boolean): ByteArray = encode(FrameType.HELLO, helloPayload(self, pairing))

    fun ping(monoMs: Long): ByteArray = encode(FrameType.PING, ByteBuffer.allocate(8).putLong(monoMs).array())
    fun pong(echo: ByteArray): ByteArray = encode(FrameType.PONG, echo)
    fun bye(reason: ByeReason): ByteArray = encode(FrameType.BYE, byteArrayOf(reason.code.toByte()))

    /** [peer] yalnız bu bağlantı sürümünde okunur; başka sürümde `null` (çağıran `BYE(6)` der). */
    class Hello(val version: Int, val peer: PeerId?, val pairing: Boolean)

    /** Sihirli baytlar, boy ya da bilinmeyen bayrak tutmazsa `null` (protokol hatası). Sürüm denetimi çağıranda. */
    fun parseHello(p: ByteArray): Hello? {
        if (p.size < 5) return null
        for (i in MAGIC.indices) if (p[i] != MAGIC[i]) return null
        val version = p[4].toInt() and 0xff
        if (version != LINK_VERSION) return Hello(version, null, false)
        if (p.size != HELLO_SIZE) return null
        val flags = p[21].toInt() and 0xff
        if (flags and FLAG_PAIRING.inv() != 0) return null
        return Hello(version, PeerId.fromBytes(p, 5), flags and FLAG_PAIRING != 0)
    }

    /** `PING`/`PONG` yükündeki monoton ms; boy tutmazsa `null`. */
    fun stamp(p: ByteArray): Long? = if (p.size == 8) ByteBuffer.wrap(p).long else null

    fun byeReason(p: ByteArray): ByeReason? = if (p.size == 1) ByeReason.of(p[0].toInt() and 0xff) else null
}

/**
 * Akış çözücü: TCP/RFCOMM parça parça ya da birleşik bayt verir; tam çerçeveleri sırayla çıkarır.
 * Bozuk uzunluk (0 ya da [maxPayload] üstü) ya da bilinmeyen tür → [error] dolar, sonraki girdiler yok sayılır (kanal kapanmalı).
 */
class FrameDecoder(private val maxPayload: Int) {
    private var buf = ByteArray(256)
    private var start = 0
    private var end = 0
    var error: String? = null
        private set

    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): List<Frame> {
        if (error != null || length <= 0) return emptyList()
        ensure(length)
        System.arraycopy(data, offset, buf, end, length)
        end += length
        val out = ArrayList<Frame>()
        while (end - start >= 4) {
            val len = ByteBuffer.wrap(buf, start, 4).int
            if (len < 1 || len - 1 > maxPayload) {
                error = "bad_length:$len"
                return out
            }
            if (end - start < 4 + len) break
            val type = FrameType.of(buf[start + 4].toInt() and 0xff)
            if (type == null) {
                error = "bad_type:${buf[start + 4].toInt() and 0xff}"
                return out
            }
            out += Frame(type, buf.copyOfRange(start + HEADER_TYPE_END, start + 4 + len))
            start += 4 + len
        }
        if (start == end) {
            start = 0
            end = 0
        }
        return out
    }

    private fun ensure(more: Int) {
        if (end + more <= buf.size) return
        val live = end - start
        val need = live + more
        val next = if (need <= buf.size) buf else ByteArray(maxOf(need, buf.size * 2))
        System.arraycopy(buf, start, next, 0, live)
        buf = next
        start = 0
        end = live
    }

    private companion object {
        const val HEADER_TYPE_END = Frames.HEADER
    }
}
