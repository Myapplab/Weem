package com.kuryelog.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kuryelog.app.link.LinkHub
import com.kuryelog.app.remote.RemoteHub
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.Dot
import com.kuryelog.app.ui.EmptyState
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.ScreenTitle
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.link.LinkRole
import com.kuryelog.logic.link.LinkStatus
import com.kuryelog.logic.link.Transport
import com.kuryelog.logic.remote.HostView
import com.kuryelog.logic.remote.RemoteLines
import com.kuryelog.logic.remote.RemoteText
import com.kuryelog.logic.remote.GlobalAction
import com.kuryelog.logic.remote.TouchLimits
import com.kuryelog.logic.remote.ViewerMachine.PointerKind
import com.kuryelog.logic.remote.ViewerView

/**
 * Uzaktan sekmesi (A9.1–A9.2, REMOTE.md 1.6, 2.4): üstte öteki telefonun ekranı (Göster / Kapat, video oran korunarak; öteki
 * telefonda dokunuş açıksa videoya dokunuş ve Geri · Ana ekran · Son uygulamalar · Bildirimler tuşları oraya gider), altta bu
 * telefonun paylaşımı (aç / bitir, dokunuş servisi durumu). 2 telefon modu kapalı ya da eşleşme yoksa yalnız yönlendirme metni.
 */
@Composable
fun RemoteScreen(lang: Lang) {
    val role by LinkHub.role.collectAsState()
    val peer by LinkHub.peer.collectAsState()
    val status by LinkHub.status.collectAsState()
    val vv by RemoteHub.viewerView.collectAsState()
    val hv by RemoteHub.hostView.collectAsState()
    val ctx = LocalContext.current
    val p = palette()
    // A10.4d (`docs/TASARIM.md` 12): sekme başlığı, öteki telefonun ekranı büyük kartta, paylaşım kartı, ipucu altta.
    Column(
        Modifier.fillMaxSize().padding(start = WeemSpace.l, end = WeemSpace.l, top = WeemSpace.s, bottom = WeemSpace.l),
        verticalArrangement = Arrangement.spacedBy(WeemSpace.m),
    ) {
        ScreenTitle(Str.TAB_REMOTE.get(lang))
        if (role == LinkRole.OFF || peer == null) {
            EmptyState(RemoteText.NEED_LINK.get(lang), Modifier.testTag("remote_need_link"), icon = WeemIcons.Devices)
            return@Column
        }
        val linkIssue = when {
            status.state != LinkStatus.State.LINKED -> RemoteText.NOT_LINKED
            status.transport != Transport.WIFI -> RemoteText.ONLY_WIFI
            else -> null
        }
        val watching = !RemoteLines.canView(vv)
        WCard(Modifier.weight(1f), large = true, spacing = WeemSpace.s) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
                IconBox(WeemIcons.Devices, tone = WTone.ACCENT)
                Text(RemoteText.VIEW_TITLE.get(lang), style = WeemType.titleM, modifier = Modifier.weight(1f))
                if (watching) {
                    WButton(RemoteText.VIEW_STOP.get(lang), RemoteHub::stopViewing, kind = BtnKind.OUTLINE)
                } else {
                    WButton(
                        RemoteText.VIEW_START.get(lang),
                        {
                            val dm = ctx.resources.displayMetrics
                            RemoteHub.startViewing(maxOf(dm.widthPixels, dm.heightPixels))
                        },
                        Modifier.testTag("remote_view"),
                        icon = WeemIcons.PlayArrow,
                        enabled = linkIssue == null,
                    )
                }
            }
            val line = if (!watching && linkIssue != null) linkIssue.get(lang) else RemoteLines.viewer(vv, lang)
            line?.let { Text(it, style = WeemType.bodyM, color = p.muted.color()) }
            RemoteLines.viewOnly(vv, lang)?.let { Banner(it, Modifier.testTag("remote_view_only"), tone = WTone.NEUTRAL) }
            if (vv.state == ViewerView.State.CONNECTING || vv.state == ViewerView.State.PLAYING) Video(vv)
            if (vv.control) Keys(lang)
        }
        WCard(padding = 0.dp) { Share(hv, lang) }
        Text(RemoteText.HINT.get(lang), style = WeemType.bodyS, color = p.muted.color(), modifier = Modifier.padding(horizontal = WeemSpace.xs))
    }
}

// Video tıklama değil, ham dokunuş taşır (öteki telefonda oynatılır); erişilebilirlik tıklaması anlamsız.
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun ColumnScope.Video(v: ViewerView) {
    val ratio = if (v.width > 0 && v.height > 0) v.width.toFloat() / v.height else 9f / 20f
    Box(
        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(WeemRadius.tile)).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { c ->
                SurfaceView(c).apply {
                    setOnTouchListener { view, e -> forward(view, e); true }
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) = RemoteHub.setSurface(h.surface)
                        override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                        override fun surfaceDestroyed(h: SurfaceHolder) = RemoteHub.setSurface(null)
                    })
                }
            },
            modifier = Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f),
        )
    }
    DisposableEffect(Unit) { onDispose { RemoteHub.setSurface(null) } }
}

/** Videoya dokunuş → [RemoteHub.pointer] (görüntüye göre 0..NORM); izleyen makine dokunuş kapalıysa yok sayar. */
private fun forward(v: View, e: MotionEvent) {
    if (v.width <= 0 || v.height <= 0) return
    fun nx(x: Float) = (x / v.width * TouchLimits.NORM).toInt()
    fun ny(y: Float) = (y / v.height * TouchLimits.NORM).toInt()
    fun send(kind: PointerKind, i: Int) = RemoteHub.pointer(kind, e.getPointerId(i), nx(e.getX(i)), ny(e.getY(i)), e.eventTime)
    when (e.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
            v.parent?.requestDisallowInterceptTouchEvent(true)
            send(PointerKind.DOWN, e.actionIndex)
        }
        MotionEvent.ACTION_MOVE -> {
            // Ara örnekler (tarih) de gider: hızlı kaydırmanın biçimi korunur.
            for (h in 0 until e.historySize) for (i in 0 until e.pointerCount) {
                RemoteHub.pointer(PointerKind.MOVE, e.getPointerId(i), nx(e.getHistoricalX(i, h)), ny(e.getHistoricalY(i, h)), e.getHistoricalEventTime(h))
            }
            for (i in 0 until e.pointerCount) send(PointerKind.MOVE, i)
        }
        MotionEvent.ACTION_POINTER_UP -> send(PointerKind.UP, e.actionIndex)
        MotionEvent.ACTION_UP -> send(PointerKind.UP, e.actionIndex)
        MotionEvent.ACTION_CANCEL -> RemoteHub.pointer(PointerKind.CANCEL, 0, 0, 0, e.eventTime)
    }
}

/** Öteki telefonun sistem tuşları: dört eşit, 48 dp hap (`docs/TASARIM.md` 11). */
@Composable
private fun Keys(lang: Lang) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
        for ((a, t) in listOf(
            GlobalAction.BACK to RemoteText.KEY_BACK, GlobalAction.HOME to RemoteText.KEY_HOME,
            GlobalAction.RECENTS to RemoteText.KEY_RECENTS, GlobalAction.NOTIFICATIONS to RemoteText.KEY_NOTIFICATIONS,
        )) {
            WButton(t.get(lang), { RemoteHub.action(a) }, Modifier.weight(1f).testTag("remote_key_${a.name.lowercase()}"), kind = BtnKind.TONAL, fill = true)
        }
    }
}

private fun openAccessibilitySettings(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
internal fun Share(hv: HostView, lang: Lang) {
    val ctx = LocalContext.current
    val p = palette()
    Column(Modifier.padding(WeemSpace.l), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
            IconBox(WeemIcons.ScreenShare, tone = if (hv.state == HostView.State.OFF) WTone.NEUTRAL else WTone.POSITIVE)
            Column(Modifier.weight(1f)) {
                Text(RemoteText.SHARE_TITLE.get(lang), style = WeemType.titleS)
                Text(RemoteLines.host(hv, lang), style = WeemType.bodyS, color = p.muted.color())
            }
        }
        if (hv.state == HostView.State.OFF) {
            WButton(RemoteText.SHARE_START.get(lang), RemoteHub::requestShare, Modifier.testTag("remote_share"), icon = WeemIcons.ScreenShare, fill = true)
        } else {
            WButton(RemoteText.SHARE_STOP.get(lang), RemoteHub::stopShare, kind = BtnKind.DANGER, icon = WeemIcons.StopScreenShare, fill = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            Dot((if (hv.touch) p.positive else p.muted).color())
            Text(
                (if (hv.touch) RemoteText.TOUCH_ON else RemoteText.TOUCH_OFF).get(lang),
                style = WeemType.bodyS, color = p.muted.color(),
                modifier = Modifier.weight(1f).testTag("remote_touch_state"),
            )
            if (!hv.touch) {
                WButton(RemoteText.TOUCH_SETTINGS.get(lang), { openAccessibilitySettings(ctx) }, Modifier.testTag("remote_touch_settings"), kind = BtnKind.TEXT)
            }
        }
    }
}
