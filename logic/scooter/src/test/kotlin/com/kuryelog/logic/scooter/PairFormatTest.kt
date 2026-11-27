package com.kuryelog.logic.scooter

import com.kuryelog.logic.scooter.ProtocolTest.Companion.AUTH
import com.kuryelog.logic.scooter.ProtocolTest.Companion.NAME
import com.kuryelog.logic.scooter.ProtocolTest.Companion.challenge
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** S45: G3 SET_PWD çerçevesi 32 bayt; ilk 16 bayt saklanan (AUTH) anahtar. */
class PairFormatTest {
    @Test fun setPwdCarries32BytesWithKeyFirst() {
        val p = ProtocolTest.Port()
        val s = NinebotSession(p, String(NAME, Charsets.US_ASCII), null, true, 0)
        challenge(s)
        val peer = NinebotCrypto(NAME, AUTH, false)
        peer.start(AUTH)
        val frame = peer.decrypt(p.sent.last())
        assertEquals(0x5c, frame[5].toInt() and 255)
        assertEquals(32, frame[2].toInt() and 255)
        assertArrayEquals(p.key, frame.copyOfRange(7, 23))
    }
}
