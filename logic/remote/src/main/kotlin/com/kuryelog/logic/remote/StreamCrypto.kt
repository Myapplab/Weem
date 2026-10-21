package com.kuryelog.logic.remote

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Akış anahtarları (REMOTE.md 1.4): HKDF-SHA256, tuz = oturum ‖ izleyenin nonce'u ‖ paylaşanın nonce'u, girdi = link üstünden kurulan
 * geçici ECDH sırrı. Her bağlantıda iki taraf yeni nonce verir: aynı oturumda yeniden bağlanmak yeni anahtar demektir (sayaç sıfırdan
 * başlasa da GCM nonce'u tekrar etmez).
 */
class StreamKeys private constructor(val hostToViewer: ByteArray, val viewerToHost: ByteArray) {
    override fun toString() = "StreamKeys"

    companion object {
        private val INFO_HOST = "WEEM remote 1 host".toByteArray(Charsets.US_ASCII)
        private val INFO_VIEWER = "WEEM remote 1 viewer".toByteArray(Charsets.US_ASCII)

        fun derive(shared: ByteArray, session: UUID, viewerNonce: ByteArray, hostNonce: ByteArray): StreamKeys {
            val salt = ByteBuffer.allocate(16 + viewerNonce.size + hostNonce.size)
                .putLong(session.mostSignificantBits).putLong(session.leastSignificantBits).put(viewerNonce).put(hostNonce).array()
            val prk = hmac(salt, shared)
            return StreamKeys(hmac(prk, INFO_HOST, byteArrayOf(1)), hmac(prk, INFO_VIEWER, byteArrayOf(1)))
        }

        private fun hmac(key: ByteArray, vararg data: ByteArray): ByteArray {
            val m = Mac.getInstance("HmacSHA256")
            m.init(SecretKeySpec(key, "HmacSHA256"))
            data.forEach(m::update)
            return m.doFinal()
        }
    }
}

/** Tek yönlü AES-256-GCM: nonce = 4 sıfır ‖ sayaç u64 BE (0'dan, ileti başına +1), AD boş, etiket 16 bayt. */
class StreamCipher(key: ByteArray, private val encrypt: Boolean) {
    private val spec = SecretKeySpec(key.copyOf(), "AES")
    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private var counter = 0L

    init { require(key.size == 32) }

    fun seal(plain: ByteArray): ByteArray {
        check(encrypt)
        cipher.init(Cipher.ENCRYPT_MODE, spec, GCMParameterSpec(TAG_BITS, nonce(counter++)))
        return cipher.doFinal(plain)
    }

    /** Değişmiş, tekrarlanmış ya da sırası bozulmuş iletide [StreamException] (sayaç ilerlemez; akış kapanır). */
    fun open(sealed: ByteArray): ByteArray {
        check(!encrypt)
        if (sealed.size < TAG_BITS / 8) throw StreamException(CloseReason.AUTH, "short")
        return try {
            cipher.init(Cipher.DECRYPT_MODE, spec, GCMParameterSpec(TAG_BITS, nonce(counter)))
            cipher.doFinal(sealed).also { counter++ }
        } catch (e: GeneralSecurityException) {
            throw StreamException(CloseReason.AUTH, "open failed")
        }
    }

    override fun toString() = "StreamCipher"

    private fun nonce(n: Long) = ByteBuffer.allocate(12).putInt(0).putLong(n).array()

    private companion object {
        const val TAG_BITS = 128
    }
}

/**
 * Bir akış bağlantısının bir ucu (REMOTE.md 1.4), saf: gelen baytları alır, iç iletileri verir, yazılacak baytları [out]'a yollar.
 *
 * - İzleyen ([viewer]): [start] `HELLO`'yu yollar; paylaşanın `HELLO`'su gelince anahtarları türetir ve ilk şifreli iletiyi
 *   ([firstMessage], kimliğini kanıtlar) yollar.
 * - Paylaşan ([host]): izleyenin `HELLO`'sundaki oturum için ortak sırrı [secretFor]'dan alır (yoksa kapat), kendi `HELLO`'sunu yollar;
 *   ilk geçerli şifreli ileti gelince [authenticated] olur. O zamana dek [send] yasaktır.
 */
class StreamEndpoint private constructor(
    private val isHost: Boolean,
    private val random: SecureRandom,
    private val out: (ByteArray) -> Unit,
    private val secretFor: (UUID) -> ByteArray?,
    private val viewerSession: UUID?,
    private val firstMessage: StreamMsg?,
) {
    private val decoder = StreamFrames.Decoder()
    private val ownNonce = ByteArray(StreamHello.NONCE).also(random::nextBytes)
    private var sealer: StreamCipher? = null
    private var opener: StreamCipher? = null

    /** Oturum (paylaşanda `HELLO` gelince belli olur). */
    var session: UUID? = viewerSession
        private set

    /** Paylaşan: izleyenin ilk şifreli iletisi çözüldü. İzleyen: anahtarlar kuruldu. */
    var authenticated = false
        private set

    /** Gönderilen/alınan şifreli ileti sayısı (tanı). */
    var sent = 0L
        private set

    fun start() {
        if (!isHost) out(StreamFrames.encode(StreamFrames.HELLO, StreamHello(viewerSession!!, ownNonce).encode()))
    }

    /** Hata: [StreamException] (çağıran akışı kapatır). */
    fun receive(bytes: ByteArray, off: Int = 0, len: Int = bytes.size): List<StreamMsg> {
        val msgs = ArrayList<StreamMsg>(2)
        for (f in decoder.feed(bytes, off, len)) {
            when (f.type) {
                StreamFrames.HELLO -> hello(f.body)
                else -> {
                    val o = opener ?: throw StreamException(CloseReason.PROTOCOL, "sealed before hello")
                    val m = StreamMsg.decode(o.open(f.body), fromHost = !isHost) ?: throw StreamException(CloseReason.PROTOCOL, "bad message")
                    if (isHost) authenticated = true
                    msgs += m
                }
            }
        }
        return msgs
    }

    fun send(m: StreamMsg) {
        val s = sealer
        check(s != null && authenticated) { "not open" }
        out(StreamFrames.encode(StreamFrames.SEALED, s.seal(StreamMsg.encode(m, fromHost = isHost))))
        sent++
    }

    private fun hello(body: ByteArray) {
        if (sealer != null) throw StreamException(CloseReason.PROTOCOL, "second hello")
        val h = StreamHello.decode(body) ?: throw StreamException(CloseReason.PROTOCOL, "bad hello")
        if (isHost) {
            val secret = secretFor(h.session) ?: throw StreamException(CloseReason.SESSION, "unknown session")
            session = h.session
            keys(StreamKeys.derive(secret, h.session, h.nonce, ownNonce))
            out(StreamFrames.encode(StreamFrames.HELLO, StreamHello(h.session, ownNonce).encode()))
        } else {
            if (h.session != viewerSession) throw StreamException(CloseReason.SESSION, "wrong session")
            val secret = secretFor(h.session) ?: throw StreamException(CloseReason.SESSION, "no secret")
            keys(StreamKeys.derive(secret, h.session, ownNonce, h.nonce))
            authenticated = true
            firstMessage?.let(::send)
        }
    }

    private fun keys(k: StreamKeys) {
        sealer = StreamCipher(if (isHost) k.hostToViewer else k.viewerToHost, encrypt = true)
        opener = StreamCipher(if (isHost) k.viewerToHost else k.hostToViewer, encrypt = false)
    }

    companion object {
        fun host(random: SecureRandom, secretFor: (UUID) -> ByteArray?, out: (ByteArray) -> Unit) =
            StreamEndpoint(true, random, out, secretFor, null, null)

        fun viewer(random: SecureRandom, session: UUID, secret: ByteArray, first: StreamMsg, out: (ByteArray) -> Unit): StreamEndpoint {
            val s = secret.copyOf()
            return StreamEndpoint(false, random, out, { if (it == session) s else null }, session, first)
        }
    }
}
