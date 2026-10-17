package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/**
 * NT `ProtocolTest.java` (S1; 55 çağrı, 408 çalışma): şifreleme vektörleri, sağlama, sayaç/tekrar/düşürme reddi,
 * PRE_COMM→AUTH→READING, okuma koruması, kayıtlı anahtarla AUTH, PRE_COMM 3 deneme, sweep çözme, birleştirme sınırları. Port her
 * notta ve gönderimde de denetler: çalışma sayısı oturumun not sırasının aynı olduğunu da gösterir.
 */
class ProtocolTest : NinebotSuite() {
    private fun rejects(r: () -> Unit, label: String) {
        val thrown: RuntimeException? = try {
            r()
            null
        } catch (e: IllegalArgumentException) {
            e
        } catch (e: IllegalStateException) {
            e
        }
        if (thrown == null) throw AssertionError(label)
        check(true, label)
    }

    private inner class Port : NinebotSession.Port {
        val sent = ArrayList<ByteArray>()
        val order = ArrayList<String>()
        val notes = ArrayList<String>()
        val values = ArrayList<NinebotMetrics.Value>()
        var key: ByteArray? = null
        var confirmed = 0
        var exhausted = 0
        var profiles = true
        override fun available(profile: Int) = profiles || profile == 0
        override fun send(profile: Int, wire: ByteArray) { check(profile == 0, "only profile zero transmitted"); sent.add(wire); order.add("send") }
        override fun state(state: String) {}
        override fun note(kind: String, detail: String) { notes.add("$kind:$detail"); check(!detail.contains(Timing.hex(KEY)), "no plaintext key in notes") }
        override fun fault(reason: String) {}
        override fun authExhausted() { exhausted++ }
        override fun key(key: ByteArray, confirmed: Boolean) { this.key = key.clone(); order.add(if (confirmed) "confirmed" else "stored"); if (confirmed) this.confirmed++ }
        override fun reading(value: NinebotMetrics.Value, bytes: ByteArray) { values.add(value) }
    }

    private fun challenge(s: NinebotSession) {
        s.receive(pre().encrypt(reply(4, 0x5b, 1, challenge())), 100)
    }

    /** The read the session just requested, recovered from its plaintext protocol_tx note (the schedule is not a fixed list). */
    private fun lastRead(p: Port, known: List<NinebotMetrics.Read>): NinebotMetrics.Read? {
        for (i in p.notes.size - 1 downTo 0) {
            val n = p.notes[i]
            if (!n.startsWith("protocol_tx:") || !n.contains("stage=READING")) continue
            val board = field(n, "board")
            val index = field(n, "index")
            for (r in known) if (r.board == board && r.index == index) return r
            throw AssertionError("unknown read $n")
        }
        return null
    }

    @Test fun protocolAssertions() {
        // Known-answer vectors were computed independently with ha-ninebot's Python crypto.
        val preFrame = NinebotFrames.request(4, 0x5b, 0, ByteArray(0))
        check(Timing.hex(NinebotCrypto(NAME, null, false).encrypt(preFrame)) == "5aa5009431798e000062ff0000", "non-SN zero known answer")
        check(Timing.hex(pre().encrypt(preFrame)) == "5aa500f0516026000062ff0000", "non-SN FW known answer")
        val sender = sn(); val auth = sender.encrypt(NinebotFrames.request(4, 0x5d, 0, NAME.copyOf(14)))
        check(Timing.hex(auth) == "5aa50e2329a80a18335734c62603ebf9a8bc172ec14f2fd2430002", "SN AUTH known answer"); val data = ByteArray(32) { it.toByte() }
        val multi = sender.encrypt(reply(0x16, 4, 0x62, data)); check(Timing.hex(multi) == "5aa520521c492b51850e3762a781461c4ad766d0fe409cf6dfc11b741c61b6d5fb2ffa07543d80583379990003", "multi-block known answer")
        val receiver = sn(); val damaged = auth.clone(); damaged[9] = (damaged[9].toInt() xor 1).toByte(); rejects({ receiver.decrypt(damaged) }, "bad MAC rejected"); check(receiver.decrypt(auth).contentEquals(NinebotFrames.request(4, 0x5d, 0, NAME.copyOf(14))), "failed MAC does not consume counter"); rejects({ receiver.decrypt(auth) }, "replay rejected"); check(receiver.decrypt(multi).contentEquals(reply(0x16, 4, 0x62, data)), "next counter succeeds")
        val bad = pre().encrypt(preFrame); bad[bad.size - 4] = (bad[bad.size - 4].toInt() xor 1).toByte(); rejects({ pre().decrypt(bad) }, "bad non-SN checksum"); rejects({ sn().decrypt(pre().encrypt(preFrame)) }, "no counter downgrade"); rejects({ pre().decrypt(auth.copyOf(10)) }, "truncated wire")
        val tx = NinebotCrypto::class.java.getDeclaredField("tx"); tx.isAccessible = true; val exhausted = sn(); tx.setInt(exhausted, 65535); rejects({ exhausted.encrypt(preFrame) }, "reconnect before counter wrap")
        val advancedPeer = sn(); val advancedClient = sn(); tx.setInt(advancedPeer, 25); val pending26 = advancedPeer.encrypt(reply(4, 0x5c, 0, ByteArray(0))); val tampered26 = pending26.clone(); tampered26[5] = (tampered26[5].toInt() xor 1).toByte(); rejects({ advancedClient.decrypt(tampered26) }, "forged higher counter rejected"); val unchanged2 = advancedClient.encrypt(preFrame); check((((unchanged2[unchanged2.size - 2].toInt() and 255) shl 8) or (unchanged2[unchanged2.size - 1].toInt() and 255)) == 2, "forged receive cannot advance outgoing counter")
        advancedClient.decrypt(pending26); val next27 = advancedClient.encrypt(preFrame); check((((next27[next27.size - 2].toInt() and 255) shl 8) or (next27[next27.size - 1].toInt() and 255)) == 27, "outgoing counter advances after verified pending reply"); rejects({ advancedClient.decrypt(pending26) }, "counter synchronization keeps replay check")
        for (cut in 1 until multi.size) { val f = NinebotFrames(); check(f.feed(multi.copyOfRange(0, cut), 0).isEmpty(), "fragment incomplete"); val out = f.feed(multi.copyOfRange(cut, multi.size), 10); check(out.size == 1 && out[0].contentEquals(multi), "all fragment boundaries") }
        val two = auth.copyOf(auth.size + multi.size); System.arraycopy(multi, 0, two, auth.size, multi.size); check(NinebotFrames().feed(two, 0).size == 2, "concatenated frames"); val partial = NinebotFrames(); partial.feed(multi.copyOf(8), 0); check(partial.feed(auth, 4000).size == 1, "stale fragment reset")
        val mileage = NinebotMetrics.vehicle()[2]; check(NinebotMetrics.decode(mileage, byteArrayOf(0x98.toByte(), 0xa5.toByte(), 0, 0)) == 4239.2, "dashboard odometer u32 scale"); check(NinebotMetrics.decode(mileage, byteArrayOf(0, 0, 1, 0)) == 6553.6, "no u16 mileage wrap"); val current = NinebotMetrics.battery(7)[1]; check(NinebotMetrics.decode(current, byteArrayOf(0x88.toByte(), 0xff.toByte())) == -1.2, "signed battery current"); check(NinebotMetrics.decode(mileage, byteArrayOf(0, 0)) == null, "incomplete metric null"); check(NinebotMetrics.decode(NinebotMetrics.vehicle()[0], byteArrayOf(101, 0)) == null, "invalid percent null")
        var p = Port(); var s = NinebotSession(p, String(NAME, Charsets.US_ASCII), null, false, 0); challenge(s); check(s.stage == NinebotSession.Stage.NEEDS_KEY && p.sent.size == 1 && p.key == null, "normal recording never changes pairing key")
        p = Port(); s = NinebotSession(p, String(NAME, Charsets.US_ASCII), KEY, false, 0); challenge(s); check(s.stage == NinebotSession.Stage.AUTH && p.key == null, "saved key skips pairing"); val peer = sn(); s.receive(peer.encrypt(reply(4, 0x5d, 1, ByteArray(0))), 200); check(s.stage == NinebotSession.Stage.READING && p.confirmed == 1, "authenticated state"); s.tick(300); val asked = lastRead(p, known(null))!!; check(asked.board == 0x16 && asked.index == 0x57, "first read after AUTH is the fast speed candidate"); s.receive(peer.encrypt(reply(7, 4, 0x57, byteArrayOf(8, 0))), 400); check(p.values.isEmpty(), "wrong board cannot become measurement"); s.receive(peer.encrypt(reply(0x16, 4, 0x5f, byteArrayOf(8, 0))), 450); check(p.values.isEmpty(), "wrong register cannot become measurement"); s.receive(peer.encrypt(reply(0x16, 4, 0x57, byteArrayOf(8, 0))), 500); check(p.values.size == 1 && p.values[0].source.key == "scooter_speed_kmh" && abs(p.values[0].value - 0.8) < 1e-9, "correlated authenticated measurement"); s.close(); check(s.metrics.values().isEmpty(), "disconnect clears measurements")
        p = Port(); s = NinebotSession(p, String(NAME, Charsets.US_ASCII), KEY, false, 0); challenge(s); s.tick(5200); s.tick(10300); s.tick(15400); check(s.stage == NinebotSession.Stage.FAILED && p.key == null && p.sent.size == 4 && p.exhausted == 1, "three AUTH attempts keep key; no SET_PWD; authExhausted signalled once")
        p = Port(); s = NinebotSession(p, String(NAME, Charsets.US_ASCII), null, true, 0); challenge(s); check(s.stage == NinebotSession.Stage.PAIR && p.key!!.size == 16 && p.order.indexOf("stored") < p.order.lastIndexOf("send"), "pair key durable before sending"); val pairPeer = NinebotCrypto(NAME, AUTH, false); pairPeer.start(AUTH); s.receive(pairPeer.encrypt(reply(4, 0x5c, 1, ByteArray(0))), 200); check(s.stage == NinebotSession.Stage.AUTH, "pair confirmation proceeds to AUTH"); val pairAuth = NinebotCrypto(p.key!!, AUTH, false); pairAuth.start(AUTH); pairAuth.encrypt(reply(4, 0x5d, 1, ByteArray(0))); s.receive(pairAuth.encrypt(reply(4, 0x5d, 1, ByteArray(0))), 300); check(s.stage == NinebotSession.Stage.READING, "fresh pairing authenticated")
        p = Port(); s = NinebotSession(p, String(NAME, Charsets.US_ASCII), null, true, 0); challenge(s); val waitingPeer = NinebotCrypto(NAME, AUTH, false); waitingPeer.start(AUTH); tx.setInt(waitingPeer, 5); s.receive(waitingPeer.encrypt(reply(4, 0x5c, 0, ByteArray(0))), 1000); tx.setInt(waitingPeer, 25); s.receive(waitingPeer.encrypt(reply(4, 0x5c, 0, ByteArray(0))), 2000); check(s.stage == NinebotSession.Stage.PAIR && p.confirmed == 0 && p.values.isEmpty(), "pending replies never count as authenticated telemetry"); s.tick(2700); val retry = p.sent[p.sent.size - 1]; check((((retry[retry.size - 2].toInt() and 255) shl 8) or (retry[retry.size - 1].toInt() and 255)) == 27, "pair retry follows latest validated device counter"); s.tick(121000); check(s.stage == NinebotSession.Stage.PAIR, "button window extends beyond old two minute limit"); s.tick(300001); check(s.stage == NinebotSession.Stage.FAILED && p.confirmed == 0, "unconfirmed pair bounded to five minutes")
        p = Port(); p.profiles = false; s = NinebotSession(p, String(NAME, Charsets.US_ASCII), null, false, 0); s.tick(5100); s.tick(10200); s.tick(15300); check(s.stage == NinebotSession.Stage.FAILED && p.sent.size == 3 && s.profile == 0, "same profile PRE_COMM retry bounded; no profile loop")
        p = Port(); s = NinebotSession(p, String(NAME, Charsets.US_ASCII), KEY, false, 0); val notStored = pre().encrypt(reply(4, 0x5b, 0, challenge())); s.receive(notStored, 100); check(s.stage == NinebotSession.Stage.NEEDS_KEY && p.sent.size == 1, "local key cannot skip no-password device flag")
        p = Port(); s = NinebotSession(p, String(NAME, Charsets.US_ASCII), null, true, 90000, NinebotMode.GEN2_FW, 300000, { 1700000000123L }); s.receive(pre().encrypt(reply(4, 0x5b, 1, challenge())), 90100); check(s.stage == NinebotSession.Stage.PAIR, "fixed FW PRE_COMM and pair"); s.tick(210000); check(s.stage == NinebotSession.Stage.PAIR, "deadline begins at delayed protocol creation"); s.tick(390000); check(s.stage == NinebotSession.Stage.FAILED, "deadline does not restart after challenge")
        p = Port(); s = NinebotSession(p, String(NAME, Charsets.US_ASCII), null, false, 0); val unexpected = sn().encrypt(reply(4, 4, 1, byteArrayOf(0, 0))); s.receive(unexpected, 100); s.receive(unexpected, 101); val rejectsPort = p; check(p.notes.count { it.startsWith("protocol_rejected:") } == 2, "every rejection logged without throttling"); check(p.notes.any { it.contains("reason=unexpected session frame") && it.contains("stage=PRE_COMM") && it.contains("wire_hex=" + Timing.hex(unexpected)) }, "rejection includes frame and reason"); check(p.notes.any { it.startsWith("protocol_tx:") && it.contains("wire_hex=" + Timing.hex(rejectsPort.sent[0])) }, "full encrypted TX hex logged")
        val b5 = unexpected.clone(); b5[1] = 0xb5.toByte(); check(NinebotFrames().feed(b5, 0).size == 1, "unsupported B5 extracted for explicit crypto rejection"); s.receive(b5, 102); check(s.stage == NinebotSession.Stage.PRE_COMM, "unexpected frame cannot authenticate")
        check(NinebotMode.values().size == 1 && NinebotMode.parse("gen2_fw") == NinebotMode.GEN2_FW && NinebotMode.parse("gen3_zero") == NinebotMode.GEN2_FW, "fixed mode migrates legacy zero setting"); rejects({ NinebotMode.parse("guess") }, "unknown mode not guessed")
        // Opt-in register sweep: unanswered sweep registers must not count toward the measurement-loss teardown.
        // Answer whatever the session requests: dashboard board 0x16 answers, BMS voltages and sweep registers time out.
        val plan = known(NinebotMetrics.Sweep(0x16, 0x40, 0x4f))
        val sweepPort = Port(); val sweepSession = NinebotSession(sweepPort, String(NAME, Charsets.US_ASCII), KEY, false, 0, NinebotMode.GEN2_FW, 300000, System::currentTimeMillis, NinebotMetrics.Sweep(0x16, 0x40, 0x4f))
        challenge(sweepSession); val sweepPeer = sn(); sweepSession.receive(sweepPeer.encrypt(reply(4, 0x5d, 1, ByteArray(0))), 200); check(sweepSession.stage == NinebotSession.Stage.READING, "sweep session authenticates")
        var sweepNow = 400L
        for (step in 0 until plan.size * 3) {
            val before = sweepPort.sent.size; sweepSession.tick(sweepNow); if (sweepPort.sent.size == before) { sweepNow += 100; continue }; val r = lastRead(sweepPort, plan)!!
            if (r.board == 0x16 && !r.sweep) { sweepNow += 60; sweepSession.receive(sweepPeer.encrypt(reply(r.board, 4, r.index, sample(r))), sweepNow); sweepNow += 100 }
            else { sweepNow += 1300; sweepSession.tick(sweepNow); sweepNow += 100 }
        }
        check(sweepSession.stage == NinebotSession.Stage.READING, "answered mapped reads survive sweep and occasional voltage timeouts")
        check(sweepPort.notes.any { it.startsWith("read_timeout:") && it.contains("sweep=true") }, "sweep register timeout labelled")
        check(sweepPort.notes.any { it.startsWith("read_timeout:") && it.contains("sweep=false") }, "mapped register timeout labelled")
        for (step in 0 until plan.size + 13) { sweepSession.tick(sweepNow); sweepNow += 1300; sweepSession.tick(sweepNow); sweepNow += 100 }
        check(sweepSession.stage == NinebotSession.Stage.FAILED, "once mapped registers stop answering the session tears down")
        // Drive to the first sweep register and confirm its raw value is accepted and reported.
        val plan2 = known(NinebotMetrics.Sweep(0x16, 0x00, 0x05))
        val acceptPort = Port(); val acceptSession = NinebotSession(acceptPort, String(NAME, Charsets.US_ASCII), KEY, false, 0, NinebotMode.GEN2_FW, 300000, System::currentTimeMillis, NinebotMetrics.Sweep(0x16, 0x00, 0x05))
        challenge(acceptSession); val acceptPeer = sn(); acceptSession.receive(acceptPeer.encrypt(reply(4, 0x5d, 1, ByteArray(0))), 200); check(acceptSession.stage == NinebotSession.Stage.READING, "accept sweep session authenticates")
        var an = 300L; var firstSweep: NinebotMetrics.Read? = null; for (k in 0 until 40) { val before = acceptPort.sent.size; acceptSession.tick(an); if (acceptPort.sent.size == before) { an += 100; continue }; val r = lastRead(acceptPort, plan2)!!; firstSweep = r; an += 60; if (r.sweep) break; acceptSession.receive(acceptPeer.encrypt(reply(r.board, 4, r.index, sample(r))), an); an += 100 }
        check(firstSweep != null && firstSweep.sweep && firstSweep.index == 0, "first sweep register follows the first fast pass")
        acceptSession.receive(acceptPeer.encrypt(reply(0x16, 4, 0x00, byteArrayOf(0xd2.toByte(), 0x04))), an)
        check(acceptPort.values.any { it.source.key == "sweep_16_00" && it.value == 1234.0 }, "swept register value decoded and logged with raw")
        assertEquals("protocol assertions passed; no physical scooter was used", 408, count)
    }

    companion object {
        val NAME: ByteArray = "TEST-G3-000001".toByteArray(Charsets.US_ASCII)
        val AUTH = ByteArray(16) { it.toByte() }
        val KEY = ByteArray(16) { (16 + it).toByte() }

        fun reply(board: Int, cmd: Int, index: Int, data: ByteArray): ByteArray {
            val p = NinebotFrames.request(board, cmd, index, data)
            p[3] = board.toByte()
            p[4] = 0x3e
            return p
        }

        fun sn(): NinebotCrypto {
            val c = NinebotCrypto(KEY, AUTH, false)
            c.start(AUTH)
            return c
        }

        fun challenge(): ByteArray {
            val data = AUTH.copyOf(30)
            System.arraycopy(NAME.copyOf(14), 0, data, 16, 14)
            return data
        }

        fun pre(): NinebotCrypto = NinebotCrypto(NAME, NinebotCrypto.FW, true)

        fun field(note: String, name: String): Int {
            val m = Regex("; $name=(\\d+);").find(note) ?: throw AssertionError("no $name in $note")
            return m.groupValues[1].toInt()
        }

        fun known(sweep: NinebotMetrics.Sweep?): List<NinebotMetrics.Read> {
            val list = ArrayList(NinebotMetrics.vehicle())
            for (b in intArrayOf(7, 0x22, 0x23)) list.addAll(NinebotMetrics.battery(b))
            if (sweep != null) list.addAll(NinebotMetrics.sweep(sweep))
            return list
        }

        fun sample(r: NinebotMetrics.Read): ByteArray {
            val n: Long = if (r.key.endsWith("_voltage_v")) 5000 else 0
            val d = ByteArray(r.size)
            for (i in 0 until r.size) d[i] = ((n ushr (8 * i)) and 0xff).toByte()
            return d
        }
    }
}
