package com.kuryelog.logic.link

import java.nio.ByteBuffer
import java.util.UUID

/** Zarf sınıfı (LINK.md 3.6): alıcı türü tanımasa da onaylar ya da birleştirir. */
enum class EnvelopeKind(val code: Int) {
    /** Son durum: tür başına en yenisi, TTL; onay yok. */
    LATEST(1),

    /** Teslim zorunlu: kalıcı kuyruk, onaya kadar yeniden gönderim, alıcı `id` ile tekrarı atar. */
    RELIABLE(2),

    /** `link.ack`: teslim zorunlu `id`'leri. */
    ACK(3),

    /** Bu katmanın denetim iletileri: `link.time`, `link.unpair`. */
    CONTROL(4);

    companion object {
        fun of(code: Int): EnvelopeKind? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Zarf v1 (LINK.md 3.6), büyük uçlu: `v u8 · sınıf u8 · ad boyu u8 · ad (ASCII [A-Za-z0-9._-], 1–48) · id 16 · seq u64 ·
 * sentAtMs i64 (gönderenin duvar saati) · ttlMs u32 · yük`. Yük opak (mesaj türleri A8.3'te). `toString` yükü yazmaz.
 */
class Envelope(
    val kind: EnvelopeKind,
    val type: String,
    val id: UUID,
    val seq: Long,
    val sentAtMs: Long,
    val ttlMs: Long,
    payload: ByteArray,
) {
    private val body = payload.copyOf()

    init {
        require(TYPE.matches(type)) { "envelope type" }
        require(ttlMs in 0..MAX_TTL) { "ttl" }
    }

    val payload: ByteArray get() = body.copyOf()
    val payloadSize: Int get() = body.size

    fun encode(): ByteArray {
        val t = type.toByteArray(Charsets.US_ASCII)
        return ByteBuffer.allocate(FIXED + t.size + body.size)
            .put(VERSION.toByte()).put(kind.code.toByte()).put(t.size.toByte()).put(t)
            .putLong(id.mostSignificantBits).putLong(id.leastSignificantBits)
            .putLong(seq).putLong(sentAtMs).putInt(ttlMs.toInt()).put(body)
            .array()
    }

    override fun toString() = "Envelope($kind, $type, seq=$seq, ${body.size} B)"

    companion object {
        const val VERSION = 1
        const val TYPE_MAX = 48
        const val MAX_TTL = 0xffffffffL
        val TYPE = Regex("[A-Za-z0-9._-]{1,$TYPE_MAX}")

        /** Ad dışındaki sabit başlık: v, sınıf, ad boyu, id, seq, sentAtMs, ttlMs. */
        private const val FIXED = 3 + 16 + 8 + 8 + 4

        fun headerSize(type: String): Int = FIXED + type.length

        /** Bozuk zarf (sürüm, sınıf, ad, boy) → `null`. */
        fun decode(b: ByteArray): Envelope? {
            if (b.size < FIXED + 1) return null
            if ((b[0].toInt() and 0xff) != VERSION) return null
            val kind = EnvelopeKind.of(b[1].toInt() and 0xff) ?: return null
            val n = b[2].toInt() and 0xff
            if (n !in 1..TYPE_MAX || b.size < FIXED + n) return null
            for (i in 3 until 3 + n) {
                val c = b[i].toInt() and 0xff
                val ok = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
                    c == '.'.code || c == '_'.code || c == '-'.code
                if (!ok) return null
            }
            val type = String(b, 3, n, Charsets.US_ASCII)
            val buf = ByteBuffer.wrap(b, 3 + n, FIXED - 3)
            val id = UUID(buf.long, buf.long)
            val seq = buf.long
            val sentAt = buf.long
            val ttl = buf.int.toLong() and 0xffffffffL
            return Envelope(kind, type, id, seq, sentAt, ttl, b.copyOfRange(FIXED + n, b.size))
        }
    }
}
