package com.kuryelog.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kuryelog.app.link.LinkHub
import com.kuryelog.app.link.TheftRuntime
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.RadioRow
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.StatusPill
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.link.LinkRole
import com.kuryelog.logic.link.LinkStatus
import com.kuryelog.logic.link.LinkText
import com.kuryelog.logic.link.LinkView
import com.kuryelog.logic.link.QrResult
import com.kuryelog.logic.link.TheftText
import java.util.Locale
import kotlinx.coroutines.delay

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

    SubScreen(LinkText.TITLE.get(lang), Str.BACK.get(lang), onBack, spacing = 0.dp) {
        SettingsGroup(LinkText.ROLE_TITLE.get(lang), dividerInset = 52.dp, selectableGroup = true) {
            for (r in LinkRole.entries) RadioRow(LinkView.role(r, lang), r == role, { LinkHub.setRole(ctx, r) }, enabled = !locked)
        }
        if (locked) Banner(TheftText.LOCKED.get(lang), Modifier.padding(top = WeemSpace.m), tone = WTone.DANGER, icon = WeemIcons.Lock)
        when (role) {
            LinkRole.PROVIDER -> Hint(LinkText.HINT_PROVIDER.get(lang))
            LinkRole.RECEIVER -> Hint(LinkText.HINT_RECEIVER.get(lang))
            LinkRole.OFF -> {}
        }
        if (role != LinkRole.OFF) Hint(LinkText.HINT_HOTSPOT.get(lang))

        WCard(Modifier.padding(top = WeemSpace.l)) {
            for ((i, line) in LinkView.lines(status, peer != null, role, peerRole, info, version, lang).withIndex()) {
                if (i == 0) Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
                    IconBox(WeemIcons.Devices, tone = if (status.state == LinkStatus.State.LINKED) WTone.POSITIVE else WTone.NEUTRAL)
                    Text(line, style = WeemType.titleS, modifier = Modifier.weight(1f))
                } else Text(line, style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            message?.let { Banner(it, tone = WTone.ACCENT) }
        }

        // A8.4: hırsızlık koruması (LINK.md 5.4).
        if (role != LinkRole.OFF) TheftCard(lang)

        SettingsGroup(LinkText.THIS_PHONE.get(lang)) {
            ListRow(mine?.let { LinkText.FINGERPRINT.get(lang).format(it) } ?: LinkText.IDENTITY_FAILED.get(lang), icon = WeemIcons.PhoneAndroid, titleStyle = WeemType.bodyM)
        }
        peer?.let { p ->
            SettingsGroup(LinkText.PEER.get(lang)) {
                ListRow(p.name, subtitle = LinkHub.peerFingerprint()?.let { LinkText.FINGERPRINT.get(lang).format(it) }, icon = WeemIcons.Devices)
            }
        }

        val pairing = until?.let { it > now } == true
        if (pairing) {
            WCard(Modifier.padding(top = WeemSpace.l), live = true) {
                qr?.let { text -> QrImage(text) }
                Text(LinkText.PAIR_WAIT.get(lang).format(LinkView.remaining((until ?: now) - now)), style = WeemType.titleS)
                if (qr != null) Text(LinkText.PAIR_HINT.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WButton(LinkText.PAIR_CANCEL.get(lang), { qr = null; LinkHub.cancelPairing(ctx) }, kind = BtnKind.OUTLINE, icon = WeemIcons.Close, fill = true)
            }
        } else if (!locked) {
            Row(Modifier.padding(top = WeemSpace.l), horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                WButton(LinkText.PAIR_SHOW.get(lang), {
                    message = null
                    qr = LinkHub.startPairing(ctx)
                    if (qr == null) message = LinkText.IDENTITY_FAILED.get(lang)
                }, Modifier.weight(1f), icon = WeemIcons.Link)
                WButton(LinkText.PAIR_SCAN.get(lang), { message = null; scan(ctx, lang) { message = it } }, Modifier.weight(1f), kind = BtnKind.TONAL, icon = WeemIcons.QrCodeScanner)
            }
            if (peer != null) WButton(LinkText.UNPAIR.get(lang), { confirmUnpair = true }, Modifier.padding(top = WeemSpace.s), kind = BtnKind.DANGER, icon = WeemIcons.Delete, fill = true)
        }
    }
    if (confirmUnpair) ConfirmDialog(
        title = LinkText.UNPAIR.get(lang), confirm = LinkText.UNPAIR.get(lang), dismiss = LinkText.PAIR_CANCEL.get(lang), danger = true,
        onConfirm = { confirmUnpair = false; LinkHub.unpair(ctx) }, onDismiss = { confirmUnpair = false },
    )
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
    // QR her temada beyaz zeminde (okuyucu karşıtlığı).
    Image(
        bmp, contentDescription = null,
        modifier = Modifier.size(260.dp).align(Alignment.CenterHorizontally).background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(WeemRadius.card)).padding(WeemSpace.s),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = WeemSpace.s, start = WeemSpace.xs))
}

/** Öteki telefondan gelen veri kartı: başlıkta simge kutusu, altta satırlar. */
@Composable
private fun RemoteCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable () -> Unit) {
    WCard(Modifier.padding(start = WeemSpace.l, end = WeemSpace.l, top = WeemSpace.s), spacing = WeemSpace.xs) {
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
            IconBox(icon, tone = WTone.ACCENT)
            Text(title, style = WeemType.titleS, modifier = Modifier.weight(1f))
        }
        content()
    }
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
    RemoteCard(WeemIcons.Devices, LinkText.REMOTE_FROM_PROVIDER.get(lang)) {
        if (status.state != LinkStatus.State.LINKED) Hint(LinkText.REMOTE_NOT_LINKED.get(lang))
        val s = shift
        if (s == null && day == null && offers.isEmpty()) { Text(LinkText.REMOTE_NO_DATA.get(lang), style = WeemType.bodyM); return@RemoteCard }
        s?.let { StatusPill((if (it.active) LinkText.REMOTE_SHIFT_ON else LinkText.REMOTE_SHIFT_OFF).get(lang), if (it.active) WTone.POSITIVE else WTone.NEUTRAL) }
        for (o in offers) {
            val fee = o.displayedFeeAzn?.let { money(it) + " ₼" }
            Text(LinkText.REMOTE_OFFER.get(lang).format(listOfNotNull(o.restaurant, fee, o.dropoffAddress).joinToString(" · ")), style = WeemType.bodyM)
        }
        val open = stages.trips.count { it.active }
        if (open > 0) Text(LinkText.REMOTE_TRIPS.get(lang).format(open), style = WeemType.bodyM)
        day?.let { d ->
            val n = d.platforms.values.sumOf { it.orders }
            val net = d.platforms.values.mapNotNull { it.netAzn }.takeIf { it.isNotEmpty() }?.sum()
            Text(LinkText.REMOTE_DAY.get(lang).format(n, net?.let(::money) ?: "—"), style = WeemType.titleS)
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
    RemoteCard(WeemIcons.ElectricScooter, LinkText.REMOTE_SCOOTER.get(lang)) {
        val l = live
        if (l == null || !l.linkActive) { Text(LinkText.REMOTE_SCOOTER_OFF.get(lang), style = WeemType.bodyM); return@RemoteCard }
        val parts = listOfNotNull(
            l.scooter.internalPct?.let { LinkText.REMOTE_BATTERY.get(lang).format("${it.toInt()}%") },
            l.scooter.externalPct?.let { LinkText.REMOTE_EXTERNAL.get(lang).format("${it.toInt()}%") },
            l.scooter.rangeKm?.let { LinkText.REMOTE_RANGE.get(lang).format(money(it)) },
        )
        Text(parts.joinToString(" · "), style = WeemType.numS)
    }
}

private fun money(v: Double) = String.format(Locale.US, "%.2f", v)
