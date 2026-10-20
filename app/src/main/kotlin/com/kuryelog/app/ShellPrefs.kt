package com.kuryelog.app

import android.content.Context
import android.content.SharedPreferences
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.app.SetupStep
import com.kuryelog.logic.app.ThemeMode

/** Kabuk tercihleri: Rawstats'ın `kuryelog` dosyası (veri sözleşmesi Bölüm 5.1); `lang` aynı, `theme` ve `weem.setupDone` yeni. */
class ShellPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Kayıtlı dil ayarı: `tr` | `az` | boş (telefon dili). */
    var langSetting: String
        get() = prefs.getString(Lang.PREF_KEY, "").orEmpty()
        set(value) { prefs.edit().putString(Lang.PREF_KEY, value).apply() }

    var theme: ThemeMode
        get() = ThemeMode.fromKey(prefs.getString(ThemeMode.PREF_KEY, null))
        set(value) { prefs.edit().putString(ThemeMode.PREF_KEY, value.key).apply() }

    /** G5 (A7.2): ilk açılış kurulumu bitti ya da atlandı (`weem.setupDone`, yeni). */
    var setupDone: Boolean
        get() = prefs.getBoolean(SetupStep.PREF_KEY, false)
        set(value) { prefs.edit().putBoolean(SetupStep.PREF_KEY, value).apply() }

    private companion object {
        const val FILE = "kuryelog"
    }
}
