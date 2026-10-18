package com.kuryelog.app

import android.content.Context
import android.content.Intent
import com.kuryelog.app.core.Store
import com.kuryelog.logic.capture.TargetApps

/**
 * İzlenen uygulamalar (tercih `targets`, `userPicked`; CAPTURE.md 1.9, RS `MainActivity.launchableApps/autoDetectTargets/pickApps`).
 * Liste Android'den gelir; sıra ve otomatik bulma kuralları saf [TargetApps]'te.
 */
object TargetPicker {
    private const val USER_PICKED = "userPicked"

    /** Başlatılabilir uygulamalar (paket, ad), WEEM hariç. */
    fun launchable(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val found = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        return found.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.distinctBy { it.first }.filter { it.first != ctx.packageName }
    }

    /** Seçim listesi: kurye uygulamaları önce, sonra ada göre. */
    fun pickList(ctx: Context): List<Pair<String, String>> = TargetApps.pickOrder(launchable(ctx))

    /** Kullanıcı listeyi elle düzenlemediyse yeni kurulan Wolt/Bolt kurye uygulamalarını da ekler (hiçbir şeyi çıkarmaz). */
    fun autoDetect(ctx: Context) {
        val p = Store.prefs(ctx)
        val current = Store.targets(ctx)
        val next = TargetApps.autoAdded(current, launchable(ctx), p.getBoolean(USER_PICKED, false))
        if (next != current) p.edit().putStringSet(Store.TARGETS, HashSet(next)).apply()
    }

    fun save(ctx: Context, picked: Set<String>) {
        Store.prefs(ctx).edit().putStringSet(Store.TARGETS, HashSet(picked)).putBoolean(USER_PICKED, true).apply()
    }
}
