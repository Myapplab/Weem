package com.kuryelog.logic.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** LINK.md 3.12: `weem-link` tercihleri + Keystore `WEEM.Link.v1` sarması (Android'de ana anahtar Keystore'da; burada yazılım AES). */
class IdentityVaultTest {
    private val random = SecureRandom()
    private val prefs = LinkPrefs.Memory()
    private var master: SecretKey = aes()
    private val vault = IdentityVault(prefs, { master }, random)
    private val peerId = PeerId.of("22222222-2222-4222-8222-222222222222")!!

    private fun aes(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun namesAreTheDataContract() {
        assertEquals("weem-link", IdentityVault.PREFS)
        assertEquals("WEEM.Link.v1", IdentityVault.ALIAS)
        assertEquals("weem-link/identity/v1", IdentityVault.AAD)
    }

    @Test
    fun createsOnceAndReloadsTheSameKey() {
        val first = vault.identity()
        assertTrue(first.created)
        assertFalse(first.replacedUnreadable)
        val again = IdentityVault(prefs, { master }, random).identity()
        assertFalse(again.created)
        assertArrayEquals(first.keys.public, again.keys.public)
        // Aynı özel anahtar: iki çiftle aynı DH sonucu.
        val other = P256.generate(random)
        assertArrayEquals(P256.agree(first.keys, other.public), P256.agree(again.keys, other.public))
        assertArrayEquals(first.keys.public, Base64.getDecoder().decode(prefs.getString("identity.public")))
    }

    @Test
    fun storedPrivateKeyIsWrappedWithTheAad() {
        val keys = vault.identity().keys
        val all = Base64.getDecoder().decode(prefs.getString("identity.key"))
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(128, all.copyOf(12)))
        c.updateAAD(IdentityVault.AAD.toByteArray())
        val pkcs8 = c.doFinal(all.copyOfRange(12, all.size))
        assertArrayEquals(P256.pkcs8(keys), pkcs8)
        val wrongAad = Cipher.getInstance("AES/GCM/NoPadding")
        wrongAad.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(128, all.copyOf(12)))
        wrongAad.updateAAD("başka".toByteArray())
        try {
            wrongAad.doFinal(all.copyOfRange(12, all.size))
            throw AssertionError("AAD olmadan açılmamalı")
        } catch (e: javax.crypto.AEADBadTagException) {
            // beklenen
        }
    }

    @Test
    fun unreadableKeyIsReplaced() {
        val first = vault.identity().keys
        val all = Base64.getDecoder().decode(prefs.getString("identity.key"))
        all[20] = (all[20].toInt() xor 1).toByte()
        prefs.edit().putString("identity.key", Base64.getEncoder().encodeToString(all)).commit()
        val again = vault.identity()
        assertTrue(again.replacedUnreadable)
        assertFalse(again.keys.public.contentEquals(first.public))
        // Keystore anahtarı değişti (uygulama verisi kısmen silindi): aynı sonuç.
        master = aes()
        assertTrue(vault.identity().replacedUnreadable)
    }

    @Test
    fun publicKeyNotMatchingThePrivateIsReplaced() {
        vault.identity()
        prefs.edit().putString("identity.public", Base64.getEncoder().encodeToString(P256.generate(random).public)).commit()
        assertTrue(vault.identity().replacedUnreadable)
    }

    @Test
    fun peerRecordSaveLoadRemove() {
        assertNull(vault.peer())
        val key = P256.generate(random).public
        val r = PeerRecord(peerId, key, "POCO X3", 1_760_000_000_000L)
        vault.savePeer(r)
        assertEquals(r, vault.peer())
        assertEquals(peerId.text, prefs.getString("peer.id"))
        assertEquals("POCO X3", prefs.getString("peer.name"))
        assertEquals(1_760_000_000_000L, prefs.getLong("peer.pairedAtMs"))
        vault.saveBtAddress("aa:bb:cc:dd:ee:ff")
        assertEquals("AA:BB:CC:DD:EE:FF", vault.btAddress())
        vault.removePeer()
        assertNull(vault.peer())
        assertNull(vault.btAddress())
        // Kimlik eşleşme kaldırılınca kalır.
        vault.identity()
        val pub = prefs.getString("identity.public")
        vault.removePeer()
        assertEquals(pub, prefs.getString("identity.public"))
    }

    @Test
    fun brokenPeerRecordReadsAsNone() {
        vault.savePeer(PeerRecord(peerId, P256.generate(random).public, "x", 1))
        prefs.edit().putString("peer.public", Base64.getEncoder().encodeToString(ByteArray(65))).commit()
        assertNull(vault.peer())
        prefs.edit().putString("peer.public", Base64.getEncoder().encodeToString(P256.generate(random).public)).putString("peer.id", "zz").commit()
        assertNull(vault.peer())
    }

    @Test
    fun peerRecordEqualityIsByContent() {
        val key = P256.generate(random).public
        val a = PeerRecord(peerId, key, "n", 5)
        val b = PeerRecord(peerId, key.copyOf(), "n", 5)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, PeerRecord(peerId, P256.generate(random).public, "n", 5))
        assertEquals(Fingerprint.of(key), a.fingerprint)
    }
}
