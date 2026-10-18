package com.kuryelog.logic.link

import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Noise DH işlevi P-256 (secp256r1), LINK.md 3.2: açık anahtar 65 bayt SEC1 sıkıştırılmamış (`04 ‖ X ‖ Y`), DH çıktısı 32 bayt X
 * (JCA `ECDH`). Gelen anahtar eğri üstünde mi kendimiz denetleriz (sağlayıcıya bırakılmaz): boy, önek, `X, Y < p`,
 * `y² = x³ − 3x + b`; kofaktör 1 olduğundan eğri üstündeki her nokta asal mertebeli gruptadır. minSdk 29'da hepsi var.
 */
object P256 : NoiseDh {
    override val name = "P256"
    override val publicLength = 65

    private val P = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)
    private val B = BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16)
    private val THREE = BigInteger.valueOf(3)

    /** Sağlayıcının kendi adlı eğri parametresi (açık parametreli spec bazı sağlayıcılarda yavaş ya da desteksiz). */
    private val params: ECParameterSpec by lazy {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"))
        (g.generateKeyPair().public as ECPublicKey).params
    }

    override fun generate(random: SecureRandom): DhKeyPair {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"), random)
        val kp = g.generateKeyPair()
        return DhKeyPair(kp.private, encode(kp.public as ECPublicKey))
    }

    fun encode(k: ECPublicKey): ByteArray = byteArrayOf(4) + fixed32(k.w.affineX) + fixed32(k.w.affineY)

    fun isValidPublic(b: ByteArray): Boolean = point(b) != null

    override fun agree(own: DhKeyPair, peerPublic: ByteArray): ByteArray {
        val p = point(peerPublic) ?: throw NoiseException("invalid public key")
        return ecdh(own.private, p)
    }

    /** Sabit anahtarlı çift (vektör testleri): özel anahtar açık anahtara uymazsa [NoiseException]. */
    fun keyPair(scalar: BigInteger, public: ByteArray): DhKeyPair {
        val priv = try {
            KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(scalar, params))
        } catch (e: GeneralSecurityException) {
            throw NoiseException("private key", e)
        }
        return checked(priv, public)
    }

    /** Saklanan kimlik anahtarı (PKCS#8 + açık anahtar; LINK.md 3.12). Uymazsa ya da bozuksa [NoiseException]. */
    fun restore(pkcs8: ByteArray, public: ByteArray): DhKeyPair {
        val priv = try {
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        } catch (e: GeneralSecurityException) {
            throw NoiseException("pkcs8", e)
        } catch (e: IllegalArgumentException) {
            throw NoiseException("pkcs8", e)
        }
        return checked(priv, public)
    }

    fun pkcs8(pair: DhKeyPair): ByteArray = pair.private.encoded ?: throw NoiseException("private key not encodable")

    /**
     * Özel anahtar açık anahtara uyuyor mu: `ECDH(d, G)` = `d·G`'nin X'i, açık anahtarın X'iyle aynı olmalı (çarpma JCA'da).
     * Y'nin işareti denetlenmez (yanlış işaretli açık anahtar el sıkışma özetini bozar, el sıkışma kendiliğinden düşer).
     */
    private fun checked(priv: PrivateKey, public: ByteArray): DhKeyPair {
        point(public) ?: throw NoiseException("invalid public key")
        val x = ecdh(priv, params.generator)
        if (!MessageDigest.isEqual(x, public.copyOfRange(1, 33))) throw NoiseException("key pair mismatch")
        return DhKeyPair(priv, public)
    }

    private fun ecdh(priv: PrivateKey, peer: ECPoint): ByteArray {
        try {
            val pub = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(peer, params))
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(priv)
            ka.doPhase(pub, true)
            val s = ka.generateSecret()
            return when {
                s.size == 32 -> s
                s.size < 32 -> ByteArray(32 - s.size) + s
                else -> throw NoiseException("ecdh length")
            }
        } catch (e: GeneralSecurityException) {
            if (e is NoiseException) throw e
            throw NoiseException("ecdh", e)
        } catch (e: IllegalStateException) {
            throw NoiseException("ecdh", e)
        }
    }

    private fun point(b: ByteArray): ECPoint? {
        if (b.size != publicLength || b[0].toInt() != 4) return null
        val x = BigInteger(1, b.copyOfRange(1, 33))
        val y = BigInteger(1, b.copyOfRange(33, 65))
        if (x >= P || y >= P) return null
        val lhs = y.multiply(y).mod(P)
        val rhs = x.multiply(x).multiply(x).subtract(x.multiply(THREE)).add(B).mod(P)
        return if (lhs == rhs) ECPoint(x, y) else null
    }

    private fun fixed32(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return when {
            b.size == 32 -> b
            b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
            else -> ByteArray(32 - b.size) + b
        }
    }
}
