package com.kuryelog.logic.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger
import java.security.SecureRandom

/** LINK.md 3.2: P-256 DH işlevi — 65 bayt SEC1, 32 bayt X, gelen anahtar eğri üstünde denetlenir. */
class P256Test {
    private val random = SecureRandom()
    private val p = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)

    private fun rejects(bytes: ByteArray, why: String) {
        assertFalse(why, P256.isValidPublic(bytes))
        try {
            P256.agree(P256.generate(random), bytes)
            fail("$why: anlaşma reddedilmeliydi")
        } catch (e: NoiseException) {
            // beklenen
        }
    }

    private fun point(x: BigInteger, y: BigInteger): ByteArray {
        fun fixed(v: BigInteger): ByteArray {
            val b = v.toByteArray()
            return when {
                b.size == 32 -> b
                b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
                else -> ByteArray(32 - b.size) + b
            }
        }
        return byteArrayOf(4) + fixed(x) + fixed(y)
    }

    @Test
    fun generatedKeysAgreeBothWays() {
        val a = P256.generate(random)
        val b = P256.generate(random)
        assertEquals(65, a.public.size)
        assertEquals(4, a.public[0].toInt())
        assertTrue(P256.isValidPublic(a.public))
        val ab = P256.agree(a, b.public)
        assertEquals(32, ab.size)
        assertArrayEquals(ab, P256.agree(b, a.public))
    }

    @Test
    fun invalidPublicKeysAreRejected() {
        val good = P256.generate(random).public
        rejects(good.copyOf(64), "kısa")
        rejects(good + byteArrayOf(0), "uzun")
        rejects(byteArrayOf(2) + good.copyOfRange(1, 33) + ByteArray(32), "sıkıştırılmış önek")
        rejects(ByteArray(65), "sıfır")
        val x = BigInteger(1, good.copyOfRange(1, 33))
        val y = BigInteger(1, good.copyOfRange(33, 65))
        rejects(point(x, y.add(BigInteger.ONE).mod(p)), "eğri dışı")
        rejects(point(x.add(p), y), "x ≥ p")
        rejects(point(x, y.add(p)), "y ≥ p")
        assertTrue(P256.isValidPublic(point(x, y)))
        // Negatif y (p − y) de eğri üstünde: geçerli nokta.
        assertTrue(P256.isValidPublic(point(x, p.subtract(y))))
    }

    @Test
    fun pkcs8RestoreAndConsistencyCheck() {
        val a = P256.generate(random)
        val back = P256.restore(P256.pkcs8(a), a.public)
        assertArrayEquals(P256.agree(a, a.public), P256.agree(back, a.public))
        val other = P256.generate(random)
        try {
            P256.restore(P256.pkcs8(a), other.public)
            fail("açık anahtar özel anahtara uymuyor")
        } catch (e: NoiseException) {
            // beklenen
        }
    }

    @Test
    fun keyPairToStringHidesTheKey() {
        val a = P256.generate(random)
        assertFalse(a.toString().contains(a.public.hex()))
        assertFalse(a.toString().contains(a.private.toString()))
    }
}
