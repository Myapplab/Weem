package com.kuryelog.app

import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kuryelog.app.link.LinkHub
import com.kuryelog.app.remote.RemoteHub
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.link.LinkRole
import com.kuryelog.logic.link.LinkStatus
import com.kuryelog.logic.link.Transport
import com.kuryelog.logic.remote.HostView
import com.kuryelog.logic.remote.RemoteLines
import com.kuryelog.logic.remote.RemoteText
import com.kuryelog.logic.remote.ViewerView

/**
 * Uzaktan sekmesi (A9.1, REMOTE.md 1.6): üstte öteki telefonun ekranı (Göster / Kapat, video oran korunarak), altta bu telefonun
 * paylaşımı (aç / bitir). 2 telefon modu kapalı ya da eşleşme yoksa yalnız yönlendirme metni. Dokunuş iletimi A9.2'de.
 */
@Composable
fun RemoteScreen(lang: Lang) {
    val role by LinkHub.role.collectAsState()
    val peer by LinkHub.peer.collectAsState()
    val status by LinkHub.status.collectAsState()
    val vv by RemoteHub.viewerView.collectAsState()
    val hv by RemoteHub.hostView.collectAsState()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (role == LinkRole.OFF || peer == null) {
            Text(RemoteText.NEED_LINK.get(lang), Modifier.testTag("remote_need_link"))
            return@Column
        }
        val linkIssue = when {
            status.state != LinkStatus.State.LINKED -> RemoteText.NOT_LINKED
            status.transport != Transport.WIFI -> RemoteText.ONLY_WIFI
            else -> null
        }
        val watching = !RemoteLines.canView(vv)
        Card(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(RemoteText.VIEW_TITLE.get(lang), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (watching) {
                        OutlinedButton(onClick = RemoteHub::stopViewing) { Text(RemoteText.VIEW_STOP.get(lang)) }
                    } else {
                        Button(
                            onClick = {
                                val dm = ctx.resources.displayMetrics
                                RemoteHub.startViewing(maxOf(dm.widthPixels, dm.heightPixels))
                            },
                            enabled = linkIssue == null,
                            modifier = Modifier.testTag("remote_view"),
                        ) { Text(RemoteText.VIEW_START.get(lang)) }
                    }
                }
                val line = if (!watching && linkIssue != null) linkIssue.get(lang) else RemoteLines.viewer(vv, lang)
                line?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (vv.state == ViewerView.State.CONNECTING || vv.state == ViewerView.State.PLAYING) Video(vv)
            }
        }
        Card(Modifier.fillMaxWidth()) { Share(hv, lang) }
        Text(RemoteText.HINT.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColumnScope.Video(v: ViewerView) {
    val ratio = if (v.width > 0 && v.height > 0) v.width.toFloat() / v.height else 9f / 20f
    Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { c ->
                SurfaceView(c).apply {
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

@Composable
private fun Share(hv: HostView, lang: Lang) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(RemoteText.SHARE_TITLE.get(lang), style = MaterialTheme.typography.titleSmall)
                Text(RemoteLines.host(hv, lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hv.state == HostView.State.OFF) {
                Button(onClick = RemoteHub::requestShare, modifier = Modifier.testTag("remote_share")) { Text(RemoteText.SHARE_START.get(lang)) }
            } else {
                OutlinedButton(onClick = RemoteHub::stopShare) { Text(RemoteText.SHARE_STOP.get(lang)) }
            }
        }
    }
}
