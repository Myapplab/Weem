package com.kuryelog.logic.link

import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Noise Protocol Framework (rev. 34) çekirdeği, LINK.md 3.2: CipherState, SymmetricState, HandshakeState; şifre AESGCM, özet
 * SHA256, kalıp `XX` ve `XXpsk3`. DH işlevi dışarıdan ([NoiseDh]): ana kodda [P256], testte resmî vektörler için X25519.
 * Yalnız JCA. Spesifikasyondan birebir; kendi kripto kuralı yok.
 */
object Noise {
    /** AES-GCM etiketi. */
    const val TAG = 16

    /** Noise ileti sınırı (bayt). */
    const val MAX_MESSAGE = 65_535

    internal val EMPTY = ByteArray(0)
}

/** El sıkışma ya da şifreli ileti çözülemedi, anahtar geçersiz. İleti yalnız neden taşır (sır, anahtar, yük yok). */
class NoiseException(reason: String, cause: Throwable? = null) : GeneralSecurityException(reason, cause)

/** DH anahtar çifti: özel anahtar JCA nesnesi, açık anahtar DH işlevinin kodlaması. `toString` anahtarı yazmaz. */
class DhKeyPair(val private: PrivateKey, public: ByteArray) {
    private val pub = public.copyOf()
    val public: ByteArray get() = pub.copyOf()

    override fun toString() = "DhKeyPair(${pub.size} B)"
}

/** Noise DH işlevi (spesifikasyon 4.1): üretme, anlaşma, açık anahtar boyu (iletide DHLEN). */
interface NoiseDh {
    val name: String
    val publicLength: Int
    fun generate(random: SecureRandom): DhKeyPair

    /** Geçersiz açık anahtarda [NoiseException]. */
    fun agree(own: DhKeyPair, peerPublic: ByteArray): ByteArray
}

/** Noise CipherState (5.1): AES-256-GCM, nonce = 4 sıfır bayt ‖ 64 bit büyük uçlu sayaç; başarısız çözme sayacı ilerletmez. */
class CipherState {
    private var key: SecretKeySpec? = null
    private var cipher: Cipher? = null

    /** Sıradaki nonce (işaretsiz 64 bit; 2^64 − 1 ayrılmış). */
    var nonce = 0L
        private set

    fun initializeKey(k: ByteArray?) {
        key = k?.let { SecretKeySpec(it, "AES") }
        nonce = 0
    }

    fun hasKey() = key != null

    fun encrypt(ad: ByteArray, plaintext: ByteArray): ByteArray {
        val k = key ?: return plaintext.copyOf()
        val c = prepare(Cipher.ENCRYPT_MODE, k, ad)
        val out = try {
            c.doFinal(plaintext)
        } catch (e: GeneralSecurityException) {
            throw NoiseException("encrypt", e)
        }
        nonce++
        return out
    }

    fun decrypt(ad: ByteArray, ciphertext: ByteArray): ByteArray {
        val k = key ?: return ciphertext.copyOf()
        if (ciphertext.size < Noise.TAG) throw NoiseException("short ciphertext")
        val c = prepare(Cipher.DECRYPT_MODE, k, ad)
        val out = try {
            c.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw NoiseException("decrypt")
        }
        nonce++
        return out
    }

    private fun prepare(mode: Int, k: SecretKeySpec, ad: ByteArray): Cipher {
        if (nonce == -1L) throw NoiseException("nonce exhausted")
        val iv = ByteArray(12)
        for (i in 0 until 8) iv[4 + i] = (nonce ushr (56 - 8 * i)).toByte()
        val c = cipher ?: Cipher.getInstance("AES/GCM/NoPadding").also { cipher = it }
        try {
            c.init(mode, k, GCMParameterSpec(128, iv))
        } catch (e: GeneralSecurityException) {
            throw NoiseException("cipher init", e)
        }
        c.updateAAD(ad)
        return c
    }
}

/** Noise SymmetricState (5.2); HASHLEN 32. */
internal class SymmetricState(protocolName: String) {
    var ck: ByteArray
        private set
    var h: ByteArray
        private set
    val cs = CipherState()

    init {
        val name = protocolName.toByteArray(Charsets.US_ASCII)
        h = if (name.size <= HASHLEN) name.copyOf(HASHLEN) else sha256(name)
        ck = h.copyOf()
    }

    fun mixKey(ikm: ByteArray) {
        val o = hkdf(ck, ikm, 2)
        ck = o[0]
        cs.initializeKey(o[1])
    }

    fun mixHash(data: ByteArray) {
        h = sha256(h, data)
    }

    fun mixKeyAndHash(ikm: ByteArray) {
        val o = hkdf(ck, ikm, 3)
        ck = o[0]
        mixHash(o[1])
        cs.initializeKey(o[2])
    }

    fun encryptAndHash(plaintext: ByteArray): ByteArray = cs.encrypt(h, plaintext).also(::mixHash)

    fun decryptAndHash(ciphertext: ByteArray): ByteArray {
        val p = cs.decrypt(h, ciphertext)
        mixHash(ciphertext)
        return p
    }

    fun split(): Pair<CipherState, CipherState> {
        val o = hkdf(ck, Noise.EMPTY, 2)
        return CipherState().apply { initializeKey(o[0]) } to CipherState().apply { initializeKey(o[1]) }
    }

    companion object {
        const val HASHLEN = 32

        fun sha256(vararg parts: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            parts.forEach(md::update)
            return md.digest()
        }

        private fun hmac(key: ByteArray, vararg data: ByteArray): ByteArray {
            val m = Mac.getInstance("HmacSHA256")
            m.init(SecretKeySpec(key, "HmacSHA256"))
            data.forEach(m::update)
            return m.doFinal()
        }

        /** Noise HKDF (4.3): HMAC-SHA256 zinciri, 2 ya da 3 çıktı. */
        fun hkdf(chainingKey: ByteArray, ikm: ByteArray, outputs: Int): List<ByteArray> {
            val temp = hmac(chainingKey, ikm)
            val o1 = hmac(temp, byteArrayOf(1))
            val o2 = hmac(temp, o1, byteArrayOf(2))
            return if (outputs == 2) listOf(o1, o2) else listOf(o1, o2, hmac(temp, o2, byteArrayOf(3)))
        }
    }
}

internal enum class Token { E, S, EE, ES, SE, SS, PSK }

/** El sıkışma kalıbı (7.5, 9.4): ileti sırası başlatan, yanıtlayan, başlatan. */
enum class NoisePattern(val id: String, internal val messages: List<List<Token>>) {
    XX("XX", listOf(listOf(Token.E), listOf(Token.E, Token.EE, Token.S, Token.ES), listOf(Token.S, Token.SE))),
    XX_PSK3("XXpsk3", listOf(listOf(Token.E), listOf(Token.E, Token.EE, Token.S, Token.ES), listOf(Token.S, Token.SE, Token.PSK)));

    internal val psk: Boolean get() = messages.any { Token.PSK in it }
}

/** El sıkışma bitince yön anahtarları: [send] bu tarafın gönderdiği, [receive] aldığı. */
class NoiseSession(val send: CipherState, val receive: CipherState, handshakeHash: ByteArray) {
    private val hash = handshakeHash.copyOf()
    val handshakeHash: ByteArray get() = hash.copyOf()
}

/**
 * Noise HandshakeState (5.3). [ephemeral] geçici anahtarı verir (ana kodda yeni üretilir; vektör testlerinde sabit).
 * Hata ([NoiseException]) durumu bozar: nesne bir daha kullanılmaz. [session] yalnız bir kez (aynı anahtar iki kez bölünmesin).
 */
class HandshakeState(
    val pattern: NoisePattern,
    private val dh: NoiseDh,
    val initiator: Boolean,
    prologue: ByteArray,
    private val s: DhKeyPair,
    psk: ByteArray? = null,
    private val ephemeral: () -> DhKeyPair,
) {
    val protocolName = "Noise_${pattern.id}_${dh.name}_AESGCM_SHA256"
    private val ss = SymmetricState(protocolName)
    private val psk = psk?.copyOf()
    private var e: DhKeyPair? = null
    private var re: ByteArray? = null
    private var rs: ByteArray? = null
    private var index = 0
    private var failed = false
    private var split = false

    init {
        require((psk != null) == pattern.psk) { "psk" }
        require(psk == null || psk.size == 32) { "psk length" }
        ss.mixHash(prologue)
    }

    val complete: Boolean get() = index == pattern.messages.size

    /** Sıradaki ileti bu tarafın mı (başlatan 0. ve 2. iletiyi yazar). */
    val myTurn: Boolean get() = !complete && (index % 2 == 0) == initiator

    /** Karşının kimlik anahtarı (`s` iletisi okununca). */
    val remoteStatic: ByteArray? get() = rs?.copyOf()

    fun writeMessage(payload: ByteArray): ByteArray {
        check(!failed && myTurn) { "not my turn" }
        try {
            val out = ByteArrayOutputStream()
            for (t in pattern.messages[index]) {
                when (t) {
                    Token.E -> {
                        val k = ephemeral()
                        e = k
                        val pub = k.public
                        out.write(pub)
                        ss.mixHash(pub)
                        if (pattern.psk) ss.mixKey(pub)
                    }
                    Token.S -> out.write(ss.encryptAndHash(s.public))
                    else -> dhToken(t)
                }
            }
            out.write(ss.encryptAndHash(payload))
            index++
            return out.toByteArray()
        } catch (x: NoiseException) {
            failed = true
            throw x
        }
    }

    fun readMessage(message: ByteArray): ByteArray {
        check(!failed && !complete && !myTurn) { "not their turn" }
        try {
            var pos = 0
            for (t in pattern.messages[index]) {
                when (t) {
                    Token.E -> {
                        if (message.size - pos < dh.publicLength) throw NoiseException("short message")
                        val r = message.copyOfRange(pos, pos + dh.publicLength)
                        pos += dh.publicLength
                        re = r
                        ss.mixHash(r)
                        if (pattern.psk) ss.mixKey(r)
                    }
                    Token.S -> {
                        val n = dh.publicLength + if (ss.cs.hasKey()) Noise.TAG else 0
                        if (message.size - pos < n) throw NoiseException("short message")
                        rs = ss.decryptAndHash(message.copyOfRange(pos, pos + n))
                        pos += n
                    }
                    else -> dhToken(t)
                }
            }
            val payload = ss.decryptAndHash(message.copyOfRange(pos, message.size))
            index++
            return payload
        } catch (x: NoiseException) {
            failed = true
            throw x
        }
    }

    private fun dhToken(t: Token) {
        when (t) {
            Token.EE -> ss.mixKey(dh.agree(e!!, re!!))
            Token.ES -> ss.mixKey(if (initiator) dh.agree(e!!, rs!!) else dh.agree(s, re!!))
            Token.SE -> ss.mixKey(if (initiator) dh.agree(s, re!!) else dh.agree(e!!, rs!!))
            Token.SS -> ss.mixKey(dh.agree(s, rs!!))
            Token.PSK -> ss.mixKeyAndHash(psk!!)
            Token.E, Token.S -> error(t)
        }
    }

    fun handshakeHash(): ByteArray = ss.h.copyOf()

    fun session(): NoiseSession {
        check(complete && !failed && !split) { "handshake not complete" }
        split = true
        val (c1, c2) = ss.split()
        return if (initiator) NoiseSession(c1, c2, ss.h) else NoiseSession(c2, c1, ss.h)
    }

    override fun toString() = "HandshakeState($protocolName, initiator=$initiator, message=$index)"
}
