package com.kuryelog.logic.scooter

import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Independent implementation of the published Encryption2 wire algorithm (SCOOTER.md 2.3; NB `NinebotCrypto.java`, line by
 * line). Key = SHA-1 of the two 16-byte padded inputs, first 16 bytes; counter 0 = static keystream + checksum (PRE_COMM),
 * session frames = counter AES-CTR + 4-byte CBC-MAC; replays and downgrades are rejected. Reference: NR `nb_crypto.py`.
 */
class NinebotCrypto(k1: ByteArray, k2: ByteArray?, firmwareInput: Boolean) {
    private var key = ByteArray(0)
    private var auth = ByteArray(16)
    private var staticInput = ByteArray(16)
    private var tx = 0
    private var rx = -1
    private var sn = false

    init {
        setKey(k1, k2)
        if (firmwareInput) staticInput = FW.clone()
    }

    /** `Arrays.copyOf` padding: shorter inputs are zero-filled, longer ones cut at 16 bytes. */
    fun setKey(a: ByteArray, b: ByteArray?) {
        try {
            val sha = MessageDigest.getInstance("SHA-1")
            sha.update(a.copyOf(16))
            key = sha.digest(if (b == null) ByteArray(16) else b.copyOf(16)).copyOf(16)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException(e)
        }
    }

    fun start(challenge: ByteArray) {
        if (challenge.size != 16) throw IllegalArgumentException("challenge")
        auth = challenge.clone()
        sn = true
        tx = 1
        rx = -1
    }

    private fun aes(data: ByteArray): ByteArray = try {
        val c = Cipher.getInstance("AES/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        c.doFinal(data)
    } catch (e: GeneralSecurityException) {
        throw IllegalStateException(e)
    }

    private fun block(counter: Int, flag: Int, end: Int): ByteArray {
        val b = ByteArray(16)
        b[0] = flag.toByte()
        b[1] = (counter ushr 24).toByte()
        b[2] = (counter ushr 16).toByte()
        b[3] = (counter ushr 8).toByte()
        b[4] = counter.toByte()
        System.arraycopy(auth, 0, b, 5, 8)
        b[15] = end.toByte()
        return b
    }

    private fun mac(p: ByteArray, counter: Int): ByteArray {
        var x = aes(block(counter, 0x59, p.size - 3))
        val header = ByteArray(16)
        System.arraycopy(p, 0, header, 0, 3)
        for (i in 0 until 16) x[i] = (x[i].toInt() xor header[i].toInt()).toByte()
        x = aes(x)
        var off = 3
        while (off < p.size) {
            var j = 0
            while (j < 16 && off + j < p.size) { x[j] = (x[j].toInt() xor p[off + j].toInt()).toByte(); j++ }
            x = aes(x)
            off += 16
        }
        return x.copyOf(4)
    }

    private fun crypt(input: ByteArray, output: ByteArray, n: Int, counter: Int) {
        var off = 3
        var index = 1
        while (off < n) {
            val ks = aes(if (counter == 0) staticInput else block(counter, 1, index))
            var j = 0
            while (j < 16 && off + j < n) { output[off + j] = (input[off + j].toInt() xor ks[j].toInt()).toByte(); j++ }
            off += 16
            index++
        }
    }

    fun encrypt(plain: ByteArray): ByteArray {
        plainLength(plain)
        var counter = 0
        if (sn) {
            if (tx >= 65535) throw IllegalStateException("counter exhausted; reconnect")
            counter = ++tx
        }
        val wire = plain.copyOf(plain.size + 6)
        crypt(plain, wire, plain.size, counter)
        val n = plain.size
        if (counter == 0) {
            var sum = 0
            for (i in 3 until n) sum += plain[i].toInt() and 255
            val crc = sum.inv() and 65535
            wire[n] = 0
            wire[n + 1] = 0
            wire[n + 2] = crc.toByte()
            wire[n + 3] = (crc ushr 8).toByte()
        } else {
            val tag = mac(plain, counter)
            val ks = aes(block(counter, 1, 0))
            for (i in 0 until 4) wire[n + i] = (tag[i].toInt() xor ks[i].toInt()).toByte()
            wire[n + 4] = (counter ushr 8).toByte()
            wire[n + 5] = counter.toByte()
        }
        return wire
    }

    fun decrypt(wire: ByteArray): ByteArray {
        if (wire.size < 13 || wire.size != (wire[2].toInt() and 255) + 13 || (wire[0].toInt() and 255) != 0x5a ||
            (wire[1].toInt() and 255) != 0xa5
        ) throw IllegalArgumentException("wire length/header")
        val n = wire.size - 6
        val counter = ((wire[n + 4].toInt() and 255) shl 8) or (wire[n + 5].toInt() and 255)
        if (sn && counter == 0) throw IllegalArgumentException("counter downgrade")
        if (!sn && counter != 0) throw IllegalArgumentException("unexpected session frame")
        if (sn && counter <= rx) throw IllegalArgumentException("replay")
        val plain = wire.copyOf(n)
        crypt(wire, plain, n, counter)
        if (counter == 0) {
            var sum = 0
            for (i in 3 until n) sum += plain[i].toInt() and 255
            val crc = sum.inv() and 65535
            if (wire[n].toInt() != 0 || wire[n + 1].toInt() != 0 || (wire[n + 2].toInt() and 255) != (crc and 255) ||
                (wire[n + 3].toInt() and 255) != (crc ushr 8)
            ) throw IllegalArgumentException("checksum")
        } else {
            val expected = mac(plain, counter)
            val ks = aes(block(counter, 1, 0))
            val got = ByteArray(4)
            for (i in 0 until 4) got[i] = (wire[n + i].toInt() xor ks[i].toInt()).toByte()
            if (!MessageDigest.isEqual(expected, got)) throw IllegalArgumentException("MAC")
        }
        plainLength(plain)
        if (counter != 0) {
            rx = counter
            tx = maxOf(tx, counter)
        }
        return plain
    }

    companion object {
        /** NR `nb_crypto.py` `FW_DATA` ("libnbcrypto.so @ 0x45A80"): the PRE_COMM static input. */
        val FW: ByteArray = Timing.unhex("97cfb802844143de56002b3b34780a5d")

        private fun plainLength(p: ByteArray) {
            if (p.size < 7 || p.size != (p[2].toInt() and 255) + 7 || (p[0].toInt() and 255) != 0x5a || (p[1].toInt() and 255) != 0xa5) {
                throw IllegalArgumentException("frame")
            }
        }
    }
}
