package com.kuryelog.app.link

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kuryelog.logic.link.IdentityVault
import com.kuryelog.logic.link.LinkPrefs
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 2 telefon kimliği ve eş kaydının Android yüzü (LINK.md 3.12, VERI_SOZLESMESI 5.3c): [IdentityVault.PREFS] tercih dosyası
 * (`MODE_PRIVATE`, eşzamanlı `commit`) ve AndroidKeyStore'da [IdentityVault.ALIAS] AES-256-GCM ana anahtarı (yoksa üretilir;
 * kullanıcı doğrulaması yok: telefon cepte, ekran kilitliyken de çalışır). Kural [IdentityVault]'ta, JVM'de test edilir; Keystore
 * yolu cihazda doğrulanmadı (A8.3 + A8.5).
 */
class LinkVault(context: Context) {
    val vault = IdentityVault(Prefs(context.applicationContext.getSharedPreferences(IdentityVault.PREFS, Context.MODE_PRIVATE)), master = { master() })

    private class Prefs(private val p: SharedPreferences) : LinkPrefs {
        override fun getString(key: String): String? = p.getString(key, null)
        override fun getLong(key: String): Long? = if (p.contains(key)) p.getLong(key, 0) else null
        override fun edit(): LinkPrefs.Editor = Editor(p.edit())

        private class Editor(private val e: SharedPreferences.Editor) : LinkPrefs.Editor {
            override fun putString(key: String, value: String) = apply { e.putString(key, value) }
            override fun putLong(key: String, value: Long) = apply { e.putLong(key, value) }
            override fun remove(key: String) = apply { e.remove(key) }
            override fun commit(): Boolean = e.commit()
        }
    }

    private companion object {
        /** Tek iş parçacığı: ilk açılışta iki iş parçacığı aynı anda iki ana anahtar üretmesin (`KeyVault` gibi). */
        @Synchronized
        fun master(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            if (!ks.containsAlias(IdentityVault.ALIAS)) {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                generator.init(
                    KeyGenParameterSpec.Builder(IdentityVault.ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
                generator.generateKey()
            }
            return ks.getKey(IdentityVault.ALIAS, null) as SecretKey
        }
    }
}
