package com.kuryelog.logic.scooter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SCOOTER.md 4.1, 4.3: NB `KeyVaultInstrument` vault checks (9 + unreadable key 3 + upgrade fixture 1) on the JVM: the same rules
 * and format with a software AES master key instead of the Android Keystore (the Keystore itself: device test, doğrulanmadı).
 */
class PairingVaultTest {
    private val master: SecretKey = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()

    /** In-memory `SharedPreferences` with Android's editor semantics: changes collect per key (last call wins), applied on commit. */
    class MemoryStore : PairingStore {
        val values = LinkedHashMap<String, Any>()
        var failCommits = false
        override fun getString(key: String, default: String): String = values[key] as? String ?: default
        override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
        override fun edit(): PairingStore.Editor = object : PairingStore.Editor {
            private val changes = LinkedHashMap<String, Any?>()
            override fun putString(key: String, value: String) = apply { changes[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
            override fun remove(key: String) = apply { changes[key] = null }
            override fun commit(): Boolean {
                if (failCommits) return false
                for ((k, v) in changes) if (v == null) values.remove(k) else values[k] = v
                return true
            }
        }
    }

    /** `KeyVaultInstrument.onStart`: "9 checks passed". */
    @Test fun candidateConfirmationPreviousAndRestore() {
        val prefs = MemoryStore(); val vault = PairingVault(prefs) { master }; val address = "TEST-VAULT-NO-RADIO"
        val first = ByteArray(16) { 0x31 }; val candidate = ByteArray(16) { 0x72 }
        vault.save(address, first, true); val active = prefs.getString(address, "")
        vault.save(address, candidate, false); assertArrayEquals("candidate must not replace active credential", first, vault.get(address)); assertTrue("pending separate from confirmed", vault.confirmed(address) && vault.hasPending(address))
        assertEquals("active ciphertext unchanged while pairing pending", active, prefs.getString(address, ""))
        assertFalse("pending encrypted", prefs.getString("$address.pending", "").contains("7272727272727272"))
        vault.save(address, candidate, true); assertTrue("confirmed promotion", vault.get(address).contentEquals(candidate) && !vault.hasPending(address))
        assertEquals("previous credential retained", active, prefs.getString("$address.previous", ""))
        vault.save(address, candidate, true); assertEquals("reconnect must not erase previous credential", active, prefs.getString("$address.previous", ""))
        assertTrue("restore confirmed credential", vault.restore(address) && vault.get(address).contentEquals(first) && vault.confirmed(address))
        prefs.edit().putBoolean("$address.confirmed", false).commit(); vault.save(address, candidate, true)
        assertTrue("restore must not invent confirmation for legacy key", vault.restore(address) && !vault.confirmed(address))
    }

    /** `KeyVaultInstrument.storagePolicies`: corrupt-key recovery. */
    @Test fun unreadableKeyIsTypedKeptAndRecoverable() {
        val prefs = MemoryStore(); val vault = PairingVault(prefs) { master }; val address = "TEST-VAULT-NO-RADIO"; val key = ByteArray(16)
        prefs.edit().putString(address, "invalid-ciphertext").commit()
        var typed = false
        try { vault.get(address) } catch (expected: PairingVault.KeyUnavailableException) { typed = true }
        assertTrue("typed unreadable-key failure retains old ciphertext", typed && prefs.getString(address, "") == "invalid-ciphertext")
        vault.save(address, key, true); assertArrayEquals("explicit replacement recovers unreadable key", key, vault.get(address))
        assertEquals("unreadable previous value retained", "invalid-ciphertext", prefs.getString("$address.previous", ""))
    }

    /** `KeyVaultInstrument.seedUpgrade`: the fixture written before an upgrade reads back with the same master key. */
    @Test fun upgradeFixtureDecrypts() {
        val address = "00:11:22:33:44:55"; val key = Timing.unhex("00112233445566778899aabbccddeeff"); val prefs = MemoryStore()
        val old = PairingVault(prefs) { master }; old.save(address, key, false); old.save(address, key, true)
        assertArrayEquals("upgrade fixture decrypts before replacement install", key, PairingVault(prefs) { master }.get(address))
    }

    /** VERI_SOZLESMESI 5.3 (WEEM eki): Base64(IV 12 ‖ GCM ciphertext + 128-bit tag), AAD = address. A value written by an independent
     *  GCM encryption with a known IV opens; the vault's own value has the same layout: 44 bytes, 60 characters, no line break. */
    @Test fun storedValueIsThePairingV1Format() {
        val address = "AA:BB:CC:DD:EE:FF"; val key = ByteArray(16) { (it * 7).toByte() }; val iv = ByteArray(12) { (0xa0 + it).toByte() }
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, master, GCMParameterSpec(128, iv)); c.updateAAD(address.toByteArray(Charsets.UTF_8))
        val written = Base64.getEncoder().encodeToString(iv + c.doFinal(key))
        assertEquals(60, written.length)
        val prefs = MemoryStore(); prefs.edit().putString(address, written).putBoolean("$address.confirmed", true).commit()
        val vault = PairingVault(prefs) { master }
        assertArrayEquals(key, vault.get(address))
        prefs.edit().putString("BB:BB:CC:DD:EE:FF", written).commit()
        assertThrows("the address is the AAD", PairingVault.KeyUnavailableException::class.java) { vault.get("BB:BB:CC:DD:EE:FF") }
        vault.save(address, key, true)
        val text = prefs.getString(address, ""); val own = Base64.getDecoder().decode(text)
        assertTrue(text.length == 60 && '\n' !in text && own.size == 44)
        val d = Cipher.getInstance("AES/GCM/NoPadding"); d.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(128, own.copyOf(12))); d.updateAAD(address.toByteArray(Charsets.UTF_8))
        assertArrayEquals(key, d.doFinal(own.copyOfRange(12, own.size)))
        assertEquals("same key again: the old value is not kept as previous", "", prefs.getString("$address.previous", ""))
    }

    @Test fun failuresAreTyped() {
        val prefs = MemoryStore(); val vault = PairingVault(prefs) { master }; val address = "AA:BB:CC:DD:EE:FF"
        assertEquals("16-byte pairing key", assertThrows(IllegalArgumentException::class.java) { vault.save(address, ByteArray(15), true) }.message)
        assertTrue(vault.get(address) == null && !vault.has(address) && !vault.confirmed(address) && !vault.hasPending(address) && !vault.restore(address))
        prefs.edit().putString(address, Base64.getEncoder().encodeToString(ByteArray(27))).commit()
        assertEquals("key length", assertThrows(PairingVault.KeyUnavailableException::class.java) { vault.get(address) }.cause!!.message)
        prefs.failCommits = true
        val e = assertThrows(IllegalStateException::class.java) { vault.save(address, ByteArray(16), false) }
        assertTrue(e.message == "Eşleştirme anahtarı saklanamadı" && e.cause!!.message == "Anahtar saklanamadı")
        val broken = PairingVault(MemoryStore()) { throw java.security.KeyStoreException("no keystore") }
        assertEquals("Eşleştirme anahtarı saklanamadı", assertThrows(IllegalStateException::class.java) { broken.save(address, ByteArray(16), true) }.message)
    }

    @Test fun namesAreTheDataContract() {
        assertEquals("pairing-v1", PairingVault.PREFS)
        assertEquals("NinebotLogger.Pairing.v1", PairingVault.ALIAS)
        assertEquals("Yerel eşleştirme anahtarı açılamadı", PairingVault.KeyUnavailableException(RuntimeException()).message)
    }
}
