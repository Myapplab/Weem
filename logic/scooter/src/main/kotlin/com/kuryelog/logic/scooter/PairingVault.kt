package com.kuryelog.logic.scooter

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Credentials are encrypted on this phone; diagnostic wire transcripts may reveal pairing material. (SCOOTER.md 4.1; NB
 * `KeyVault.java`, line by line; Android supplies only the [store] and the Keystore [master] key.)
 *
 * Format and names never change (VERI_SOZLESMESI 5.3; WEEM installed over Rawstats reads the same values): preferences
 * [PREFS], Keystore alias [ALIAS], `AES/GCM/NoPadding`, provider-chosen 12-byte IV ‖ ciphertext + 128-bit tag, AAD = the device
 * address in UTF-8, Base64 without line breaks (`android.util.Base64.NO_WRAP` and `java.util.Base64` write the same text; reading
 * uses the MIME decoder, which skips stray characters like Android's). Slots per address: active `<address>`,
 * `<address>.confirmed`, candidate `<address>.pending`, `<address>.previous`, `<address>.previous.confirmed`.
 */
class PairingVault(private val store: PairingStore, private val master: () -> SecretKey) {
    class KeyUnavailableException internal constructor(cause: Throwable) : IllegalStateException("Yerel eşleştirme anahtarı açılamadı", cause)

    fun get(address: String): ByteArray? {
        val encoded = store.getString(address, "")
        if (encoded.isEmpty()) return null
        try {
            val all = Base64.getMimeDecoder().decode(encoded)
            if (all.size < 28) throw java.security.GeneralSecurityException("key length")
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, master(), GCMParameterSpec(128, all.copyOf(12)))
            c.updateAAD(address.toByteArray(Charsets.UTF_8))
            val key = c.doFinal(all.copyOfRange(12, all.size))
            if (key.size != 16) throw java.security.GeneralSecurityException("credential length")
            return key
        } catch (e: Exception) {
            throw KeyUnavailableException(e)
        }
    }

    /** A stored key exists (confirmed or from an older version); nothing is decrypted. */
    fun has(address: String): Boolean = store.getString(address, "").isNotEmpty()

    fun confirmed(address: String): Boolean = store.getBoolean("$address.confirmed", false)

    fun hasPending(address: String): Boolean = store.getString("$address.pending", "").isNotEmpty()

    /** Unconfirmed: only the candidate slot. Confirmed: the old active value moves to `.previous` unless it is the same key (an
     *  unreadable one moves too: an explicit replacement can recover), the new one becomes active and confirmed, the candidate goes. */
    fun save(address: String, key: ByteArray, confirmed: Boolean) {
        if (key.size != 16) throw IllegalArgumentException("16-byte pairing key")
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, master())
            c.updateAAD(address.toByteArray(Charsets.UTF_8))
            val encrypted = c.doFinal(key)
            val iv = c.iv
            val all = iv.copyOf(iv.size + encrypted.size)
            System.arraycopy(encrypted, 0, all, iv.size, encrypted.size)
            val encoded = Base64.getEncoder().encodeToString(all)
            val editor = store.edit()
            if (!confirmed) {
                editor.putString("$address.pending", encoded)
            } else {
                val old = store.getString(address, "")
                var active: ByteArray? = null
                try {
                    active = get(address)
                } catch (unreadable: KeyUnavailableException) {
                    // Keep unreadable ciphertext as previous; an explicit replacement can recover.
                }
                val same = active != null && MessageDigest.isEqual(active, key)
                active?.fill(0)
                if (old.isNotEmpty() && !same) editor.putString("$address.previous", old).putBoolean("$address.previous.confirmed", this.confirmed(address))
                editor.putString(address, encoded).putBoolean("$address.confirmed", true).remove("$address.pending")
            }
            if (!editor.commit()) throw IllegalStateException("Anahtar saklanamadı")
        } catch (e: Exception) {
            throw IllegalStateException("Eşleştirme anahtarı saklanamadı", e)
        }
    }

    /** Brings `.previous` back as the active key with its own confirmation flag (never invented); clears the candidate. */
    fun restore(address: String): Boolean {
        val old = store.getString("$address.previous", "")
        if (old.isEmpty()) return false
        val wasConfirmed = store.getBoolean("$address.previous.confirmed", false)
        return store.edit().putString(address, old).remove("$address.previous").remove("$address.previous.confirmed").remove("$address.pending")
            .putBoolean("$address.confirmed", wasConfirmed).commit()
    }

    companion object {
        /** SharedPreferences file (MODE_PRIVATE). */
        const val PREFS = "pairing-v1"

        /** AndroidKeyStore alias of the AES/GCM master key. */
        const val ALIAS = "NinebotLogger.Pairing.v1"
    }
}

/** The `SharedPreferences` subset [PairingVault] uses (Android: [PairingVault.PREFS]; tests: in memory). Writes are synchronous. */
interface PairingStore {
    fun getString(key: String, default: String): String
    fun getBoolean(key: String, default: Boolean): Boolean
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun commit(): Boolean
    }
}
