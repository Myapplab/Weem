package com.kuryelog.logic.scooter

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

/**
 * Derives the 16-byte pairing session key from the device-supplied challenge, using the same deterministic construction as the
 * published reference client (NR `nb_crypto.py`). Read-only telemetry only: the key authorises reading the owner's own paired G3;
 * no setting or control command is ever sent. This is not a G3 success claim. (SCOOTER.md 2.7; NB `SessionKey.java`.)
 */
object SessionKey {
    /** Fresh keys for newly requested pairing (what the app uses); existing stored keys are never rotated. */
    fun random(): ByteArray {
        val key = ByteArray(16)
        SecureRandom().nextBytes(key)
        return key
    }

    /**
     * Reference/parity fixtures only. Java semantics on purpose (R-S7): the byte is sign-extended to `Int` and shifted as an `Int`
     * (`shl` keeps only five bits: 32 → 0, 40 → 8, …), then added to the `Long` seed; the generator is `java.util.Random`.
     */
    fun derive(auth: ByteArray, wallMs: Long): ByteArray {
        if (auth.size != 16) throw IllegalArgumentException("auth length")
        var seed = wallMs
        for (i in auth.indices) seed += (auth[i].toInt() shl ((i % 8) * 8)).toLong()
        val random = ByteArray(16)
        java.util.Random(seed).nextBytes(random)
        try {
            return MessageDigest.getInstance("SHA-256").digest(random).copyOf(16)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException(e)
        } finally {
            random.fill(0)
        }
    }
}
