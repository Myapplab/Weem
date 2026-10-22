package com.kuryelog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Satırın sol simge kutusu: 40 dp, `raised` (ya da ton zemini), köşe 12. */
@Composable
fun IconBox(icon: ImageVector, modifier: Modifier = Modifier, tone: WTone = WTone.NEUTRAL) {
    val p = palette()
    Box(
        modifier.size(40.dp).background(p.container(tone).color(), RoundedCornerShape(WeemRadius.tile)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = (if (tone == WTone.NEUTRAL) p.text else p.mark(tone)).color(), modifier = Modifier.size(22.dp)) }
}

/**
 * Liste satırı (`docs/TASARIM.md` 11): tek satır 56 dp, iki satır 72 dp; solda simge kutusu ya da [leading]; başlık `titleS`, alt satır
 * `bodyS`; sağda [trailing] ve/veya ›. Bütün satır dokunulur.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTone: WTone = WTone.NEUTRAL,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = false,
    enabled: Boolean = true,
    titleTone: WTone? = null,
    titleStyle: androidx.compose.ui.text.TextStyle = if (subtitle == null && icon == null && leading == null) WeemType.bodyL else WeemType.titleS,
    onClick: (() -> Unit)? = null,
) {
    val p = palette()
    val alpha = if (enabled) 1f else .38f
    val clickMod = if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 56.dp else 72.dp).then(clickMod)
            .padding(start = WeemSpace.l, end = WeemSpace.m, top = WeemSpace.s, bottom = WeemSpace.s),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else if (icon != null) IconBox(icon, tone = iconTone)
        Column(Modifier.weight(1f)) {
            Text(
                title, style = titleStyle, maxLines = 3, overflow = TextOverflow.Ellipsis,
                color = (if (titleTone != null) p.onSurface(titleTone) else p.text).color().copy(alpha = alpha),
            )
            if (subtitle != null) Text(subtitle, style = WeemType.bodyS, color = p.muted.color().copy(alpha = alpha))
        }
        trailing?.invoke(this)
        if (chevron) Icon(WeemIcons.ChevronRight, contentDescription = null, tint = p.muted.color(), modifier = Modifier.size(24.dp))
    }
}

/** Değer satırı: solda başlık, sağda değer (`numS`) ve ›; dokununca düzenleme açılır. */
@Composable
fun ValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val p = palette()
    ListRow(
        title = title, subtitle = subtitle, icon = icon, enabled = enabled, onClick = onClick, chevron = true, modifier = modifier,
        trailing = { Text(value, style = WeemType.numS, color = p.text.color(), maxLines = 1) },
    )
}

/** Anahtarlı satır: bütün satır anahtardır (`Role.Switch`); [tag] satıra verilir (testler `assertIsOn` ile okur); [extra] anahtarın solunda (ör. bilgi düğmesi). */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tag: String? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        titleStyle = if (subtitle == null && icon == null) WeemType.bodyL else WeemType.titleS,
        modifier = modifier.let { if (tag != null) it.testTag(tag) else it }
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        trailing = {
            extra?.invoke()
            Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = weemSwitchColors())
        },
    )
}

@Composable
fun weemSwitchColors() = palette().let { p ->
    SwitchDefaults.colors(
        checkedThumbColor = p.accentInk.color(), checkedTrackColor = p.accent.color(), checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = p.muted.color(), uncheckedTrackColor = p.raised.color(), uncheckedBorderColor = p.muted.color(),
    )
}

/** Tek seçim satırı: solda radyo, bütün satır seçilir (`Role.RadioButton`); [trailingIcon] sağda ikincil renkte. */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
    tag: String? = null,
) {
    val p = palette()
    ListRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        titleStyle = WeemType.bodyL,
        modifier = modifier.let { if (tag != null) it.testTag(tag) else it }
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        leading = {
            RadioButton(
                selected = selected, onClick = null, enabled = enabled, modifier = Modifier.size(20.dp),
                colors = RadioButtonDefaults.colors(selectedColor = p.accent.color(), unselectedColor = p.muted.color()),
            )
        },
        trailing = if (trailingIcon != null) ({ Icon(trailingIcon, contentDescription = null, tint = p.muted.color()) }) else null,
    )
}

/** Onay kutulu satır (`Role.Checkbox`). */
@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    tag: String? = null,
) {
    val p = palette()
    ListRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        titleStyle = WeemType.bodyL,
        modifier = modifier.let { if (tag != null) it.testTag(tag) else it }
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        leading = {
            Checkbox(
                checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.size(20.dp),
                colors = CheckboxDefaults.colors(checkedColor = p.accent.color(), checkmarkColor = p.accentInk.color(), uncheckedColor = p.muted.color()),
            )
        },
    )
}

/**
 * Ayar grubu (`docs/TASARIM.md` 11): bölüm başlığı + köşesi 16 kart içinde satırlar; [divided] iken satır araları 1 dp çizgi
 * ([dividerInset] soldan girinti: simgeli satırda 70, radyoda 52). Tek seçimli gruplar [selectableGroup] ile işaretlenir.
 */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    divided: Boolean = true,
    dividerInset: Dp = WeemSpace.l,
    selectableGroup: Boolean = false,
    content: @Composable () -> Unit,
) {
    val p = palette()
    Column(modifier.fillMaxWidth()) {
        if (title != null) SectionLabel(title)
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(WeemRadius.card),
            color = p.surface.color(),
            contentColor = p.text.color(),
            border = if (p.isDark) androidx.compose.foundation.BorderStroke(1.dp, p.outline.color()) else null,
        ) {
            DividedColumn(
                divider = if (divided) p.outline.color() else null,
                inset = dividerInset,
                modifier = if (selectableGroup) Modifier.selectableGroup() else Modifier,
                content = content,
            )
        }
        if (footer != null) Text(footer, style = WeemType.bodyS, color = p.muted.color(), modifier = Modifier.padding(start = WeemSpace.xs, top = WeemSpace.s))
    }
}

/** Çocukları alt alta dizer; [divider] verilirse aralarına 1 dp çizgi çizer (her çocuk bir satır). */
@Composable
fun DividedColumn(divider: Color?, inset: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val gaps = remember { GapHolder() }
    val drawMod = if (divider == null) Modifier else Modifier.drawBehind {
        val start = inset.toPx()
        for (y in gaps.ys) drawLine(divider, Offset(start, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
    Layout(content, modifier.fillMaxWidth().then(drawMod)) { measurables, constraints ->
        val line = if (divider == null) 0 else 1.dp.roundToPx()
        val loose = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val height = placeables.sumOf { it.height } + line * (placeables.size - 1).coerceAtLeast(0)
        val ys = FloatArray((placeables.size - 1).coerceAtLeast(0))
        layout(constraints.maxWidth, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            placeables.forEachIndexed { i, pl ->
                pl.placeRelative(0, y)
                y += pl.height
                if (i < placeables.size - 1) { ys[i] = y + line / 2f; y += line }
            }
            if (!gaps.last.contentEquals(ys)) { gaps.last = ys; gaps.ys = ys }
        }
    }
}

/** Çizgi konumları çizimde okunur: değişince yalnız yeniden çizilir. */
private class GapHolder {
    var last = FloatArray(0)
    var ys by mutableStateOf(FloatArray(0))
}
