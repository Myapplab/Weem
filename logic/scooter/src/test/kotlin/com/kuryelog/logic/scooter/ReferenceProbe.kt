package com.kuryelog.logic.scooter

/**
 * NT `ReferenceProbe.java` (yardımcı, suit değil; SCOOTER.md 13.1): `reference_parity.py`'nin sentetik stdin satırlarını Kotlin
 * uygulamasıyla yanıtlar; Python referansı (`nb_crypto.py`) aynı baytları bekler. CI dışı, elle çalıştırılır (13.1 komutu).
 * Synthetic stdin vectors only; no radio, user identifiers, or real passwords.
 */
object ReferenceProbe {
    private fun bytes(h: String): ByteArray = Timing.unhex(h)

    private fun reply(cmd: Int, index: Int, data: ByteArray): ByteArray {
        val p = NinebotFrames.request(4, cmd, index, data)
        p[3] = 4
        p[4] = 0x3e
        return p
    }

    private fun initial(name: ByteArray, fw: Boolean) = NinebotCrypto(name, if (fw) NinebotCrypto.FW else null, fw)

    @JvmStatic fun main(args: Array<String>) {
        val input = System.`in`.bufferedReader(Charsets.UTF_8)
        while (true) {
            val line = input.readLine() ?: break
            val a = line.split("\t")
            val op = a[0]; val fw = a[1] == "gen2_fw"; val name = bytes(a[2]); val auth = bytes(a[3]); val key = bytes(a[4])
            val time = a[5].toLong(); val counter = a[6].toInt(); val plain = bytes(a[7])
            if (op == "password") { println(Timing.hex(SessionKey.derive(auth, time))); continue }
            if (op.startsWith("session")) {
                val wires = ArrayList<String>(); var candidate: ByteArray? = null
                val s = NinebotSession(object : NinebotSession.Port {
                    override fun available(profile: Int) = profile == 0
                    override fun send(profile: Int, wire: ByteArray) { if (profile != 0) throw AssertionError("profile"); wires.add(Timing.hex(wire)) }
                    override fun state(state: String) {}
                    override fun note(kind: String, detail: String) {}
                    override fun fault(reason: String) { throw AssertionError(reason) }
                    override fun key(key: ByteArray, confirmed: Boolean) { candidate = key.clone() }
                    override fun reading(value: NinebotMetrics.Value, bytes: ByteArray) {}
                }, String(name, Charsets.UTF_8), null, true, 0, NinebotMode.GEN2_FW, 300000, { time })
                val challenge = auth.copyOf(30); System.arraycopy(name.copyOf(14), 0, challenge, 16, 14)
                s.receive(initial(name, fw).encrypt(reply(0x5b, 1, challenge)), 100)
                val peer = NinebotCrypto(name, auth, false); peer.start(auth); val payloadProbe = op == "session_payload"
                s.receive(peer.encrypt(reply(0x5c, if (payloadProbe) 0 else 1, if (payloadProbe) byteArrayOf(0, 0xa1.toByte()) else ByteArray(0))), 200)
                peer.setKey(candidate!!, auth); peer.encrypt(reply(0x5d, 1, ByteArray(0))); s.receive(peer.encrypt(reply(0x5d, 1, ByteArray(0))), 300)
                if (s.stage != NinebotSession.Stage.READING) throw AssertionError("not authenticated")
                s.tick(400)
                println(wires.joinToString(","))
                continue
            }
            val c = initial(name, fw)
            if (op == "sn_name" || op == "sn_key") {
                c.setKey(if (op == "sn_name") name else key, auth); c.start(auth)
                val tx = NinebotCrypto::class.java.getDeclaredField("tx"); tx.isAccessible = true; tx.setInt(c, counter)
            }
            println(Timing.hex(c.encrypt(plain)))
        }
    }
}
