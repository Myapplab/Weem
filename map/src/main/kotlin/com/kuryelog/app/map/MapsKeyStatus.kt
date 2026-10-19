package com.kuryelog.app.map

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.security.MessageDigest

/**
 * Google Maps anahtarının durumu (YolGo `mapsStatus`, `MainActivity.kt:114-126`; MAP.md 6.10): anahtar `AIza` ile başlıyor mu, son 4 karakter, paket, imza SHA-1
 * (iki noktalı büyük onaltılık), Play Services kodu (okunamazsa −1). Anahtarın kendisi hiçbir yere yazılmaz.
 */
data class MapsKeyStatus(val hasKey: Boolean, val last4: String, val packageName: String, val sha1: String, val playServices: Int) {
    companion object {
        fun read(context: Context, key: String): MapsKeyStatus {
            val pkg = context.packageName
            val sha1 = runCatching {
                val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val sig = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: return@runCatching ""
                MessageDigest.getInstance("SHA-1").digest(sig).joinToString(":") { "%02X".format(it) }
            }.getOrDefault("")
            val ps = runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) }.getOrDefault(-1)
            return MapsKeyStatus(key.startsWith("AIza"), key.takeLast(4), pkg, sha1, ps)
        }

        const val PLAY_SERVICES_OK = ConnectionResult.SUCCESS
    }
}
