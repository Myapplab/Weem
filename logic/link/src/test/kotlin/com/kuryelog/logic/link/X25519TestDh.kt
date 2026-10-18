package com.kuryelog.logic.link

import java.math.BigInteger
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.NamedParameterSpec
import java.security.spec.XECPrivateKeySpec
import java.security.spec.XECPublicKeySpec
import javax.crypto.KeyAgreement

/**
 * Noise'un `25519` DH işlevi yalnız testte (resmî vektörler; LINK.md 3.2): JDK'nın `XDH`'ı. Android'de genel API 33'ten, bu
 * yüzden ana kod P-256 kullanır. Anahtar ve DH çıktısı RFC 7748 küçük uçlu 32 bayt.
 */
object X25519TestDh : NoiseDh {
    override val name = "25519"
    override val publicLength = 32

    private val kf = KeyFactory.getInstance("XDH")

    fun keyPair(privateKey: ByteArray): DhKeyPair {
        val priv = kf.generatePrivate(XECPrivateKeySpec(NamedParameterSpec.X25519, privateKey))
        val base = kf.generatePublic(XECPublicKeySpec(NamedParameterSpec.X25519, BigInteger.valueOf(9)))
        val ka = KeyAgreement.getInstance("XDH")
        ka.init(priv)
        ka.doPhase(base, true)
        return DhKeyPair(priv, ka.generateSecret())
    }

    override fun generate(random: SecureRandom): DhKeyPair = keyPair(ByteArray(32).also(random::nextBytes))

    override fun agree(own: DhKeyPair, peerPublic: ByteArray): ByteArray {
        if (peerPublic.size != 32) throw NoiseException("x25519 public length")
        val le = peerPublic.copyOf()
        le[31] = (le[31].toInt() and 0x7f).toByte()
        val u = BigInteger(1, le.reversedArray())
        val pub = kf.generatePublic(XECPublicKeySpec(NamedParameterSpec.X25519, u))
        val ka = KeyAgreement.getInstance("XDH")
        ka.init(own.private)
        ka.doPhase(pub, true)
        return ka.generateSecret()
    }
}
