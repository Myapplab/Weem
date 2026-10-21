package com.kuryelog.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kuryelog.app.link.LinkHub
import com.kuryelog.app.link.TheftRuntime
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.link.LinkRole
import com.kuryelog.logic.link.LinkStatus
import com.kuryelog.logic.link.LinkText
import com.kuryelog.logic.link.LinkView
import com.kuryelog.logic.link.QrResult
import com.kuryelog.logic.link.TheftText
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Daha fazla → 2 telefon modu (LINK.md 4.10): rol, bağlantı durumu, iki telefonun parmak izi, QR ile eşleştirme (S24: QR'ı Google code
 * scanner okur, ZXing çizer; jeton WEEM dışına çıkmaz), eşleşmeyi kaldırma. Cihazda doğrulanmadı.
 */
@Composable
fun TwoPhoneScreen(lang: Lang, version: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val role by LinkHub.role.collectAsState()
    val status by LinkHub.status.collectAsState()
    val peer by LinkHub.peer.collectAsState()
    val info by LinkHub.peerInfo.collectAsState()
    val bridge by LinkHub.bridge.collectAsState()
    val peerRole = bridge?.peerRole?.collectAsState()?.value
    val until by LinkHub.pairingUntil.collectAsState()
    val theft by TheftRuntime.status.collectAsState()
    // Receiver'da koruma devredeyken rol, eşleştirme ve eşleşmeyi kaldırma kilitli (LinkHub.locked; LINK.md 5.1).
    val locked = role == LinkRole.RECEIVER && theft.guarding
    var qr by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmUnpair by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val mine = remember { LinkHub.fingerprint(ctx) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    LaunchedEffect(Unit) {
        LinkHub.notices.collect { n ->
            qr = null
            message = when (n) {
                is LinkHub.Notice.Paired -> LinkText.PAIRED.get(lang).format(n.name)
                LinkHub.Notice.PairingExpired -> LinkText.PAIR_EXPIRED.get(lang)
                LinkHub.Notice.UnpairedByPeer -> LinkText.UNPAIRED_BY_PEER.get(lang)
            }
        }
    }
    if (until == null && qr != null) qr = null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text(Str.BACK.get(lang)) }
        Text(LinkText.TITLE.get(lang), style = MaterialTheme.typography.headlineMedium)

        Text(LinkText.ROLE_TITLE.get(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Column(Modifier.selectableGroup()) {
            for (r in LinkRole.entries) RoleChoice(LinkView.role(r, lang), r == role, enabled = !locked) { LinkHub.setRole(ctx, r) }
        }
        if (locked) Text(TheftText.LOCKED.get(lang), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        when (role) {
            LinkRole.PROVIDER -> Hint(LinkText.HINT_PROVIDER.get(lang))
            LinkRole.RECEIVER -> Hint(LinkText.HINT_RECEIVER.get(lang))
            LinkRole.OFF -> {}
        }
        if (role != LinkRole.OFF) Hint(LinkText.HINT_HOTSPOT.get(lang))

        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(Modifier.padding(12.dp)) {
                for ((i, line) in LinkView.lines(status, peer != null, role, peerRole, info, version, lang).withIndex()) {
                    Text(line, style = if (i == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall)
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
            }
        }

        // A8.4: hırsızlık koruması (LINK.md 5.4).
        if (role != LinkRole.OFF) TheftCard(lang)

        Text(LinkText.THIS_PHONE.get(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(mine?.let { LinkText.FINGERPRINT.get(lang).format(it) } ?: LinkText.IDENTITY_FAILED.get(lang), style = MaterialTheme.typography.bodyMedium)
        peer?.let { p ->
            Text(LinkText.PEER.get(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text(p.name, style = MaterialTheme.typography.bodyMedium)
            LinkHub.peerFingerprint()?.let { Text(LinkText.FINGERPRINT.get(lang).format(it), style = MaterialTheme.typography.bodyMedium) }
        }

        val pairing = until?.let { it > now } == true
        if (pairing) {
            qr?.let { text -> QrImage(text) }
            Text(LinkText.PAIR_WAIT.get(lang).format(LinkView.remaining((until ?: now) - now)), modifier = Modifier.padding(top = 8.dp))
            if (qr != null) Hint(LinkText.PAIR_HINT.get(lang))
            OutlinedButton(onClick = { qr = null; LinkHub.cancelPairing(ctx) }, modifier = Modifier.padding(top = 8.dp)) { Text(LinkText.PAIR_CANCEL.get(lang)) }
        } else if (!locked) {
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    message = null
                    qr = LinkHub.startPairing(ctx)
                    if (qr == null) message = LinkText.IDENTITY_FAILED.get(lang)
                }) { Text(LinkText.PAIR_SHOW.get(lang)) }
                OutlinedButton(onClick = { message = null; scan(ctx, lang) { message = it } }) { Text(LinkText.PAIR_SCAN.get(lang)) }
            }
            if (peer != null) TextButton(onClick = { confirmUnpair = true }, modifier = Modifier.padding(top = 8.dp)) { Text(LinkText.UNPAIR.get(lang)) }
        }
    }
    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text(LinkText.UNPAIR.get(lang)) },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; LinkHub.unpair(ctx) }) { Text(LinkText.UNPAIR.get(lang)) } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text(LinkText.PAIR_CANCEL.get(lang)) } },
        )
    }
}

/** Google code scanner (S24): kamera izni yok; sonuç yalnız [LinkHub.pairWith]'e gider. */
private fun scan(ctx: Context, lang: Lang, done: (String?) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    runCatching {
        GmsBarcodeScanning.getClient(ctx, options).startScan()
            .addOnSuccessListener { b ->
                done(when (b.rawValue?.let { LinkHub.pairWith(ctx, it) }) {
                    is QrResult.Ok -> null
                    QrResult.Newer -> LinkText.QR_NEWER.get(lang)
                    else -> LinkText.QR_INVALID.get(lang)
                })
            }
            .addOnFailureListener { done(LinkText.SCAN_FAILED.get(lang)) }
    }.onFailure { done(LinkText.SCAN_FAILED.get(lang)) }
}

@Composable
private fun ColumnScope.QrImage(text: String) {
    val bmp = remember(text) {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
        Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565).apply {
            for (x in 0 until m.width) for (y in 0 until m.height) setPixel(x, y, if (m.get(x, y)) Color.BLACK else Color.WHITE)
        }.asImageBitmap()
    }
    Image(bmp, contentDescription = null, modifier = Modifier.padding(top = 16.dp).size(260.dp).align(Alignment.CenterHorizontally))
}

@Composable
private fun RoleChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.RadioButton).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

/** Receiver'da Siparişler sekmesinin üstü: Provider'ın vardiyası, teklifleri, açık siparişleri ve gün özeti. */
@Composable
fun RemoteOrdersCard(lang: Lang) {
    val bridge by LinkHub.bridge.collectAsState()
    val b = bridge ?: return
    if (b.role != LinkRole.RECEIVER) return
    val status by LinkHub.status.collectAsState()
    val shift by b.remoteShift.collectAsState()
    val offers by b.orders.offers.collectAsState()
    val stages by b.orders.stages.collectAsState()
    val day by b.orders.day.collectAsState()
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(LinkText.REMOTE_FROM_PROVIDER.get(lang), style = MaterialTheme.typography.titleSmall)
            if (status.state != LinkStatus.State.LINKED) Hint(LinkText.REMOTE_NOT_LINKED.get(lang))
            val s = shift
            if (s == null && day == null && offers.isEmpty()) { Text(LinkText.REMOTE_NO_DATA.get(lang)); return@Column }
            s?.let { Text((if (it.active) LinkText.REMOTE_SHIFT_ON else LinkText.REMOTE_SHIFT_OFF).get(lang)) }
            for (o in offers) {
                val fee = o.displayedFeeAzn?.let { money(it) + " ₼" }
                Text(LinkText.REMOTE_OFFER.get(lang).format(listOfNotNull(o.restaurant, fee, o.dropoffAddress).joinToString(" · ")))
            }
            val open = stages.trips.count { it.active }
            if (open > 0) Text(LinkText.REMOTE_TRIPS.get(lang).format(open))
            day?.let { d ->
                val n = d.platforms.values.sumOf { it.orders }
                val net = d.platforms.values.mapNotNull { it.netAzn }.takeIf { it.isNotEmpty() }?.sum()
                Text(LinkText.REMOTE_DAY.get(lang).format(n, net?.let(::money) ?: "—"))
            }
        }
    }
}

/** Provider'da Scooter sekmesinin üstü: scooter Receiver'da; bataryası `BatterySummary`'den. */
@Composable
fun RemoteScooterCard(lang: Lang) {
    val bridge by LinkHub.bridge.collectAsState()
    val b = bridge ?: return
    if (b.role != LinkRole.PROVIDER) return
    val live by b.scooter.live.collectAsState()
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(LinkText.REMOTE_SCOOTER.get(lang), style = MaterialTheme.typography.titleSmall)
            val l = live
            if (l == null || !l.linkActive) { Text(LinkText.REMOTE_SCOOTER_OFF.get(lang)); return@Column }
            val parts = listOfNotNull(
                l.scooter.internalPct?.let { LinkText.REMOTE_BATTERY.get(lang).format("${it.toInt()}%") },
                l.scooter.externalPct?.let { LinkText.REMOTE_EXTERNAL.get(lang).format("${it.toInt()}%") },
                l.scooter.rangeKm?.let { LinkText.REMOTE_RANGE.get(lang).format(money(it)) },
            )
            Text(parts.joinToString(" · "))
        }
    }
}

private fun money(v: Double) = String.format(Locale.US, "%.2f", v)
