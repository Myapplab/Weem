package com.kuryelog.app

import android.content.Context
import android.content.SharedPreferences
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.app.ThemeMode

/** Kabuk tercihleri: Rawstats'ın `kuryelog` dosyası (veri sözleşmesi Bölüm 5.1); `lang` aynı, `theme` yeni. */
class ShellPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Kayıtlı dil ayarı: `tr` | `az` | boş (telefon dili). */
    var langSetting: String
        get() = prefs.getString(Lang.PREF_KEY, "").orEmpty()
        set(value) { prefs.edit().putString(Lang.PREF_KEY, value).apply() }

    var theme: ThemeMode
        get() = ThemeMode.fromKey(prefs.getString(ThemeMode.PREF_KEY, null))
        set(value) { prefs.edit().putString(ThemeMode.PREF_KEY, value.key).apply() }

    private companion object {
        const val FILE = "kuryelog"
    }
}
