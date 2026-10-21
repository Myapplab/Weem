package com.kuryelog.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.kuryelog.logic.orders.OrdersTools
import com.kuryelog.logic.orders.SplitTargets

/**
 * Siparişler → Araçlar → "Wolt + Bolt bölünmüş ekranı aç" (Rawstats `openSplitScreen`): Wolt tam ekran açılır, 350 ms sonra Bolt yanına
 * (`FLAG_ACTIVITY_LAUNCH_ADJACENT`). Hedef seçimi ve metinler `logic/orders` [OrdersTools]'tadır; burada yalnız intent başlatma.
 * Kullanıcı düğmeye basınca çalışır; WEEM kurye uygulamalarında başka hiçbir şeye dokunmaz. Cihazda denenmedi.
 */
object SplitScreen {
    fun open(ctx: Context, tools: OrdersTools, handler: Handler = Handler(Looper.getMainLooper())) {
        fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
        val targets = when (val t = tools.splitTargets()) {
            is SplitTargets.Missing -> return toast(t.message)
            is SplitTargets.Ready -> t
        }
        val a = ctx.packageManager.getLaunchIntentForPackage(targets.wolt)
        val b = ctx.packageManager.getLaunchIntentForPackage(targets.bolt)
        if (a == null || b == null) return toast(tools.launchFailed)
        try {
            ctx.startActivity(a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            handler.postDelayed({
                runCatching { ctx.startActivity(b.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)) }.onFailure { toast(tools.splitHint) }
            }, DELAY_MS)
        } catch (e: Exception) { toast(tools.splitUnsupported) }
    }

    private const val DELAY_MS = 350L
}
