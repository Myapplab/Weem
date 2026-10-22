package com.kuryelog.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kuryelog.app.link.LinkHub
import com.kuryelog.app.link.TheftRuntime
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.CheckRow
import com.kuryelog.app.ui.SectionLabel
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.SwitchRow
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSize
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.floating
import com.kuryelog.logic.app.Palette
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.link.LinkRole
import com.kuryelog.logic.link.TheftGuard.State
import com.kuryelog.logic.link.TheftText
import kotlinx.coroutines.delay

/**
 * Hırsızlık koruması ekranları (A8.4, LINK.md 5.4): 2 telefon modu ekranındaki kart (Receiver'da ayarlar, Pixel'de durum ve Park/Kapat)
 * ve Receiver'da her sekmenin üstündeki uyarı katmanı ([TheftOverlay]). Cihazda doğrulanmadı.
 */
@Composable
fun TheftCard(lang: Lang) {
    val role by LinkHub.role.collectAsState()
    SectionLabel(TheftText.TITLE.get(lang))
    Column(verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
        when (role) {
            LinkRole.RECEIVER -> ReceiverTheft(lang)
            LinkRole.PROVIDER -> ProviderTheft(lang)
            LinkRole.OFF -> Small(TheftText.ONLY_TWO_PHONE.get(lang))
        }
    }
}

@Composable
private fun ReceiverTheft(lang: Lang) {
    val ctx = LocalContext.current
    val status by TheftRuntime.status.collectAsState()
    val smsIssue by TheftRuntime.smsIssue.collectAsState()
    var enabled by remember { mutableStateOf(TheftRuntime.enabled(ctx)) }
    var autoStage by remember { mutableStateOf(TheftRuntime.autoStage(ctx)) }
    var autoAway by remember { mutableStateOf(TheftRuntime.autoAway(ctx)) }
    var hasPin by remember { mutableStateOf(TheftRuntime.hasPin(ctx)) }
    var pin by remember { mutableStateOf("") }
    val saved = remember { TheftRuntime.smsNumbers(ctx) }
    var n1 by remember { mutableStateOf(saved[0]) }
    var n2 by remember { mutableStateOf(saved[1]) }
    var smsAsked by remember { mutableIntStateOf(0) }
    val now = rememberNow()
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsAsked++ }
    val smsAllowed = remember(smsAsked) { TheftRuntime.smsAllowed(ctx) }

    // Koruma devredeyken korumayı kapatan ayarlar kilitli; kapatma PIN'le (ya da 3 sn) ya da Pixel'den (LINK.md 5.1).
    val locked = status.guarding

    SettingsGroup(null, footer = TheftText.HINT.get(lang)) {
        SwitchRow(TheftText.ENABLE.get(lang), enabled, { if (TheftRuntime.setEnabled(ctx, it)) enabled = it }, icon = WeemIcons.Shield, enabled = !locked)
        if (enabled) {
            CheckRow(TheftText.AUTO_STAGE.get(lang), autoStage, { autoStage = it; TheftRuntime.setAutoStage(ctx, it) }, enabled = !locked)
            CheckRow(TheftText.AUTO_AWAY.get(lang), autoAway, { autoAway = it; TheftRuntime.setAutoAway(ctx, it) }, enabled = !locked)
        }
    }
    if (!enabled) return
    WCard(live = status.state != State.OFF) {
        Text(TheftText.state(status, lang, now), style = WeemType.titleS)
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            if (status.state == State.OFF) WButton(TheftText.PARK.get(lang), { TheftRuntime.parkLocal() }, Modifier.weight(1f), icon = WeemIcons.Shield)
            if (status.state == State.ARMING) WButton(TheftText.CANCEL.get(lang), { TheftRuntime.cancelArmingLocal() }, Modifier.weight(1f), kind = BtnKind.OUTLINE)
            if (!locked) WButton(TheftText.TEST_SIREN.get(lang), { TheftRuntime.testSiren(ctx) }, Modifier.weight(1f), kind = BtnKind.OUTLINE, icon = WeemIcons.Notifications)
        }
        if (locked) {
            Banner(TheftText.LOCKED.get(lang), tone = WTone.DANGER, icon = WeemIcons.Lock)
            Disarm(lang, onAlarm = false) { p -> TheftRuntime.disarmLocal(ctx, p) }
        }
    }
    if (locked) return

    val field = RoundedCornerShape(WeemRadius.tile)
    WCard {
        Text(TheftText.PIN.get(lang), style = WeemType.titleS)
        Small(if (hasPin) TheftText.PIN_IS_SET.get(lang) else TheftText.PIN_NONE.get(lang))
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(pin, { v -> pin = v.filter(Char::isDigit).take(4) }, Modifier.weight(1f), singleLine = true, shape = field,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            WButton(TheftText.PIN_SET.get(lang), { if (TheftRuntime.setPin(ctx, pin)) { hasPin = true; pin = "" } }, kind = BtnKind.TONAL, enabled = pin.length == 4)
        }
    }

    WCard {
        Text(TheftText.SMS_TITLE.get(lang), style = WeemType.titleS)
        Small(TheftText.SMS_HINT.get(lang))
        OutlinedTextField(n1, { n1 = it }, Modifier.fillMaxWidth(), label = { Text(TheftText.SMS_NUMBER.get(lang).format(1)) }, singleLine = true, shape = field,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        OutlinedTextField(n2, { n2 = it }, Modifier.fillMaxWidth(), label = { Text(TheftText.SMS_NUMBER.get(lang).format(2)) }, singleLine = true, shape = field,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WButton(TheftText.SMS_SAVE.get(lang), { TheftRuntime.setSmsNumbers(ctx, listOf(n1, n2)) }, Modifier.weight(1f), kind = BtnKind.TONAL)
            if (!smsAllowed) WButton(TheftText.SMS_PERMISSION.get(lang), { askSms.launch(Manifest.permission.SEND_SMS) }, Modifier.weight(1f), kind = BtnKind.OUTLINE)
        }
        if (!smsAllowed) Small(TheftText.SMS_NO_PERMISSION.get(lang))
        if (smsIssue) Banner(TheftText.SMS_FAILED.get(lang), tone = WTone.DANGER)
    }
}

@Composable
private fun ProviderTheft(lang: Lang) {
    val ctx = LocalContext.current
    val bridge by LinkHub.bridge.collectAsState()
    val b = bridge ?: return
    val remote by b.theft.collectAsState()
    var note by remember { mutableStateOf<String?>(null) }
    val now = rememberNow()
    val t = remote
    WCard(live = t != null && t.status.state != State.OFF) {
        if (t == null) Small(TheftText.NO_STATE.get(lang)) else {
            Text(TheftText.state(t.status, lang, now), style = WeemType.titleS)
            t.status.fix?.let { f ->
                Small(TheftText.LAST_FIX.get(lang).format(TheftText.ago(now - f.atMs, lang), f.accuracyM.toInt()))
                WButton(TheftText.OPEN_MAP.get(lang), {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TheftText.mapUrl(f))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, kind = BtnKind.TEXT, icon = WeemIcons.Map)
            }
        }
        val st = t?.status?.state ?: State.OFF
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            if (st == State.OFF) WButton(TheftText.PARK.get(lang), { note = (if (TheftRuntime.requestArm()) TheftText.SENT else TheftText.NOT_SENT).get(lang) }, Modifier.weight(1f), icon = WeemIcons.Shield)
            else WButton(TheftText.DISARM.get(lang), { note = (if (TheftRuntime.requestDisarm()) TheftText.SENT else TheftText.NOT_SENT).get(lang) }, Modifier.weight(1f), kind = BtnKind.DANGER)
            if (st == State.ALARM) WButton(TheftText.SILENCE.get(lang), { TheftRuntime.silenceLocal(ctx) }, Modifier.weight(1f), kind = BtnKind.OUTLINE)
        }
        note?.let { Small(it) }
    }
}

/** Receiver'da her sekmenin üstü: geri sayımda küçük kart ("İptal"; LINK.md 5.1). Uyarı/alarm ekranı [TheftAlarm]. */
@Composable
fun TheftOverlay(lang: Lang) {
    val role by LinkHub.role.collectAsState()
    if (role != LinkRole.RECEIVER) return
    val s by TheftRuntime.status.collectAsState()
    val now = rememberNow()
    if (s.state == State.ARMING) Banner(
        TheftText.state(s, lang, now), Modifier.padding(horizontal = WeemSpace.l, vertical = WeemSpace.s).floating(RoundedCornerShape(WeemRadius.card)),
        tone = WTone.WARN, icon = WeemIcons.Timer,
        trailing = { WButton(TheftText.CANCEL.get(lang), { TheftRuntime.cancelArmingLocal() }, kind = BtnKind.TEXT) },
    )
}

/**
 * Receiver'da uyarı/alarmda kırmızı tam ekran: kabuğun (alt menü dahil) üstünde, dokunuşu alttakine geçirmez. Kapatma PIN'le; PIN
 * yoksa 3 sn basılı tutarak (LINK.md 5.1).
 */
@Composable
fun TheftAlarm(lang: Lang) {
    val role by LinkHub.role.collectAsState()
    if (role != LinkRole.RECEIVER) return
    val ctx = LocalContext.current
    val s by TheftRuntime.status.collectAsState()
    if (!s.alarming) return
    val now = rememberNow()
    // Alarm her temada aynı koyu kırmızı (gündüz paletinin tehlike rengi; beyaz yazı 8,5:1).
    Box(
        Modifier.fillMaxSize().background(Color(Palette.light.danger))
            // Boş dokunuş yakalayıcı: alttaki sekmeler ve alt menü dokunuş almaz.
            .pointerInput(Unit) { detectTapGestures { } }
            .safeDrawingPadding().padding(WeemSpace.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
            Icon(WeemIcons.WarningFilled, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            Text(TheftText.OVERLAY_WATCHED.get(lang), color = Color.White, style = WeemType.headline, textAlign = TextAlign.Center)
            Text(TheftText.state(s, lang, now), color = Color.White, style = WeemType.bodyL, textAlign = TextAlign.Center)
            Disarm(lang, onAlarm = true) { pin -> TheftRuntime.disarmLocal(ctx, pin) }
        }
    }
}

/** Poco'da kapatma: PIN ayarlıysa PIN, yoksa 3 sn basılı tutma (alarm ekranı ve devredeki koruma kartı). */
@Composable
private fun Disarm(lang: Lang, onAlarm: Boolean, disarm: (String?) -> Boolean) {
    val ctx = LocalContext.current
    val hasPin = remember { TheftRuntime.hasPin(ctx) }
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val ink = if (onAlarm) Color.White else MaterialTheme.colorScheme.onSurface
    if (hasPin) {
        val colors = if (onAlarm) OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = .7f),
            focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(alpha = .85f), cursorColor = Color.White,
        ) else OutlinedTextFieldDefaults.colors()
        OutlinedTextField(pin, { v -> pin = v.filter(Char::isDigit).take(4); wrong = false }, singleLine = true, colors = colors,
            shape = RoundedCornerShape(WeemRadius.tile), textStyle = WeemType.numM,
            label = { Text(TheftText.PIN_ENTER.get(lang)) }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        if (onAlarm) Button(
            onClick = { wrong = !disarm(pin); pin = "" }, enabled = pin.length == 4, shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(Palette.light.danger)),
            modifier = Modifier.heightIn(min = WeemSize.ride),
        ) { Text(TheftText.DISARM.get(lang), style = WeemType.labelL) }
        else WButton(TheftText.DISARM.get(lang), { wrong = !disarm(pin); pin = "" }, kind = BtnKind.DANGER_FILLED, enabled = pin.length == 4, ride = true)
        if (wrong) Text(TheftText.PIN_WRONG.get(lang), color = ink, style = WeemType.titleS)
    } else {
        Row(
            Modifier.background(ink.copy(alpha = 0.16f), RoundedCornerShape(WeemRadius.panel)).padding(horizontal = WeemSpace.xxl, vertical = WeemSpace.xl)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val start = System.currentTimeMillis()
                        tryAwaitRelease()
                        if (System.currentTimeMillis() - start >= HOLD_MS) disarm(null)
                    })
                },
            horizontalArrangement = Arrangement.spacedBy(WeemSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(WeemIcons.TouchApp, contentDescription = null, tint = ink)
            Text(TheftText.HOLD.get(lang), color = ink, style = WeemType.titleS)
        }
    }
}

private const val HOLD_MS = 3_000L

@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    return now
}

@Composable
private fun Small(text: String) {
    Text(text, style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
