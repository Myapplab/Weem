package com.kuryelog.app.scooter

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kuryelog.logic.scooter.PairingStore
import com.kuryelog.logic.scooter.PairingVault
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Eşleşme anahtarı deposunun Android yüzü (SCOOTER.md 4.1, 4.3; NB `KeyVault.java`): [PairingVault.PREFS] tercih dosyası
 * (`MODE_PRIVATE`, eşzamanlı `commit`) ve AndroidKeyStore'da [PairingVault.ALIAS] AES/GCM ana anahtarı (yoksa üretilir; anahtar
 * boyu sağlayıcı varsayılanı, kullanıcı doğrulaması yok). Kural ve biçim [PairingVault]'ta, JVM'de test edilir; Keystore yolu ve
 * Rawstats'ın üstüne kurulumda aynı anahtarın okunması cihazda doğrulanmadı (A5.5).
 */
class KeyVault(context: Context) {
    private val vault = PairingVault(Prefs(context.getSharedPreferences(PairingVault.PREFS, Context.MODE_PRIVATE))) { master() }

    /** Etkin anahtar; yoksa null. Açılamazsa [PairingVault.KeyUnavailableException]. */
    fun get(address: String): ByteArray? = vault.get(address)
    fun has(address: String): Boolean = vault.has(address)
    fun confirmed(address: String): Boolean = vault.confirmed(address)
    fun hasPending(address: String): Boolean = vault.hasPending(address)
    fun save(address: String, key: ByteArray, confirmed: Boolean) = vault.save(address, key, confirmed)
    fun restore(address: String): Boolean = vault.restore(address)

    private class Prefs(private val p: SharedPreferences) : PairingStore {
        override fun getString(key: String, default: String): String = p.getString(key, default) ?: default
        override fun getBoolean(key: String, default: Boolean): Boolean = p.getBoolean(key, default)
        override fun edit(): PairingStore.Editor = Editor(p.edit())

        private class Editor(private val e: SharedPreferences.Editor) : PairingStore.Editor {
            override fun putString(key: String, value: String) = apply { e.putString(key, value) }
            override fun putBoolean(key: String, value: Boolean) = apply { e.putBoolean(key, value) }
            override fun remove(key: String) = apply { e.remove(key) }
            override fun commit(): Boolean = e.commit()
        }
    }

    private companion object {
        /** NB `KeyVault.master`. WEEM: tek iş parçacığı (ilk eşleştirmede iki iş parçacığı aynı anda iki ana anahtar üretmesin). */
        @Synchronized
        fun master(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            if (!ks.containsAlias(PairingVault.ALIAS)) {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                generator.init(
                    KeyGenParameterSpec.Builder(PairingVault.ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
                generator.generateKey()
            }
            return ks.getKey(PairingVault.ALIAS, null) as SecretKey
        }
    }
}
