package com.kuryelog.app.remote

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle

/**
 * Saydam onay penceresi (REMOTE.md 1.2): sistemin ekran yakalama onayını açar; onay gelirse [CaptureService] başlar, gelmezse
 * [RemoteHub] reddi öğrenir. Android 14+'ta tam ekran zorlanır (`createConfigForDefaultDisplay`). WEEM bu pencereye kendiliğinden
 * basmaz: kullanıcı paylaşan telefonda kendisi onaylar. Cihazda doğrulanmadı.
 */
class ConsentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        RemoteNotices.cancelAsk(this)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST)
        } catch (e: Exception) {
            RemoteHub.consentDenied()
            finish()
        }
    }

    @Deprecated("Activity sonuç API'si (androidx yok)")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST) {
            if (resultCode == RESULT_OK && data != null) CaptureService.start(this, resultCode, data) else RemoteHub.consentDenied()
        }
        finish()
    }

    private companion object {
        const val REQUEST = 7405
    }
}
