package com.kuryelog.logic.link

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

/** Mesaj protokolü sürümü (LINK.md 3.10): ana sürüm eşit olmalı; küçük fark bağlanır (bilinmeyen alan ve tür yok sayılır). */
object LinkProtocol {
    const val MAJOR = 1
    const val MINOR = 0
}

/**
 * El sıkışmadaki bilgi yükü (LINK.md 3.4): `cihaz kimliği 16 · protokol ana u8 · küçük u8 · sürüm kodu u32 · sürüm adı (u8 boy +
 * UTF-8 ≤ 32) · cihaz adı (u8 boy + UTF-8 ≤ 32)`; fazlası yok sayılır. Adlar kodlanırken 32 bayta kırpılır.
 */
data class PeerInfo(
    val id: PeerId,
    val protoMajor: Int,
    val protoMinor: Int,
    val appCode: Long,
    val appName: String,
    val deviceName: String,
) {
    init {
        require(protoMajor in 0..255 && protoMinor in 0..255) { "protocol version" }
        require(appCode in 0..0xffffffffL) { "app version code" }
    }

    fun encode(): ByteArray {
        val app = Utf8.cap(appName, NAME_MAX).toByteArray(Charsets.UTF_8)
        val dev = Utf8.cap(deviceName, NAME_MAX).toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(16 + 2 + 4 + 1 + app.size + 1 + dev.size)
            .put(id.bytes()).put(protoMajor.toByte()).put(protoMinor.toByte()).putInt(appCode.toInt())
            .put(app.size.toByte()).put(app).put(dev.size.toByte()).put(dev).array()
    }

    companion object {
        const val NAME_MAX = 32

        fun parse(b: ByteArray): PeerInfo? {
            if (b.size < 16 + 2 + 4 + 1) return null
            val buf = ByteBuffer.wrap(b)
            val id = PeerId.fromBytes(b, 0)
            buf.position(16)
            val major = buf.get().toInt() and 0xff
            val minor = buf.get().toInt() and 0xff
            val code = buf.int.toLong() and 0xffffffffL
            val app = text(buf) ?: return null
            val dev = text(buf) ?: return null
            return PeerInfo(id, major, minor, code, app, dev)
        }

        private fun text(buf: ByteBuffer): String? {
            if (!buf.hasRemaining()) return null
            val n = buf.get().toInt() and 0xff
            if (n > NAME_MAX || buf.remaining() < n) return null
            val raw = ByteArray(n)
            buf.get(raw)
            return Utf8.decode(raw)
        }
    }
}

/** Bu telefonun kimliği: cihaz kimliği, kimlik anahtarı (P-256) ve el sıkışmada gönderilecek bilgi. */
class LocalIdentity(val id: PeerId, val keys: DhKeyPair, val info: PeerInfo) {
    init {
        require(info.id == id) { "info id" }
    }

    override fun toString() = "LocalIdentity($id)"
}

/** Eşleşen telefon (LINK.md 3.3): kimlik, açık anahtar (sabitlenir), ad, eşleşme zamanı. */
class PeerRecord(val id: PeerId, publicKey: ByteArray, val name: String, val pairedAtMs: Long) {
    private val key = publicKey.copyOf()

    init {
        require(P256.isValidPublic(key)) { "peer public key" }
    }

    val publicKey: ByteArray get() = key.copyOf()
    val fingerprint: String get() = Fingerprint.of(key)

    fun keyEquals(other: ByteArray?): Boolean = other != null && MessageDigest.isEqual(key, other)

    override fun equals(other: Any?) = other is PeerRecord && other.id == id && other.keyEquals(key) &&
        other.name == name && other.pairedAtMs == pairedAtMs

    override fun hashCode() = (id.hashCode() * 31 + key.contentHashCode()) * 31 + name.hashCode()
    override fun toString() = "PeerRecord($id, $name, $fingerprint)"
}

/**
 * Kanalın kime açılacağı (LINK.md 3.3–3.4): eşleşmiş eş; QR veren telefon (pencere açık, her kimlik, jetonla); QR okuyan telefon
 * (yalnız QR'daki kimlik ve anahtar). Sırlar `toString`'de yazılmaz.
 */
sealed class Trust {
    /** Beklenen eş kimliği; veren telefonda `null` (her kimlik denenir, jeton doğrular). */
    abstract val expected: PeerId?

    class Paired(val peer: PeerRecord) : Trust() {
        override val expected: PeerId get() = peer.id
        override fun toString() = "Trust.Paired(${peer.id})"
    }

    class Issuer(token: ByteArray, val untilMs: Long) : Trust() {
        private val t = token.copyOf()

        init {
            require(t.size == PairingQr.TOKEN_LENGTH) { "token length" }
        }

        internal val token: ByteArray get() = t.copyOf()
        override val expected: PeerId? get() = null
        internal fun sameToken(other: Issuer) = MessageDigest.isEqual(t, other.t)
        override fun toString() = "Trust.Issuer(until=$untilMs)"
    }

    class Scanner(val qr: PairingQr, val untilMs: Long) : Trust() {
        override val expected: PeerId get() = qr.id
        override fun toString() = "Trust.Scanner(${qr.id}, until=$untilMs)"
    }
}

sealed interface QrResult {
    class Ok(val qr: PairingQr) : QrResult

    /** Daha yeni bir WEEM'in QR'ı: "WEEM'i güncelleyin". */
    object Newer : QrResult

    object Invalid : QrResult
}

/**
 * Eşleştirme QR'ı (LINK.md 3.3): `weem://pair?v=1&id=<cihaz kimliği>&k=<açık anahtar>&t=<jeton>&n=<cihaz adı>`; anahtar ve jeton
 * base64url dolgusuz, ad URL kodlu ve ≤ 32 bayt UTF-8. Okuyan bilinmeyen parametreyi yok sayar. Metin uygulama dışına çıkmaz
 * (S24: Google code scanner); `toString` jetonu yazmaz.
 */
class PairingQr(val id: PeerId, publicKey: ByteArray, token: ByteArray, name: String) {
    private val key = publicKey.copyOf()
    private val tok = token.copyOf()
    val name: String = Utf8.cap(name, PeerInfo.NAME_MAX)

    init {
        require(P256.isValidPublic(key)) { "public key" }
        require(tok.size == TOKEN_LENGTH) { "token length" }
    }

    val publicKey: ByteArray get() = key.copyOf()
    val token: ByteArray get() = tok.copyOf()

    internal fun sameAs(o: PairingQr) = o.id == id && MessageDigest.isEqual(o.key, key) && MessageDigest.isEqual(o.tok, tok)

    fun toUri(): String = "$PREFIX" + "v=$VERSION&id=${id.text}&k=${B64.url(key)}&t=${B64.url(tok)}&n=${encodeName(name)}"

    override fun toString() = "PairingQr($id)"

    companion object {
        const val VERSION = 1
        const val TOKEN_LENGTH = 32

        /** Jetonun ömrü (veren telefonun monoton saatiyle; okuyan da taramadan sonra bu kadar dener). */
        const val WINDOW_MS = 300_000L
        private const val PREFIX = "weem://pair?"
        private const val MAX_TEXT = 1024

        fun parse(text: String): QrResult {
            if (text.length > MAX_TEXT || !text.startsWith(PREFIX)) return QrResult.Invalid
            val params = HashMap<String, String>()
            for (part in text.substring(PREFIX.length).split('&')) {
                if (part.isEmpty()) continue
                val eq = part.indexOf('=')
                if (eq <= 0) return QrResult.Invalid
                if (params.put(part.substring(0, eq), part.substring(eq + 1)) != null) return QrResult.Invalid
            }
            val v = params["v"]?.toIntOrNull() ?: return QrResult.Invalid
            if (v > VERSION) return QrResult.Newer
            if (v != VERSION) return QrResult.Invalid
            val id = PeerId.of(params["id"]) ?: return QrResult.Invalid
            val key = params["k"]?.let(B64::parseUrl) ?: return QrResult.Invalid
            val token = params["t"]?.let(B64::parseUrl) ?: return QrResult.Invalid
            if (!P256.isValidPublic(key) || token.size != TOKEN_LENGTH) return QrResult.Invalid
            val name = params["n"]?.let(::decodeName) ?: if (params.containsKey("n")) return QrResult.Invalid else ""
            return QrResult.Ok(PairingQr(id, key, token, name))
        }

        private fun encodeName(n: String): String = URLEncoder.encode(n, "UTF-8")

        private fun decodeName(s: String): String? = try {
            URLDecoder.decode(s, "UTF-8")
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: UnsupportedEncodingException) {
            null
        }
    }
}

/** Parmak izi (LINK.md 3.3): SHA-256(açık anahtar)'ın ilk 10 baytı, onaltılık büyük harf, 4'lü gruplar. */
object Fingerprint {
    fun of(publicKey: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(publicKey)
        val hex = StringBuilder()
        for (i in 0 until 10) {
            if (i > 0 && i % 2 == 0) hex.append(' ')
            hex.append(HEX[(d[i].toInt() shr 4) and 0xf]).append(HEX[d[i].toInt() and 0xf])
        }
        return hex.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}

/** base64url, dolgusuz (QR). Android'de `java.util.Base64` API 26'dan. */
object B64 {
    fun url(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

    fun parseUrl(s: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(s)
    } catch (e: IllegalArgumentException) {
        null
    }
}

internal object Utf8 {
    /** En çok [maxBytes] bayt UTF-8; karakter (vekil çifti) bölünmez. */
    fun cap(s: String, maxBytes: Int): String {
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            if (bytes + n > maxBytes) break
            bytes += n
            i += Character.charCount(cp)
        }
        return s.substring(0, i)
    }

    fun decode(b: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(b)).toString()
    } catch (e: CharacterCodingException) {
        null
    }
}
