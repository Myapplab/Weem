package com.kuryelog.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kuryelog.app.capture.CaptureStatus
import com.kuryelog.app.core.Home
import com.kuryelog.app.scooter.Scooter
import com.kuryelog.app.scooter.ScooterShiftLink
import com.kuryelog.logic.app.BatteryAction
import com.kuryelog.logic.app.BatteryGuide
import com.kuryelog.logic.app.PermKind
import com.kuryelog.logic.app.PermissionState
import com.kuryelog.logic.app.Permissions
import com.kuryelog.logic.app.SetupText
import com.kuryelog.logic.app.Vendor
import com.kuryelog.logic.core.Lang

/** G5 (A7.2): izin durumunu Android'den okur (RS `MainActivity.kt:1388-1394`). Okunamayan durum "verilmedi" sayılır. */
object PermissionProbe {
    fun read(ctx: Context): PermissionState = PermissionState(
        accessibility = secureHas(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
        notificationListener = secureHas(ctx, "enabled_notification_listeners"),
        location = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION),
        backgroundLocation = granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        bluetoothNeeded = Build.VERSION.SDK_INT >= 31 && runCatching { Scooter.selected(ctx) }.getOrDefault(false),
        bluetooth = runCatching { !ScooterShiftLink.needsBluetoothPermission(ctx) }.getOrDefault(false),
        batteryUnrestricted = runCatching { ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true }.getOrDefault(false),
        homeSet = runCatching { Home.point(ctx) != null }.getOrDefault(false),
    )

    private fun granted(ctx: Context, p: String) = runCatching { ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    private fun secureHas(ctx: Context, key: String) =
        runCatching { (Settings.Secure.getString(ctx.contentResolver, key) ?: "").contains("${ctx.packageName}/") }.getOrDefault(false)
}

/** Android ayar sayfaları; açılamayan sayfa uygulama bilgisine, o da açılamazsa genel ayarlara düşer. */
object SystemPages {
    fun appInfo(ctx: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))

    /** MIUI/HyperOS otomatik başlatma listesi (güvenlik merkezi); başka telefonda yok, uygulama bilgisine düşer. */
    fun autostart(): Intent = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))

    fun intent(ctx: Context, action: BatteryAction): Intent = when (action) {
        BatteryAction.AUTOSTART -> autostart()
        BatteryAction.APP_INFO -> appInfo(ctx)
        BatteryAction.BATTERY_LIST -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    fun open(ctx: Context, intent: Intent) {
        for (i in listOf(intent, appInfo(ctx), Intent(Settings.ACTION_SETTINGS))) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }
}

/** İzin durumu; ekrana her dönüşte (Android ayarlarından geri gelince) yeniden okunur. */
@Composable
fun rememberPermissionState(refresh: Int = 0): PermissionState {
    val ctx = LocalContext.current
    var resumed by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumed++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    return remember(resumed, refresh) { PermissionProbe.read(ctx) }
}

/**
 * İzinler kartı (G5; RS `MainActivity.kt:1188-1210`, `ORDERS.md` 13.7): başlık "✓" ya da kırmızı "! n/m", eksik varsa açık gelir; her satır
 * kendi Android sayfasını ya da izin isteğini açar. Pil satırı yalnız bilgi ("isteğe bağlı"), ayrı bildirim yok. [alwaysOpen]: kurulum adımı.
 */
@Composable
fun PermissionsCard(lang: Lang, modifier: Modifier = Modifier, alwaysOpen: Boolean = false) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val s = rememberPermissionState(refresh)
    var userOpen by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var restrictedOpen by rememberSaveable { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        // Vardiyada Bluetooth izni verildi: scooter bağlantısı bir sonraki vardiyayı beklemeden açılır (RS `onRequestPermissionsResult` 3).
        ctx.safely { if (ScooterShiftLink.autoConnect(ctx) && ScooterShiftLink.canConnectNow(ctx)) ScooterShiftLink.connectNow(ctx) }
    }
    val ok = Permissions.allOk(s)
    val open = alwaysOpen || Permissions.startsOpen(s, userOpen)
    val warn = MaterialTheme.colorScheme.error
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !alwaysOpen, role = Role.Button) { userOpen = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(SetupText.PERMS_TITLE.get(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Permissions.badge(s), color = if (ok) MaterialTheme.colorScheme.primary else warn, style = MaterialTheme.typography.titleMedium)
            }
            if (!open) return@Column
            for (row in Permissions.rows(s)) {
                PermLine(row.kind.label.get(lang), if (row.ok) "✓" else "!", if (row.ok) null else warn) {
                    ctx.safely {
                        when (row.kind) {
                            PermKind.ACCESSIBILITY -> CaptureStatus.openAccessibilitySettings(ctx)
                            PermKind.NOTIFICATION_LISTENER -> SystemPages.open(ctx, Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                            PermKind.LOCATION -> askLocation.launch(locationPermissions())
                            PermKind.BACKGROUND_LOCATION ->
                                if (!s.location) askLocation.launch(locationPermissions()) else askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            PermKind.BLUETOOTH -> askBluetooth.launch(ScooterShiftLink.bluetoothPermissions)
                        }
                    }
                }
                if (row.kind == PermKind.NOTIFICATION_LISTENER) Info(SetupText.PERM_NOTIF_NOTE.get(lang))
            }
            if (Permissions.backgroundNote(s)) Info(SetupText.PERM_BGLOC_HOME.get(lang))
            PermLine(Permissions.batteryText(s).get(lang), Permissions.batteryBadge(s)?.get(lang) ?: "✓", null) {
                ctx.safely { SystemPages.open(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
            Info(SetupText.BATTERY_HELP.get(lang))
            TextButton(onClick = { restrictedOpen = !restrictedOpen }) {
                Text((if (restrictedOpen) "▾ " else "▸ ") + SetupText.RESTRICTED_TITLE.get(lang))
            }
            if (restrictedOpen) {
                Info(SetupText.RESTRICTED_HELP.get(lang))
                TextButton(onClick = { ctx.safely { SystemPages.open(ctx, SystemPages.appInfo(ctx)) } }) { Text(SetupText.OPEN_APP_INFO.get(lang)) }
                TextButton(onClick = { ctx.safely { SystemPages.open(ctx, Intent(Settings.ACTION_APPLICATION_SETTINGS)) } }) { Text(SetupText.OPEN_ALL_APPS.get(lang)) }
            }
        }
    }
}

/** Pil rehberi (G5): telefon ailesine göre adımlar ve ayar düğmeleri. */
@Composable
fun BatteryGuideCard(lang: Lang, modifier: Modifier = Modifier, vendor: Vendor = Vendor.of(Build.MANUFACTURER, Build.BRAND)) {
    val ctx = LocalContext.current
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(SetupText.GUIDE_TITLE.get(lang), style = MaterialTheme.typography.titleMedium)
            Text(BatteryGuide.title(vendor).get(lang), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            BatteryGuide.steps(vendor).forEachIndexed { i, step ->
                Text("${i + 1}. ${step.get(lang)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
            Info(SetupText.GUIDE_NAMES_VARY.get(lang))
            for (a in BatteryGuide.actions(vendor)) {
                OutlinedButton(onClick = { ctx.safely { SystemPages.open(ctx, SystemPages.intent(ctx, a)) } }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(a.label.get(lang))
                }
            }
        }
    }
}

@Composable
private fun PermLine(label: String, badge: String, color: androidx.compose.ui.graphics.Color?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = color ?: MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(badge, color = color ?: MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Info(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
}
