package com.kuryelog.logic.link

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** `weem-link` tercihlerinin [IdentityVault]'un kullandığı alt kümesi (Android: `SharedPreferences`; testler: [Memory]). Yazma eşzamanlı. */
interface LinkPrefs {
    fun getString(key: String): String?
    fun getLong(key: String): Long?
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String): Editor
        fun putLong(key: String, value: Long): Editor
        fun remove(key: String): Editor
        fun commit(): Boolean
    }

    class Memory : LinkPrefs {
        private val values = HashMap<String, Any>()

        @Synchronized override fun getString(key: String) = values[key] as? String
        @Synchronized override fun getLong(key: String) = values[key] as? Long

        override fun edit(): Editor = object : Editor {
            private val ops = ArrayList<(MutableMap<String, Any>) -> Unit>()
            override fun putString(key: String, value: String) = apply { ops += { it[key] = value } }
            override fun putLong(key: String, value: Long) = apply { ops += { it[key] = value } }
            override fun remove(key: String) = apply { ops += { it.remove(key) } }
            override fun commit(): Boolean {
                synchronized(this@Memory) { ops.forEach { it(values) } }
                return true
            }
        }
    }
}

/**
 * Kimlik anahtarı ve eş kaydı (LINK.md 3.12, VERI_SOZLESMESI 5.3c): tercih dosyası [PREFS], kimliğin özel anahtarı (PKCS#8)
 * AndroidKeyStore'daki [ALIAS] AES-256-GCM anahtarıyla sarılı (`IV 12 ‖ şifreli + etiket 16`, AAD [AAD], Base64). Keystore'da
 * anahtar anlaşması API 31'den olduğundan (minSdk 29) kimlik anahtarı yazılım anahtarıdır; yalnız ana anahtar Keystore'da
 * (`:scooter` `KeyVault` deseni). Kural burada, JVM'de test edilir; Android yalnız tercihleri ve [master]'ı verir.
 */
class IdentityVault(
    private val prefs: LinkPrefs,
    private val master: () -> SecretKey,
    private val random: SecureRandom = SecureRandom(),
) {
    /** [created] = yeni üretildi; [replacedUnreadable] = saklanan açılamadı, yenisi yazıldı (öteki telefon yeniden eşleşmeli). */
    class Loaded(val keys: DhKeyPair, val created: Boolean, val replacedUnreadable: Boolean)

    @Synchronized
    fun identity(): Loaded {
        val enc = prefs.getString(IDENTITY_KEY)
        val pub = prefs.getString(IDENTITY_PUBLIC)
        var unreadable = enc != null || pub != null
        if (enc != null && pub != null) {
            try {
                return Loaded(open(enc, pub), created = false, replacedUnreadable = false)
            } catch (e: Exception) {
                unreadable = true
            }
        }
        val keys = P256.generate(random)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, master())
        c.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val pkcs8 = P256.pkcs8(keys)
        val sealed = c.doFinal(pkcs8)
        pkcs8.fill(0)
        val all = c.iv + sealed
        val ok = prefs.edit()
            .putString(IDENTITY_KEY, Base64.getEncoder().encodeToString(all))
            .putString(IDENTITY_PUBLIC, Base64.getEncoder().encodeToString(keys.public))
            .commit()
        if (!ok) throw IllegalStateException("Kimlik anahtarı saklanamadı")
        return Loaded(keys, created = true, replacedUnreadable = unreadable)
    }

    private fun open(enc: String, pub: String): DhKeyPair {
        val all = Base64.getDecoder().decode(enc)
        if (all.size < 12 + 16 + 1) throw NoiseException("identity length")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, master(), GCMParameterSpec(128, all.copyOf(12)))
        c.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val pkcs8 = c.doFinal(all.copyOfRange(12, all.size))
        try {
            return P256.restore(pkcs8, Base64.getDecoder().decode(pub))
        } finally {
            pkcs8.fill(0)
        }
    }

    @Synchronized
    fun peer(): PeerRecord? {
        val id = PeerId.of(prefs.getString(PEER_ID)) ?: return null
        val key = prefs.getString(PEER_PUBLIC)?.let {
            try {
                Base64.getDecoder().decode(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        } ?: return null
        return try {
            PeerRecord(id, key, prefs.getString(PEER_NAME).orEmpty(), prefs.getLong(PEER_PAIRED_AT) ?: 0)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    @Synchronized
    fun savePeer(r: PeerRecord) {
        val ok = prefs.edit()
            .putString(PEER_ID, r.id.text)
            .putString(PEER_PUBLIC, Base64.getEncoder().encodeToString(r.publicKey))
            .putString(PEER_NAME, r.name)
            .putLong(PEER_PAIRED_AT, r.pairedAtMs)
            .commit()
        if (!ok) throw IllegalStateException("Eş kaydı saklanamadı")
    }

    /** Eş kaydı ve öğrenilen Bluetooth adresi silinir; kimlik kalır (3.11). */
    @Synchronized
    fun removePeer() {
        prefs.edit().remove(PEER_ID).remove(PEER_PUBLIC).remove(PEER_NAME).remove(PEER_PAIRED_AT).remove(PEER_BT).commit()
    }

    @Synchronized
    fun btAddress(): String? = prefs.getString(PEER_BT)

    @Synchronized
    fun saveBtAddress(address: String?) {
        val e = prefs.edit()
        if (address == null) e.remove(PEER_BT) else e.putString(PEER_BT, address.uppercase())
        e.commit()
    }

    companion object {
        /** SharedPreferences dosyası (MODE_PRIVATE). */
        const val PREFS = "weem-link"

        /** AndroidKeyStore'daki AES-256-GCM ana anahtarın takma adı. */
        const val ALIAS = "WEEM.Link.v1"
        const val AAD = "weem-link/identity/v1"

        const val IDENTITY_KEY = "identity.key"
        const val IDENTITY_PUBLIC = "identity.public"
        const val PEER_ID = "peer.id"
        const val PEER_PUBLIC = "peer.public"
        const val PEER_NAME = "peer.name"
        const val PEER_PAIRED_AT = "peer.pairedAtMs"
        const val PEER_BT = "peer.btAddress"
    }
}
