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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.kuryelog.app.link.LinkHub
import com.kuryelog.app.link.TheftRuntime
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
    Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(TheftText.TITLE.get(lang), style = MaterialTheme.typography.titleMedium)
            when (role) {
                LinkRole.RECEIVER -> ReceiverTheft(lang)
                LinkRole.PROVIDER -> ProviderTheft(lang)
                LinkRole.OFF -> Small(TheftText.ONLY_TWO_PHONE.get(lang))
            }
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

    Small(TheftText.HINT.get(lang))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(TheftText.ENABLE.get(lang), Modifier.weight(1f))
        Switch(enabled, { if (TheftRuntime.setEnabled(ctx, it)) enabled = it }, enabled = !locked)
    }
    if (!enabled) return
    Check(TheftText.AUTO_STAGE.get(lang), autoStage, !locked) { autoStage = it; TheftRuntime.setAutoStage(ctx, it) }
    Check(TheftText.AUTO_AWAY.get(lang), autoAway, !locked) { autoAway = it; TheftRuntime.setAutoAway(ctx, it) }
    Text(TheftText.state(status, lang, now), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status.state == State.OFF) Button(onClick = { TheftRuntime.parkLocal() }) { Text(TheftText.PARK.get(lang)) }
        if (status.state == State.ARMING) OutlinedButton(onClick = { TheftRuntime.cancelArmingLocal() }) { Text(TheftText.CANCEL.get(lang)) }
        if (!locked) OutlinedButton(onClick = { TheftRuntime.testSiren(ctx) }) { Text(TheftText.TEST_SIREN.get(lang)) }
    }
    if (locked) {
        Text(TheftText.LOCKED.get(lang), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Disarm(lang, MaterialTheme.colorScheme.onSurface) { p -> TheftRuntime.disarmLocal(ctx, p) }
        return
    }

    Text(TheftText.PIN.get(lang), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    Small(if (hasPin) TheftText.PIN_IS_SET.get(lang) else TheftText.PIN_NONE.get(lang))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(pin, { v -> pin = v.filter(Char::isDigit).take(4) }, Modifier.weight(1f), singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        TextButton(onClick = { if (TheftRuntime.setPin(ctx, pin)) { hasPin = true; pin = "" } }, enabled = pin.length == 4) { Text(TheftText.PIN_SET.get(lang)) }
    }

    Text(TheftText.SMS_TITLE.get(lang), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    Small(TheftText.SMS_HINT.get(lang))
    OutlinedTextField(n1, { n1 = it }, Modifier.fillMaxWidth(), label = { Text(TheftText.SMS_NUMBER.get(lang).format(1)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
    OutlinedTextField(n2, { n2 = it }, Modifier.fillMaxWidth(), label = { Text(TheftText.SMS_NUMBER.get(lang).format(2)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { TheftRuntime.setSmsNumbers(ctx, listOf(n1, n2)) }) { Text(TheftText.SMS_SAVE.get(lang)) }
        if (!smsAllowed) TextButton(onClick = { askSms.launch(Manifest.permission.SEND_SMS) }) { Text(TheftText.SMS_PERMISSION.get(lang)) }
    }
    if (!smsAllowed) Small(TheftText.SMS_NO_PERMISSION.get(lang))
    if (smsIssue) Text(TheftText.SMS_FAILED.get(lang), color = MaterialTheme.colorScheme.error)
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
    if (t == null) Small(TheftText.NO_STATE.get(lang)) else {
        Text(TheftText.state(t.status, lang, now), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        t.status.fix?.let { f ->
            Small(TheftText.LAST_FIX.get(lang).format(TheftText.ago(now - f.atMs, lang), f.accuracyM.toInt()))
            TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TheftText.mapUrl(f))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                Text(TheftText.OPEN_MAP.get(lang))
            }
        }
    }
    val st = t?.status?.state ?: State.OFF
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (st == State.OFF) Button(onClick = { note = (if (TheftRuntime.requestArm()) TheftText.SENT else TheftText.NOT_SENT).get(lang) }) { Text(TheftText.PARK.get(lang)) }
        else Button(onClick = { note = (if (TheftRuntime.requestDisarm()) TheftText.SENT else TheftText.NOT_SENT).get(lang) }) { Text(TheftText.DISARM.get(lang)) }
        if (st == State.ALARM) OutlinedButton(onClick = { TheftRuntime.silenceLocal(ctx) }) { Text(TheftText.SILENCE.get(lang)) }
    }
    note?.let { Small(it) }
}

/** Receiver'da her sekmenin üstü: geri sayımda küçük kart ("İptal"; LINK.md 5.1). Uyarı/alarm ekranı [TheftAlarm]. */
@Composable
fun TheftOverlay(lang: Lang) {
    val role by LinkHub.role.collectAsState()
    if (role != LinkRole.RECEIVER) return
    val s by TheftRuntime.status.collectAsState()
    val now = rememberNow()
    if (s.state == State.ARMING) Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(TheftText.state(s, lang, now), Modifier.weight(1f))
            TextButton(onClick = { TheftRuntime.cancelArmingLocal() }) { Text(TheftText.CANCEL.get(lang)) }
        }
    }
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
    Box(
        Modifier.fillMaxSize().background(Color(0xFFB71C1C))
            // Boş dokunuş yakalayıcı: alttaki sekmeler ve alt menü dokunuş almaz.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(TheftText.OVERLAY_WATCHED.get(lang), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(TheftText.state(s, lang, now), color = Color.White, modifier = Modifier.padding(top = 8.dp))
            Disarm(lang, Color.White) { pin -> TheftRuntime.disarmLocal(ctx, pin) }
        }
    }
}

/** Poco'da kapatma: PIN ayarlıysa PIN, yoksa 3 sn basılı tutma (alarm ekranı ve devredeki koruma kartı). */
@Composable
private fun Disarm(lang: Lang, color: Color, disarm: (String?) -> Boolean) {
    val ctx = LocalContext.current
    val hasPin = remember { TheftRuntime.hasPin(ctx) }
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    if (hasPin) {
        OutlinedTextField(pin, { v -> pin = v.filter(Char::isDigit).take(4); wrong = false }, Modifier.padding(top = 16.dp), singleLine = true,
            label = { Text(TheftText.PIN_ENTER.get(lang)) }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Button(onClick = { wrong = !disarm(pin); pin = "" }, enabled = pin.length == 4, modifier = Modifier.padding(top = 8.dp)) { Text(TheftText.DISARM.get(lang)) }
        if (wrong) Text(TheftText.PIN_WRONG.get(lang), color = color)
    } else {
        Box(
            Modifier.padding(top = 16.dp).background(color.copy(alpha = 0.2f)).padding(24.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val start = System.currentTimeMillis()
                        tryAwaitRelease()
                        if (System.currentTimeMillis() - start >= HOLD_MS) disarm(null)
                    })
                },
        ) { Text(TheftText.HOLD.get(lang), color = color) }
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
private fun Check(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Small(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}
