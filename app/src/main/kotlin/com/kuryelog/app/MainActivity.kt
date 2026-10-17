package com.kuryelog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kuryelog.app.core.HomeWatch
import com.kuryelog.app.core.Shift
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.Lang
import java.util.Locale

/** Tek Activity: alt menü kabuğu. Ekranlar sonraki aşamalarda sekmelere takılır. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // RS `MainActivity`: eski otomatik oturumlar güncellemeden sonra sürmez (CORE.md 1.1).
        safely("start") { Store.ensureManualMode(this); Shift.deviceId(this) }
        val prefs = ShellPrefs(this)
        setContent {
            var langSetting by remember { mutableStateOf(prefs.langSetting) }
            var theme by remember { mutableStateOf(prefs.theme) }
            val lang = Lang.resolve(langSetting, Locale.getDefault().language)
            WeemTheme(dark = theme.isDark(isSystemInDarkTheme())) {
                WeemApp(
                    lang = lang,
                    langSetting = langSetting,
                    theme = theme,
                    versionName = BuildInfo.versionName(this),
                    onLangSetting = { prefs.langSetting = it; langSetting = it },
                    onTheme = { prefs.theme = it; theme = it },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ev izleyicinin sürücülerinden biri uygulama açılışı (CORE.md 1.7); en çok 5 sn'de bir denetler.
        HomeWatch.refresh(this)
    }
}

internal object BuildInfo {
    fun versionName(context: android.content.Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    fun isDebuggable(context: android.content.Context): Boolean =
        context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
}
