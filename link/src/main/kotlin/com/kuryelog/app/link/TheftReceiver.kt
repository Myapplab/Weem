package com.kuryelog.app.link

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kuryelog.app.core.Store

/** Pixel'deki hırsızlık bildiriminin düğmeleri (LINK.md 5.1): Kapat (Receiver'a `TheftCommand disarm`), Sesi kapat (yalnız bu telefon). */
class TheftReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.guarded(context, "theft_action") {
            when (intent.action) {
                ACTION_DISARM -> TheftRuntime.requestDisarm()
                ACTION_SILENCE -> TheftRuntime.silenceLocal(context)
            }
        }
    }

    companion object {
        const val ACTION_DISARM = "com.kuryelog.app.link.THEFT_DISARM"
        const val ACTION_SILENCE = "com.kuryelog.app.link.THEFT_SILENCE"
    }
}
