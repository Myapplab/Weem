package com.kuryelog.logic.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class RemoteControlTest {
    private val s = UUID.randomUUID()
    private val pub = ByteArray(65) { it.toByte() }

    @Test fun askOfferEndRoundTrip() {
        val a = RemoteControl.parseAsk(RemoteControl.ask(RemoteAsk(s, pub, 2400)))!!
        assertEquals(s, a.session)
        assertArrayEquals(pub, a.pub)
        assertEquals(2400, a.maxSide)
        val o = RemoteControl.parseOffer(RemoteControl.offer(RemoteOffer(s, RemoteOffer.State.READY, pub, 47312)))!!
        assertEquals(RemoteOffer.State.READY, o.state)
        assertEquals(47312, o.port)
        assertArrayEquals(pub, o.pub)
        for (st in listOf(RemoteOffer.State.CONSENT, RemoteOffer.State.REFUSED, RemoteOffer.State.UNAVAILABLE)) {
            assertEquals(st, RemoteControl.parseOffer(RemoteControl.offer(RemoteOffer(s, st)))!!.state)
        }
        for (r in RemoteEnd.Reason.entries) assertEquals(r, RemoteControl.parseEnd(RemoteControl.end(RemoteEnd(s, r)))!!.reason)
    }

    @Test fun brokenPayloadsAreDropped() {
        fun b(t: String) = t.toByteArray()
        assertNull(RemoteControl.parseAsk(b("{")))
        assertNull(RemoteControl.parseAsk(b("""{"schemaVersion":1,"session":"$s","pub":"AAAA","maxSide":1280}""")))
        assertNull(RemoteControl.parseAsk(RemoteControl.ask(RemoteAsk(s, pub, 100))))
        assertNull(RemoteControl.parseAsk(b("""{"schemaVersion":0,"session":"$s"}""")))
        assertNull(RemoteControl.parseOffer(b("""{"schemaVersion":1,"session":"$s","state":"ready"}""")))
        assertNull(RemoteControl.parseOffer(b("""{"schemaVersion":1,"session":"$s","state":"maybe"}""")))
        assertNull(RemoteControl.parseOffer(b("""{"schemaVersion":1,"session":"nope","state":"consent"}""")))
    }

    @Test fun unknownFieldsAndNewerSchemaAreAccepted() {
        val o = RemoteControl.parseOffer(b("""{"schemaVersion":2,"session":"$s","state":"consent","extra":[1,2]}"""))!!
        assertEquals(RemoteOffer.State.CONSENT, o.state)
        // Bilinmeyen bitirme nedeni hata sayılır (oturum yine biter).
        assertEquals(RemoteEnd.Reason.ERROR, RemoteControl.parseEnd(b("""{"schemaVersion":1,"session":"$s","reason":"later"}"""))!!.reason)
    }

    private fun b(t: String) = t.toByteArray()
}
