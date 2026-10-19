package com.kuryelog.app.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.kuryelog.logic.map.input.GoogleCredentials
import java.security.MessageDigest

/**
 * Google REST kimliği (MAP.md 8.8 `ApkGoogleConfig`): APK'daki Maps anahtarı (`@string/google_maps_key`, `AIza` ile başlamıyorsa yok), paket ve imza SHA-1'i
 * (onaltılık, büyük harf, iki noktasız). Ayarlar'daki anahtar varsa o kullanılır, paket ve imza her zaman APK'nınki.
 */
object ApkGoogleConfig {
    @Volatile private var cached: GoogleCredentials? = null

    fun apk(ctx: Context): GoogleCredentials = cached ?: read(ctx.applicationContext).also { cached = it }

    /** Ayarlar'daki [userKey] doluysa o; değilse APK anahtarı; ikisi de yoksa null (istek atılmaz). */
    fun credentials(ctx: Context, userKey: String): GoogleCredentials? {
        val apk = apk(ctx)
        val key = userKey.trim().ifEmpty { apk.key }
        return if (key.isEmpty()) null else apk.copy(key = key)
    }

    @SuppressLint("DiscouragedApi")
    private fun read(app: Context): GoogleCredentials {
        val id = app.resources.getIdentifier("google_maps_key", "string", app.packageName)
        val key = (if (id == 0) "" else app.getString(id)).trim().takeIf { it.startsWith("AIza") } ?: ""
        return GoogleCredentials(key, app.packageName, signatureSha1(app))
    }

    private fun signatureSha1(app: Context): String? = runCatching {
        val pm = app.packageManager
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return@runCatching null
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val cert = certs?.firstOrNull() ?: return@runCatching null
        MessageDigest.getInstance("SHA-1").digest(cert.toByteArray()).joinToString("") { "%02X".format(it) }
    }.getOrNull()
}
