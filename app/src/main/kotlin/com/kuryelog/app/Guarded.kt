package com.kuryelog.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemSize
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.ink
import com.kuryelog.app.ui.mark
import com.kuryelog.app.ui.palette
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

/** Hata/çökme kartı (A10.4d, `docs/TASARIM.md` 10): tehlike tonu, simgeli başlık, özet; Kopyala · Paylaş yan yana, Gizle metin düğmesi. */
@Composable
private fun ErrorCard(title: String, j: JSONObject, lang: Lang, hint: String?, onHide: (() -> Unit)?) {
    val ctx = LocalContext.current
    val p = palette()
    val ink = p.ink(WTone.DANGER).color()
    val text = remember(j) { CrashReport.text(j, LogTime.ts(j.optLong("at"))) }
    WCard(Modifier.padding(horizontal = WeemSpace.l, vertical = WeemSpace.s), tone = WTone.DANGER) {
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.Top) {
            Icon(WeemIcons.Error, contentDescription = null, tint = p.mark(WTone.DANGER).color(), modifier = Modifier.size(WeemSize.icon))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                Text(title, style = WeemType.titleS, color = ink)
                Text(CrashReport.summary(j), style = WeemType.bodyS, color = ink)
                if (hint != null) Text(hint, style = WeemType.bodyS, color = ink)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WButton(Str.CRASH_COPY.get(lang), { ctx.safely { ctx.copyText(text) } }, Modifier.weight(1f), kind = BtnKind.OUTLINE, icon = WeemIcons.ContentCopy, fill = true)
            WButton(Str.CRASH_SHARE.get(lang), { ctx.safely { ctx.shareText(text) } }, Modifier.weight(1f), kind = BtnKind.OUTLINE, icon = WeemIcons.Share, fill = true)
        }
        if (onHide != null) WButton(Str.CRASH_HIDE.get(lang), { ctx.safely { onHide() } }, kind = BtnKind.TEXT)
    }
}

private fun Context.copyText(s: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("WEEM", s))
}

private fun Context.shareText(s: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s)
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
