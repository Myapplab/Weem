package com.kuryelog.logic.link

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * LINK.md 3.2, 3.14: Noise çekirdeği resmî vektörleri birebir üretir (cacophony `XX` + `XXpsk3`, snow `XX`; X25519 yalnız testte).
 * Böylece el sıkışma, HKDF, nonce düzeni ve `Split` Noise'un kendisi; ana koddaki P-256 yalnız DH işlevini değiştirir.
 */
class NoiseVectorsTest {
    @Test
    fun officialVectorsByteForByte() {
        val vectors = NoiseVectorFiles.load("/noise/official-25519.json").getJSONArray("vectors")
        assertEquals(3, vectors.length())
        val seen = HashSet<String>()
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            NoiseVectorFiles.play(v, X25519TestDh) { hex -> X25519TestDh.keyPair(hex.unhex()) }
            seen += v.getString("source") + ":" + v.getString("protocol_name")
        }
        assertEquals(
            setOf("cacophony:Noise_XX_25519_AESGCM_SHA256", "cacophony:Noise_XXpsk3_25519_AESGCM_SHA256", "snow:Noise_XX_25519_AESGCM_SHA256"),
            seen,
        )
    }

    @Test
    fun tamperedHandshakeMessageFails() {
        val v = NoiseVectorFiles.load("/noise/official-25519.json").getJSONArray("vectors").getJSONObject(0)
        val (init, resp) = NoiseVectorFiles.pair(v, X25519TestDh) { field -> X25519TestDh.keyPair(v.getString(field).unhex()) }
        val m1 = init.writeMessage(ByteArray(0))
        resp.readMessage(m1)
        val m2 = resp.writeMessage(ByteArray(0))
        m2[m2.size - 1] = (m2[m2.size - 1].toInt() xor 1).toByte()
        try {
            init.readMessage(m2)
            fail("değiştirilmiş msg2 çözülmemeliydi")
        } catch (e: NoiseException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }

    @Test
    fun cipherStateNonceLayoutAndReplay() {
        val k = ByteArray(32) { it.toByte() }
        val tx = CipherState().apply { initializeKey(k) }
        val rx = CipherState().apply { initializeKey(k) }
        val c0 = tx.encrypt(ByteArray(0), "a".toByteArray())
        val c1 = tx.encrypt(ByteArray(0), "b".toByteArray())
        assertEquals(17, c0.size)
        assertArrayEquals("a".toByteArray(), rx.decrypt(ByteArray(0), c0))
        // Aynı ileti ikinci kez: sayaç ilerledi, çözülmez; başarısız deneme sayacı ilerletmez.
        try {
            rx.decrypt(ByteArray(0), c0)
            fail("tekrar çözülmemeli")
        } catch (e: NoiseException) {
            // beklenen
        }
        assertArrayEquals("b".toByteArray(), rx.decrypt(ByteArray(0), c1))
        assertEquals(2L, rx.nonce)
    }
}

/** Vektör dosyaları: iletiler başlatan/yanıtlayan sırayla, başlatanla başlar (cacophony biçimi). */
object NoiseVectorFiles {
    fun load(resource: String): JSONObject {
        val text = NoiseVectorFiles::class.java.getResourceAsStream(resource)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        return JSONObject(text)
    }

    fun pattern(protocol: String): NoisePattern = when (protocol.split('_')[1]) {
        "XX" -> NoisePattern.XX
        "XXpsk3" -> NoisePattern.XX_PSK3
        else -> error(protocol)
    }

    /** [keys] = özel anahtar hex → anahtar çifti (P-256 vektörlerinde açık anahtar ayrıca verilir). */
    fun pair(v: JSONObject, dh: NoiseDh, keys: (String) -> DhKeyPair): Pair<HandshakeState, HandshakeState> {
        val p = pattern(v.getString("protocol_name"))
        fun psk(field: String): ByteArray? = (v.optJSONArray(field) ?: JSONArray()).let { if (it.length() == 0) null else it.getString(0).unhex() }
        val ie = keys("init_ephemeral")
        val re = keys("resp_ephemeral")
        val init = HandshakeState(p, dh, initiator = true, prologue = v.getString("init_prologue").unhex(), s = keys("init_static"), psk = psk("init_psks"), ephemeral = { ie })
        val resp = HandshakeState(p, dh, initiator = false, prologue = v.getString("resp_prologue").unhex(), s = keys("resp_static"), psk = psk("resp_psks"), ephemeral = { re })
        assertEquals(v.getString("protocol_name"), init.protocolName)
        return init to resp
    }

    fun play(v: JSONObject, dh: NoiseDh, privateHex: (String) -> DhKeyPair) {
        val (init, resp) = pair(v, dh) { field -> privateHex(v.getString(field)) }
        playPair(v, init, resp)
    }

    fun playPair(v: JSONObject, init: HandshakeState, resp: HandshakeState) {
        val name = v.optString("source", v.optString("name")) + " " + v.getString("protocol_name")
        val messages = v.getJSONArray("messages")
        var iSession: NoiseSession? = null
        var rSession: NoiseSession? = null
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val payload = m.getString("payload").unhex()
            val fromInit = i % 2 == 0
            val ct: ByteArray
            if (iSession == null) {
                val (w, r) = if (fromInit) init to resp else resp to init
                ct = w.writeMessage(payload)
                assertArrayEquals("$name ileti $i yük", payload, r.readMessage(ct))
                if (w.complete) {
                    assertTrue(r.complete)
                    if (v.has("handshake_hash")) assertEquals("$name özet", v.getString("handshake_hash"), init.handshakeHash().hex())
                    assertArrayEquals(init.handshakeHash(), resp.handshakeHash())
                    iSession = init.session()
                    rSession = resp.session()
                }
            } else {
                ct = if (fromInit) iSession.send.encrypt(ByteArray(0), payload) else rSession!!.send.encrypt(ByteArray(0), payload)
                val back = if (fromInit) rSession!!.receive.decrypt(ByteArray(0), ct) else iSession.receive.decrypt(ByteArray(0), ct)
                assertArrayEquals("$name taşıma $i", payload, back)
            }
            assertEquals("$name ileti $i", m.getString("ciphertext"), ct.hex())
        }
    }
}

fun String.unhex(): ByteArray {
    require(length % 2 == 0)
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
