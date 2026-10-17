package com.kuryelog.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kuryelog.app.core.CrashLog
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.CrashReport
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.LogTime
import com.kuryelog.logic.core.attempt
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kuryelog.logic.app.Text as Str

/** Dokunuş ve başlatma adımları (CORE.md 4.2, RS `MainActivity.safely`): hata kaydedilir, uygulama kapanmaz; dokunuşta kısa uyarı. */
fun Context.safely(source: String = "tap", block: () -> Unit) {
    attempt(source, Store.sink(this), block).onFailure { e ->
        if (source == "tap") runCatching { Toast.makeText(this, Str.ERROR.get(Store.lang(this)) + e.message, Toast.LENGTH_SHORT).show() }
    }
}

/**
 * Sayfa hata sınırı (CORE.md 4.1, RS `renderFailure`): sayfanın durumu [build] ile korumalı hesaplanır; hata verirse sayfa yerine
 * kart (`page_<ad>` olarak bir kez kaydedilir), alt menü çalışır kalır. Compose çizimin içindeki istisnayı yakalayamaz: karar ve
 * ayrıştırma [build]'de, [content] yalnız çizer.
 */
@Composable
fun <T> GuardedPage(page: String, lang: Lang, key: Any? = Unit, build: () -> T, content: @Composable (T) -> Unit) {
    val ctx = LocalContext.current
    val outcome = remember(page, key) { attempt("page_$page", Store.sink(ctx), build) }
    val failure = outcome.exceptionOrNull()
    if (failure == null) {
        @Suppress("UNCHECKED_CAST")
        content(outcome.getOrNull() as T)
    } else {
        val j = remember(failure) { CrashReport.json(System.currentTimeMillis(), CrashLog.version(ctx), "page_$page", failure) }
        ErrorCard(Str.PAGE_FAILED.get(lang), j, lang, hint = null, onHide = null)
    }
}

/** Kapanmış uygulamanın kartı (CORE.md 4.1): gizlenene dek görünür, daha yeni çökme yeniden gösterir. */
@Composable
fun CrashCard(j: JSONObject, lang: Lang, onHide: () -> Unit) {
    val time = remember(j, lang) {
        SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag(lang.key)).format(Date(j.optLong("at")))
    }
    ErrorCard(Str.CRASH_TITLE.get(lang).format(time), j, lang, hint = Str.CRASH_HINT.get(lang), onHide = onHide)
}

@Composable
private fun ErrorCard(title: String, j: JSONObject, lang: Lang, hint: String?, onHide: (() -> Unit)?) {
    val ctx = LocalContext.current
    val text = remember(j) { CrashReport.text(j, LogTime.ts(j.optLong("at"))) }
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(CrashReport.summary(j), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = { ctx.safely { ctx.copyText(text) } }) { Text(Str.CRASH_COPY.get(lang)) }
            TextButton(onClick = { ctx.safely { ctx.shareText(text) } }) { Text(Str.CRASH_SHARE.get(lang)) }
            if (onHide != null) TextButton(onClick = { ctx.safely { onHide() } }) { Text(Str.CRASH_HIDE.get(lang)) }
        }
    }
}

private fun Context.copyText(s: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("WEEM", s))
}

private fun Context.shareText(s: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s)
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
