package com.kuryelog.logic.link

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Bir kanalın Noise el sıkışması (LINK.md 3.4), iki `HELLO` alındıktan sonra; başlatan = bağlantıyı açan.
 *
 * msg1 → `e` · msg2 ← `e, ee, s, es` · msg3 → `s, se` (+ `psk`) + başlatanın bilgisi · onay ← yanıtlayanın ilk taşıma iletisi +
 * bilgisi. Kimlik anahtarı güvene göre denetlenir; bilgi yükü yalnız doğrulanmış eşe gider. Hata → [Failure] (`BYE` nedeniyle).
 */
internal class SecureHandshake(
    private val me: LocalIdentity,
    private val trust: Trust,
    val pairing: Boolean,
    val initiator: Boolean,
    private val peerHello: PeerId,
    prologue: ByteArray,
    dh: NoiseDh,
    random: SecureRandom,
) {
    class Failure(val reason: ByeReason, message: String) : Exception(message)

    /** Gönderilecekler: el sıkışma iletisi (`NOISE`) ya da onay (`DATA`). */
    class Out(val noise: ByteArray? = null, val data: ByteArray? = null)

    private val hs = HandshakeState(
        if (pairing) NoisePattern.XX_PSK3 else NoisePattern.XX,
        dh,
        initiator,
        prologue,
        me.keys,
        psk = if (pairing) token() else null,
        ephemeral = { dh.generate(random) },
    )

    var session: NoiseSession? = null
        private set
    var peerKey: ByteArray? = null
        private set
    var peerInfo: PeerInfo? = null
        private set

    /** Kanal açılabilir: yanıtlayan msg3'ü, başlatan onayı doğruladı. */
    var open = false
        private set

    private fun token(): ByteArray = when (trust) {
        is Trust.Issuer -> trust.token
        is Trust.Scanner -> trust.qr.token
        is Trust.Paired -> throw IllegalStateException("pairing without a token")
    }

    /** Başlatanın msg1'i; yanıtlayanda `null`. */
    fun first(): ByteArray? = if (initiator) write(Noise.EMPTY) else null

    fun onNoise(message: ByteArray): Out {
        if (session != null) throw Failure(ByeReason.PROTOCOL, "noise after handshake")
        val payload = read(message)
        return if (!initiator && !hs.complete) {
            // msg1 okundu → msg2 (yük boş).
            if (payload.isNotEmpty()) throw Failure(ByeReason.PROTOCOL, "msg1 payload")
            Out(noise = write(Noise.EMPTY))
        } else if (initiator && !hs.complete) {
            // msg2: yanıtlayanın kimlik anahtarı doğrulanmadan msg3 (kendi anahtarımız ve bilgimiz) gitmez.
            if (payload.isNotEmpty()) throw Failure(ByeReason.PROTOCOL, "msg2 payload")
            val rs = hs.remoteStatic ?: throw Failure(ByeReason.PROTOCOL, "no static")
            checkKey(rs)
            val m3 = write(me.info.encode())
            peerKey = rs
            session = hs.session()
            Out(noise = m3)
        } else if (!initiator) {
            // msg3: başlatan doğrulandı (eşleştirmede jeton da); onay iletisi bizim bilgimizi taşır.
            val rs = hs.remoteStatic ?: throw Failure(ByeReason.PROTOCOL, "no static")
            checkKey(rs)
            val info = PeerInfo.parse(payload) ?: throw Failure(ByeReason.PROTOCOL, "info")
            checkInfo(info)
            val s = hs.session()
            session = s
            peerKey = rs
            peerInfo = info
            open = true
            Out(data = encrypt(s, me.info.encode()))
        } else {
            throw Failure(ByeReason.PROTOCOL, "unexpected noise")
        }
    }

    /** Başlatan: yanıtlayanın ilk taşıma iletisi (anahtar onayı + bilgi). */
    fun onConfirm(data: ByteArray) {
        val s = session
        if (!initiator || s == null || open) throw Failure(ByeReason.PROTOCOL, "unexpected data")
        val payload = try {
            s.receive.decrypt(Noise.EMPTY, data)
        } catch (e: NoiseException) {
            throw Failure(ByeReason.AUTH, "confirm")
        }
        val info = PeerInfo.parse(payload) ?: throw Failure(ByeReason.PROTOCOL, "info")
        checkInfo(info)
        peerInfo = info
        open = true
    }

    private fun read(message: ByteArray): ByteArray = try {
        hs.readMessage(message)
    } catch (e: NoiseException) {
        throw Failure(ByeReason.AUTH, "noise read")
    } catch (e: IllegalStateException) {
        throw Failure(ByeReason.PROTOCOL, "noise order")
    }

    private fun write(payload: ByteArray): ByteArray = try {
        hs.writeMessage(payload)
    } catch (e: NoiseException) {
        throw Failure(ByeReason.AUTH, "noise write")
    }

    private fun encrypt(s: NoiseSession, p: ByteArray): ByteArray = try {
        s.send.encrypt(Noise.EMPTY, p)
    } catch (e: NoiseException) {
        throw Failure(ByeReason.AUTH, "encrypt")
    }

    /** Eşleşmiş: kayıttaki anahtar; okuyan: QR'daki anahtar; veren: her anahtar (jeton psk ile doğrulanır). */
    private fun checkKey(rs: ByteArray) {
        val ok = when (trust) {
            is Trust.Paired -> trust.peer.keyEquals(rs)
            is Trust.Scanner -> MessageDigest.isEqual(trust.qr.publicKey, rs)
            is Trust.Issuer -> true
        }
        if (!ok) throw Failure(ByeReason.AUTH, "static key")
    }

    private fun checkInfo(info: PeerInfo) {
        if (info.id != peerHello) throw Failure(ByeReason.AUTH, "info id")
    }

    override fun toString() = "SecureHandshake(initiator=$initiator, pairing=$pairing, open=$open)"

    companion object {
        private val LABEL = "WEEM link 2".toByteArray(Charsets.US_ASCII)

        /** prologue = `WEEM link 2` ‖ başlatanın `HELLO` yükü ‖ yanıtlayanınki. */
        fun prologue(initiatorHello: ByteArray, responderHello: ByteArray): ByteArray = LABEL + initiatorHello + responderHello
    }
}
