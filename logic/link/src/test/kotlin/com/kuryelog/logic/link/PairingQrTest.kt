package com.kuryelog.logic.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/** LINK.md 3.3: `weem://pair?v=1&id=…&k=…&t=…&n=…`, parmak izi, bilgi yükü. */
class PairingQrTest {
    private val random = SecureRandom()
    private val id = PeerId.of("0f0e0d0c-0b0a-4908-8706-050403020100")!!
    private val key = P256.generate(random).public
    private val token = ByteArray(32) { (it * 3).toByte() }

    private fun ok(text: String): PairingQr = (PairingQr.parse(text) as QrResult.Ok).qr

    @Test
    fun roundTripAndLayout() {
        val qr = PairingQr(id, key, token, "Pixel 10 Pro")
        val text = qr.toUri()
        assertTrue(text, text.startsWith("weem://pair?v=1&id=0f0e0d0c-0b0a-4908-8706-050403020100&k="))
        assertTrue(text, text.length < 260)
        val back = ok(text)
        assertEquals(id, back.id)
        assertArrayEquals(key, back.publicKey)
        assertArrayEquals(token, back.token)
        assertEquals("Pixel 10 Pro", back.name)
        // base64url dolgusuz: 65 bayt → 87, 32 bayt → 43 karakter
        val params = text.substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals(87, params.getValue("k").length)
        assertEquals(43, params.getValue("t").length)
        assertFalse(params.getValue("k").contains('=') || params.getValue("k").contains('+') || params.getValue("k").contains('/'))
    }

    @Test
    fun nameIsEscapedAndCappedAt32Bytes() {
        val qr = PairingQr(id, key, token, "Kuryenin telefonu & ğüşiöç Ünal 2026 uzun ad")
        val back = ok(qr.toUri())
        assertTrue(back.name.toByteArray(Charsets.UTF_8).size <= 32)
        assertTrue(qr.toUri().substringAfter("&n=").none { it == ' ' || it == '&' })
        assertTrue("Kuryenin telefonu & ğüşiöç Ünal 2026 uzun ad".startsWith(back.name))
    }

    @Test
    fun unknownParametersAreIgnoredAndOrderIsFree() {
        val qr = PairingQr(id, key, token, "A")
        val p = qr.toUri().substringAfter('?').split('&').reversed().joinToString("&")
        val back = ok("weem://pair?x=9&$p&yeni=1")
        assertEquals(id, back.id)
        assertEquals("A", back.name)
    }

    @Test
    fun newerVersionAsksForAnUpdate() {
        val text = PairingQr(id, key, token, "A").toUri().replace("v=1", "v=2")
        assertEquals(QrResult.Newer, PairingQr.parse(text))
    }

    @Test
    fun invalidInputsAreRejected() {
        val good = PairingQr(id, key, token, "A").toUri()
        val bad = listOf(
            "",
            "https://example.com/pair?v=1",
            good.replace("weem://pair", "weem://pairx"),
            good.replace("v=1", "v=x"),
            good.replace("&id=0f0e0d0c-0b0a-4908-8706-050403020100", "&id=zz"),
            good.replace(Regex("&k=[^&]+"), "&k=AAAA"),
            good.replace(Regex("&k=[^&]+"), "&k=" + B64.url(ByteArray(65))),
            good.replace(Regex("&t=[^&]+"), "&t=" + B64.url(ByteArray(16))),
            good.replace(Regex("&t=[^&]+"), "&t=%%%"),
            good.replace(Regex("&id=[^&]+"), ""),
            "weem://pair?" + "a=b&".repeat(400),
        )
        for (b in bad) assertEquals(b, QrResult.Invalid, PairingQr.parse(b))
    }

    @Test
    fun toStringHidesTheToken() {
        val qr = PairingQr(id, key, token, "A")
        assertFalse(qr.toString().contains(B64.url(token)))
        assertFalse(Trust.Issuer(token, 1_000).toString().contains(B64.url(token)))
        assertFalse(Trust.Scanner(qr, 1_000).toString().contains(B64.url(token)))
    }

    @Test
    fun fingerprintIsTenBytesOfSha256InGroups() {
        val fp = Fingerprint.of(key)
        assertTrue(fp, Regex("[0-9A-F]{4}( [0-9A-F]{4}){4}").matches(fp))
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(key).copyOf(10).hex().uppercase()
        assertEquals(sha, fp.replace(" ", ""))
    }

    @Test
    fun peerInfoRoundTripAndForwardCompatibility() {
        val info = PeerInfo(id, 1, 3, 4_000_000_123L, "1.2.3-beta", "POCO X3 NFC")
        val bytes = info.encode()
        assertEquals(info, PeerInfo.parse(bytes))
        // Yeni sürüm sona alan ekleyebilir: fazlası yok sayılır.
        assertEquals(info, PeerInfo.parse(bytes + byteArrayOf(9, 9, 9)))
        assertEquals(null, PeerInfo.parse(bytes.copyOf(bytes.size - 1)))
        assertEquals(null, PeerInfo.parse(ByteArray(10)))
        val long = PeerInfo(id, 1, 0, 1, "ş".repeat(40), "ğ".repeat(40))
        val back = PeerInfo.parse(long.encode())!!
        assertTrue(back.appName.toByteArray().size <= 32 && back.deviceName.toByteArray().size <= 32)
    }
}
