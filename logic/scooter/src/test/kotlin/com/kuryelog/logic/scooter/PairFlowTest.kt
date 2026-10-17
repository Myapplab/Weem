package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test

/** NT `PairFlowTest.java` (S1; 26 çağrı, 37 çalışma). Synthetic authenticated peer: pending, malformed routes, payload probe and full window. */
class PairFlowTest : NinebotSuite() {
    private class Port : NinebotSession.Port {
        val notes = ArrayList<String>()
        val wires = ArrayList<ByteArray>()
        var candidate: ByteArray? = null
        var saves = 0
        var promotions = 0
        var prompts = 0
        var visible = false
        override fun available(profile: Int) = profile == 0
        override fun send(profile: Int, wire: ByteArray) { wires.add(wire.clone()) }
        override fun state(state: String) {}
        override fun note(kind: String, detail: String) { notes.add("$kind:$detail") }
        override fun fault(reason: String) { throw AssertionError(reason) }
        override fun key(key: ByteArray, confirmed: Boolean) { candidate = key.clone(); if (confirmed) promotions++ else saves++ }
        override fun reading(value: NinebotMetrics.Value, bytes: ByteArray) { throw AssertionError("no read before successful AUTH") }
        override fun pairPrompt(visible: Boolean) { this.visible = visible; if (visible) prompts++ }
        fun last(k: String): String = notes.lastOrNull { it.startsWith("$k:") } ?: ""
    }

    @Test fun pairFlowAssertions() {
        var p = Port(); var s = pairing(p, 0, 300000)
        val badMac = wire(NAME, 4, 0x5c, 0, ByteArray(0), 4); badMac[7] = (badMac[7].toInt() xor 1).toByte(); s.receive(badMac, 200)
        check(p.prompts == 0 && p.last("pair_reply").isEmpty(), "bad MAC cannot prompt or produce trusted decoded fields")
        s.receive(wire(NAME, 7, 0x5c, 0, ByteArray(0), 4), 300)
        check(p.prompts == 0 && p.last("pair_reply").contains("status=invalid_source_or_length"), "wrong source logged and rejected before alert")
        s.receive(wire(NAME, 4, 0x92, 0, ByteArray(0), 6), 400)
        check(s.stage == NinebotSession.Stage.PAIR && p.prompts == 0, "literal hex 92 is not decimal PAIR 92")
        val first = wire(NAME, 4, 0x5c, 0, ByteArray(0), 8); s.receive(first, 500)
        check(p.prompts == 1 && p.visible, "first valid PAIR triggers prompt")
        val detail = p.last("pair_reply"); for (field in arrayOf("src=4;", "dst=62;", "cmd=92;", "cmd_hex=0x5c;", "index=0;", "length=0;", "payload_length=0;", "payload_hex=;", "wire_length=13;", "counter=8;", "status=waiting;", "mac_valid=true;")) check(detail.contains(field), "decoded field $field")
        s.receive(first, 501); s.receive(wire(NAME, 4, 0x5c, 0, ByteArray(0), 10), 1000)
        check(p.prompts == 1 && p.promotions == 0 && s.stage == NinebotSession.Stage.PAIR, "replay and repeat pending never re-alert or promote")
        s.receive(wire(NAME, 4, 0x5c, 2, ByteArray(0), 12), 1200)
        check(s.stage == NinebotSession.Stage.PAIR && p.last("pair_reply").contains("status=unknown_status"), "undocumented index does not falsely claim scooter rejection")
        s.receive(wire(NAME, 4, 0x5c, 0, byteArrayOf(1, 0xff.toByte(), 0xaa.toByte()), 14), 2000)
        check(s.stage == NinebotSession.Stage.AUTH && p.promotions == 0, "first nonempty payload probes AUTH without promoting candidate")
        check(p.last("pair_reply").contains("payload_hex=01ffaa;") && p.last("pair_reply").contains("payload_length=3;"), "nonempty PAIR payload retained exactly")
        check(p.last("pair_to_auth").contains("reason=nonempty_payload;") && p.last("pair_to_auth").contains("experimental=true;"), "payload transition explicitly experimental")
        val candidate = p.candidate!!.clone(); s.receive(wire(candidate, 4, 0x5d, 0, ByteArray(0), 16), 2100)
        check(s.stage == NinebotSession.Stage.PAIR && p.promotions == 0 && p.saves == 1 && candidate.contentEquals(p.candidate), "AUTH rejection resumes same candidate instead of ending window")
        check(p.last("auth_reply").contains("status=rejected") && p.last("pair_resumed").contains("deadline_mono_ms=300000;"), "documented AUTH rejection and original deadline logged")
        s.receive(wire(NAME, 4, 0x5c, 0, byteArrayOf(2), 18), 2300)
        check(s.stage == NinebotSession.Stage.PAIR && p.prompts == 1, "payload probe performed only once, no alert storm")
        s.tick(299999); check(s.stage == NinebotSession.Stage.PAIR, "pending keeps full 300 second window"); val sends = p.wires.size; s.tick(300000); s.tick(310000)
        check(s.stage == NinebotSession.Stage.FAILED && !p.visible && p.wires.size == sends, "exact window end hides prompt and stops pairing TX")
        p = Port(); s = pairing(p, 0, 300000); s.receive(wire(NAME, 4, 0x5c, 0, byteArrayOf(9), 4), 1000); s.tick(6000); s.tick(11000); s.tick(16000)
        check(s.stage == NinebotSession.Stage.PAIR && p.saves == 1 && p.last("pair_resumed").contains("AUTH timeout"), "three timed-out AUTH attempts preserve remaining pairing window")
        s.receive(wire(NAME, 4, 0x5c, 1, ByteArray(0), 20), 17000); check(s.stage == NinebotSession.Stage.AUTH, "standard empty index one still enters AUTH after probe timeout")
        s.receive(wire(p.candidate!!, 7, 0x5d, 1, ByteArray(0), 22), 17500); check(p.promotions == 0 && s.stage == NinebotSession.Stage.AUTH, "wrong source AUTH cannot promote")
        s.receive(wire(p.candidate!!, 4, 0x5d, 1, ByteArray(0), 24), 18000); check(s.stage == NinebotSession.Stage.READING && p.promotions == 1 && !p.visible, "verified AUTH alone promotes and clears prompt")
        p = Port(); s = pairing(p, 120000, 180000); s.receive(wire(NAME, 4, 0x5c, 0, ByteArray(0), 4), 121000); s.tick(299999); check(s.stage == NinebotSession.Stage.PAIR, "reconnect with remaining window does not expire early"); s.tick(300000); check(s.stage == NinebotSession.Stage.FAILED, "remaining window cannot reset total deadline")
        p = Port(); s = pairing(p, 0, 300000); s.receive(wire(NAME, 4, 0x5c, 0, byteArrayOf(7), 4), 299999); s.receive(wire(p.candidate!!, 4, 0x5d, 1, ByteArray(0), 6), 300000); check(p.promotions == 0 && s.stage == NinebotSession.Stage.FAILED, "AUTH response after deadline cannot extend pairing window")
        p = Port(); s = pairing(p, 0, 300000); s.receive(wire(NAME, 4, 0x5c, 0, ByteArray(0), 4), 200); s.close(); s.tick(3000); s.receive(wire(NAME, 4, 0x5c, 1, ByteArray(0), 6), 4000); check(s.stage == NinebotSession.Stage.CLOSED && !p.visible && p.promotions == 0 && p.wires.size == 2, "user stop cancels pending and prevents AUTH")
        // The production key supplier changes only newly requested pairing, not the wire protocol.
        val secure = SessionKey.random(); val generated = intArrayOf(0); p = Port()
        s = NinebotSession(p, String(NAME, Charsets.US_ASCII), null, true, 0, NinebotMode.GEN2_FW, 300000, { 1700000000123L }, null, { generated[0]++; secure.clone() })
        val pre = CHALLENGE.copyOf(30); System.arraycopy(NAME.copyOf(14), 0, pre, 16, 14)
        s.receive(NinebotCrypto(NAME, NinebotCrypto.FW, true).encrypt(reply(4, 0x5b, 1, pre)), 100)
        check(secure.size == 16 && generated[0] == 1 && secure.contentEquals(p.candidate), "new pair uses supplied secure 16-byte key once")
        s.receive(wire(NAME, 4, 0x5c, 1, ByteArray(0), 4), 200); s.receive(wire(secure, 4, 0x5d, 1, ByteArray(0), 6), 300)
        check(s.stage == NinebotSession.Stage.READING && p.promotions == 1, "random candidate completes synthetic authenticated handshake")
        p = Port(); s = NinebotSession(p, String(NAME, Charsets.US_ASCII), secure, false, 0, NinebotMode.GEN2_FW, 300000, { 0L }, null, { throw AssertionError("stored key must not rotate") })
        s.receive(NinebotCrypto(NAME, NinebotCrypto.FW, true).encrypt(reply(4, 0x5b, 1, pre)), 100)
        check(s.stage == NinebotSession.Stage.AUTH, "normal connection reuses stored key")
        assertEquals("PAIR flow assertions passed; no physical scooter was used", 37, count)
    }

    private companion object {
        val NAME: ByteArray = "TEST-G3-000001".toByteArray(Charsets.US_ASCII)
        val CHALLENGE = ByteArray(16)

        fun reply(src: Int, cmd: Int, index: Int, payload: ByteArray): ByteArray {
            val p = NinebotFrames.request(src, cmd, index, payload)
            p[3] = src.toByte()
            p[4] = 0x3e
            return p
        }

        fun wire(key: ByteArray, src: Int, cmd: Int, index: Int, payload: ByteArray, counter: Int): ByteArray {
            val c = NinebotCrypto(key, CHALLENGE, false)
            c.start(CHALLENGE)
            val tx = NinebotCrypto::class.java.getDeclaredField("tx")
            tx.isAccessible = true
            tx.setInt(c, counter - 1)
            return c.encrypt(reply(src, cmd, index, payload))
        }

        fun pairing(p: Port, now: Long, window: Long): NinebotSession {
            val s = NinebotSession(p, String(NAME, Charsets.US_ASCII), null, true, now, NinebotMode.GEN2_FW, window, { 1700000000123L })
            val data = CHALLENGE.copyOf(30)
            System.arraycopy(NAME.copyOf(14), 0, data, 16, 14)
            s.receive(NinebotCrypto(NAME, NinebotCrypto.FW, true).encrypt(reply(4, 0x5b, 1, data)), now + 100)
            return s
        }
    }
}
