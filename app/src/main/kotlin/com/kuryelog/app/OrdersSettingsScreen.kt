package com.kuryelog.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kuryelog.app.core.Store
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.CheckRow
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.RadioRow
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.SwitchRow
import com.kuryelog.app.ui.ValueRow
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.economics.NetSettings
import com.kuryelog.logic.orders.CustomerCard
import com.kuryelog.logic.orders.OrderSettings
import com.kuryelog.logic.orders.RestaurantBubble

/**
 * Daha fazla → Siparişler ayarları (A4.2, S20 (b); RS `renderSettings` "Özel ayarlar"): filo komisyonu (platform başına), ekran ve kart
 * tercihleri, izlenen uygulamalar; ve yeni: kWh fiyatı, şarj verimi, günlük net hedef (N4, N8). Tercihler hemen yazılır ([Store.values]);
 * anahtarlar ve varsayılanlar `OrderSettings` / `NetSettings`'te. Sayı kutuları boş kaydedilirse değeri siler (komisyon hariç: 0).
 */
@Composable
fun OrdersSettingsScreen(lang: Lang, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    // `version` her yazıdan sonra artar; değerler Compose durumundan değil tercihlerden okunur.
    val values = remember(version) { Store.values(ctx) }
    fun write(map: Map<String, Any?>) { ctx.safely { Store.values(ctx).put(map); version++ } }

    SubScreen(Str.OSET_TITLE.get(lang), Str.BACK.get(lang), onBack, spacing = 0.dp) {
        // Sayı düzenleme ekranın içinde açılır (pencere değil): hem telefonda klavyeyle birlikte rahat görünür hem de Robolectric testinde çizilir.
        (dialog as? Dialog.Number)?.let { NumberEditor(lang, it) { dialog = null } }

        val groups = OrderSettings.platformPackages(Store.targets(ctx))
        SettingsGroup(Str.OSET_COMMISSION.get(lang), footer = Str.OSET_COMMISSION_NOTE.get(lang)) {
            if (groups.isEmpty()) ListRow(Str.OSET_NO_PLATFORM.get(lang), icon = WeemIcons.Info)
            for ((name, pkgs) in groups) {
                ValueRow(name, "%" + OrderSettings.pctText(OrderSettings.commissionPct(values, pkgs.first())), {
                    dialog = Dialog.Number(Str.OSET_COMMISSION_ASK.get(lang).format(name), OrderSettings.pctText(OrderSettings.commissionPct(values, pkgs.first())), 0.0, OrderSettings.MAX_COMMISSION, false) { v ->
                        write(OrderSettings.commissionWrite(pkgs, v ?: 0.0))
                    }
                }, icon = WeemIcons.Storefront)
            }
        }

        val prices = NetSettings.read(values)
        val effText = num(prices.chargeEfficiencyPct)
        val effSet = values.getFloat(NetSettings.CHARGE_EFFICIENCY, Float.NaN).isFinite()
        SettingsGroup(Str.OSET_NET.get(lang), footer = Str.OSET_NET_NOTE.get(lang)) {
            ValueRow(Str.OSET_KWH.get(lang), prices.kwhPrice?.let { num(it) } ?: Str.OSET_UNSET.get(lang), {
                dialog = Dialog.Number(Str.OSET_KWH.get(lang), prices.kwhPrice?.let { num(it) }.orEmpty(), 0.0, 10.0, true) { v -> write(mapOf(NetSettings.KWH_PRICE to v?.toFloat())) }
            })
            ValueRow(Str.OSET_EFFICIENCY.get(lang), "%" + if (effSet) effText else Str.OSET_DEFAULT.get(lang).format(effText), {
                dialog = Dialog.Number(Str.OSET_EFFICIENCY.get(lang), if (effSet) effText else "", NetSettings.MIN_EFFICIENCY_PCT, NetSettings.MAX_EFFICIENCY_PCT, true) { v -> write(mapOf(NetSettings.CHARGE_EFFICIENCY to v?.toFloat())) }
            })
            ValueRow(Str.OSET_GOAL.get(lang), prices.dailyGoal?.let { num(it) } ?: Str.OSET_UNSET.get(lang), {
                dialog = Dialog.Number(Str.OSET_GOAL.get(lang), prices.dailyGoal?.let { num(it) }.orEmpty(), 0.0, 10_000.0, true) { v -> write(mapOf(NetSettings.DAILY_GOAL to v?.takeIf { it > 0.0 }?.toFloat())) }
            })
        }

        val bubble = RestaurantBubble.widthPct(values.getInt(RestaurantBubble.WIDTH_KEY, RestaurantBubble.DEFAULT_WIDTH_PCT))
        val card = CustomerCard.sizePct(values.getInt(CustomerCard.SIZE_KEY, CustomerCard.DEFAULT_SIZE_PCT))
        SettingsGroup(Str.OSET_SCREEN.get(lang)) {
            SwitchRow(Str.OSET_OVERLAY.get(lang), values.getBoolean(OrderSettings.OVERLAY, true), { write(mapOf(OrderSettings.OVERLAY to it)) })
            SwitchRow(Str.OSET_OCR.get(lang), values.getBoolean(OrderSettings.OCR, true), { write(mapOf(OrderSettings.OCR to it)) })
            SwitchRow(Str.OSET_CARD.get(lang), values.getBoolean(OrderSettings.CUSTOMER_CARD, true), { write(mapOf(OrderSettings.CUSTOMER_CARD to it)) })
            // A3.11 (S16 (a)): yalnız `.dev` yapıda; sürüm yapısında anahtar da görünmez, akış da okumaz.
            if (BuildInfo.isDebuggable(ctx)) {
                SwitchRow(Str.OSET_SHADOW.get(lang), OrderSettings.shadowMode(values), { write(mapOf(OrderSettings.SHADOW to it)) }, subtitle = Str.OSET_SHADOW_NOTE.get(lang))
            }
            ValueRow(Str.OSET_BUBBLE.get(lang), "%$bubble", {
                dialog = Dialog.Choice(Str.OSET_BUBBLE_ASK.get(lang), RestaurantBubble.WIDTH_CHOICES, bubble) { write(mapOf(RestaurantBubble.WIDTH_KEY to it)) }
            })
            ValueRow(Str.OSET_CARD_SIZE.get(lang), "%$card", {
                dialog = Dialog.Choice(Str.OSET_CARD_SIZE_ASK.get(lang), CustomerCard.SIZE_CHOICES, card) { write(mapOf(CustomerCard.SIZE_KEY to it)) }
            })
        }

        val targets = Store.targets(ctx)
        SettingsGroup(Str.OSET_APPS.get(lang)) {
            if (targets.isEmpty()) ListRow(Str.OSET_APPS_NONE.get(lang), icon = WeemIcons.Warning, iconTone = WTone.DANGER, titleTone = WTone.DANGER)
            targets.sorted().forEach { ListRow(it, icon = WeemIcons.PhoneAndroid) }
            ListRow(Str.OSET_APPS_PICK.get(lang), icon = WeemIcons.Tune, iconTone = WTone.ACCENT, titleTone = WTone.ACCENT, chevron = true, onClick = { dialog = Dialog.Apps })
        }
    }

    when (val d = dialog) {
        null -> {}
        is Dialog.Number -> {}
        // K4: çok seçenekli pencere alttan açılan sayfa.
        is Dialog.Choice -> WSheet(onDismiss = { dialog = null }, title = d.title) {
            SettingsGroup(null, dividerInset = 52.dp, selectableGroup = true) {
                d.choices.forEach { c -> RadioRow("%$c", c == d.current, { d.save(c); dialog = null }) }
            }
            WButton(Str.CANCEL.get(lang), { dialog = null }, kind = BtnKind.TEXT, fill = true)
        }
        Dialog.Apps -> AppsDialog(lang) { saved -> dialog = null; if (saved) version++ }
    }
}

private sealed interface Dialog {
    class Number(val title: String, val initial: String, val min: Double, val max: Double, val clearable: Boolean, val save: (Double?) -> Unit) : Dialog
    class Choice(val title: String, val choices: List<Int>, val current: Int, val save: (Int) -> Unit) : Dialog
    data object Apps : Dialog
}

/** 12.5 → "12.5", 90.0 → "90" (gereksiz sıfır yok). */
private fun num(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else v.toString()

@Composable
private fun NumberEditor(lang: Lang, d: Dialog.Number, close: () -> Unit) {
    var text by remember(d) { mutableStateOf(d.initial) }
    var error by remember(d) { mutableStateOf(false) }
    WCard(Modifier.padding(top = WeemSpace.m), live = true) {
        Text(d.title, style = WeemType.titleM)
        OutlinedTextField(
            value = text, onValueChange = { text = it; error = false }, singleLine = true, isError = error,
            textStyle = WeemType.numM, shape = RoundedCornerShape(WeemRadius.tile),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        if (d.clearable) Text(Str.OSET_EMPTY_CLEARS.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error) Banner(Str.OSET_INVALID.get(lang).format(num(d.min), num(d.max)), tone = WTone.DANGER)
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WButton(Str.CANCEL.get(lang), close, Modifier.weight(1f), kind = BtnKind.OUTLINE)
            WButton(Str.SAVE.get(lang), {
                if (text.isBlank() && d.clearable) { d.save(null); close(); return@WButton }
                val v = NetSettings.parse(text, d.min, d.max)
                if (v == null) error = true else { d.save(v); close() }
            }, Modifier.weight(1f))
        }
    }
}

/** İzlenen uygulamalar (K4: uzun liste alttan açılan sayfada; kaydet/iptal altta). */
@Composable
private fun AppsDialog(lang: Lang, close: (saved: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { TargetPicker.pickList(ctx) }
    val checked = remember { mutableStateListOf<String>().apply { addAll(Store.targets(ctx)) } }
    WSheet(onDismiss = { close(false) }, title = Str.OSET_APPS.get(lang)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            SettingsGroup(null, dividerInset = 52.dp) {
                for ((pkg, label) in apps) {
                    CheckRow(label, pkg in checked, { on -> if (on) checked.add(pkg) else checked.remove(pkg) }, subtitle = pkg)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WButton(Str.CANCEL.get(lang), { close(false) }, Modifier.weight(1f), kind = BtnKind.OUTLINE)
            WButton(Str.SAVE.get(lang), { ctx.safely { TargetPicker.save(ctx, checked.toSet()) }; close(true) }, Modifier.weight(1f))
        }
    }
}
