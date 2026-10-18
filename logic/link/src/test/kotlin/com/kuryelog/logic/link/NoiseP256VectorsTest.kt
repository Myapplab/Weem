package com.kuryelog.logic.link

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

/**
 * LINK.md 3.2, 3.14: WEEM'in P-256 Noise'u bağımsız Python uygulamasıyla (`tools/link-noise-reference.py`, `cryptography`/OpenSSL)
 * birebir aynı iletileri üretir: `XX`, `XXpsk3` (eşleştirme) ve rolleri değişmiş `XX`; prologue `WEEM link 2` + iki `HELLO`,
 * bilgi yükleri (msg3 ve yanıtlayanın onay iletisi) ve iki yönde taşıma iletileri.
 */
class NoiseP256VectorsTest {
    private val doc = NoiseVectorFiles.load("/noise/p256.json")

    private fun keys(v: org.json.JSONObject, field: String): DhKeyPair =
        P256.keyPair(BigInteger(v.getString(field), 16), v.getString(field + "_public").unhex())

    @Test
    fun pythonVectorsByteForByte() {
        val vectors = doc.getJSONArray("vectors")
        val names = ArrayList<String>()
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val (init, resp) = NoiseVectorFiles.pair(v, P256) { field -> keys(v, field) }
            NoiseVectorFiles.playPair(v, init, resp)
            names += v.getString("name") + ":" + v.getString("protocol_name")
        }
        assertEquals(
            listOf("xx:Noise_XX_P256_AESGCM_SHA256", "pair:Noise_XXpsk3_P256_AESGCM_SHA256", "xx-swapped:Noise_XX_P256_AESGCM_SHA256"),
            names,
        )
    }

    @Test
    fun prologueAndInfoLayoutMatchTheSpec() {
        val v = doc.getJSONArray("vectors").getJSONObject(1)
        val a = PeerId.of("11111111-1111-4111-8111-111111111111")!!
        val b = PeerId.of("22222222-2222-4222-8222-222222222222")!!
        val prologue = SecureHandshake.prologue(Frames.helloPayload(a, pairing = true), Frames.helloPayload(b, pairing = true))
        assertEquals(v.getString("init_prologue"), prologue.hex())
        val messages = v.getJSONArray("messages")
        assertEquals(0, messages.getJSONObject(0).getString("payload").length)
        assertEquals(0, messages.getJSONObject(1).getString("payload").length)
        // msg3: başlatanın bilgisi; 4. ileti: yanıtlayanın onayı + bilgisi (LINK.md 3.4)
        assertEquals(PeerInfo(a, 1, 0, 34, "1.0.0", "Pixel 10 Pro").encode().hex(), messages.getJSONObject(2).getString("payload"))
        assertEquals(PeerInfo(b, 1, 0, 34, "1.0.0", "POCO X3").encode().hex(), messages.getJSONObject(3).getString("payload"))
    }

    @Test
    fun ecdhKnownAnswer() {
        val e = doc.getJSONObject("ecdh")
        val a = P256.keyPair(BigInteger(e.getString("a_private"), 16), e.getString("a_public").unhex())
        val b = P256.keyPair(BigInteger(e.getString("b_private"), 16), e.getString("b_public").unhex())
        assertEquals(e.getString("shared"), P256.agree(a, b.public).hex())
        assertEquals(e.getString("shared"), P256.agree(b, a.public).hex())
    }
}
