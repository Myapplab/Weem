package com.kuryelog.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Düğme türleri (`docs/TASARIM.md` 11): dolgu, tonal, çerçeveli, yazı; tehlike tonal (onayda dolgu); "Eve git" uyarı dolgusu. */
enum class BtnKind { PRIMARY, TONAL, OUTLINE, TEXT, DANGER, DANGER_FILLED, WARN_FILLED }

/**
 * Tek düğme bileşeni: hap, 48 dp (sürüşte [ride] 56 dp), `labelL`, solda isteğe bağlı 20 dp simge; uzun (Azerice) metin iki satıra
 * iner, kesilmez. [busy] iken simgenin yerinde ilerleme halkası. [haptic] verilirse dokununca titrer (`docs/TASARIM.md` 8: vardiya ve
 * kayıt başlat/bitir, kaydet → `Confirm`).
 */
@Composable
fun WButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    ride: Boolean = false,
    fill: Boolean = false,
    busy: Boolean = false,
    haptic: HapticFeedbackType? = null,
) {
    val p = palette()
    val haptics = LocalHapticFeedback.current
    val (container, content) = when (kind) {
        BtnKind.PRIMARY -> p.accent to p.accentInk
        BtnKind.TONAL -> p.accentContainer to p.onAccentContainer
        BtnKind.OUTLINE -> null to p.text
        BtnKind.TEXT -> null to p.accentText
        BtnKind.DANGER -> p.dangerBg to p.danger
        BtnKind.DANGER_FILLED -> p.danger to (if (p.isDark) p.bg else p.surface)
        BtnKind.WARN_FILLED -> p.warn to p.warnBg
    }
    val colors = ButtonDefaults.buttonColors(
        containerColor = container?.color() ?: Color.Transparent,
        contentColor = content.color(),
        disabledContainerColor = if (container == null) Color.Transparent else p.text.color().copy(alpha = .10f),
        disabledContentColor = p.text.color().copy(alpha = .38f),
    )
    val horizontal = when {
        kind == BtnKind.TEXT -> 14.dp
        ride -> 24.dp
        else -> 20.dp
    }
    Button(
        onClick = { if (haptic != null) haptics.performHapticFeedback(haptic); onClick() },
        modifier = modifier.heightIn(min = if (ride) WeemSize.ride else WeemSize.touch).let { if (fill) it.fillMaxWidth() else it },
        enabled = enabled,
        shape = CircleShape,
        colors = colors,
        border = if (kind == BtnKind.OUTLINE) BorderStroke(1.5.dp, (if (enabled) p.outlineStrong else p.outline).color()) else null,
        contentPadding = PaddingValues(horizontal = horizontal, vertical = 8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = content.color(), strokeWidth = 2.dp)
            else if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text,
                style = if (ride) WeemType.labelL.copy(fontSize = 16.sp, lineHeight = 20.sp) else WeemType.labelL,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Yuvarlak simge düğmesi: 48 dp (sürüşte 56); [description] ekran okuyucuya ve testlere gider. [tinted] `raised` zemin,
 * [selected] `accentContainer`.
 */
@Composable
fun WIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = WeemSize.touch,
    tinted: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    tone: WTone? = null,
    iconSize: Dp = if (size >= WeemSize.ride) WeemSize.iconRide else WeemSize.icon,
) {
    val p = palette()
    val container = when {
        selected -> p.accentContainer.color()
        tone != null -> p.container(tone).color()
        tinted -> p.raised.color()
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> p.text.color().copy(alpha = .38f)
        selected -> p.onAccentContainer.color()
        tone != null -> p.ink(tone).color()
        else -> p.text.color()
    }
    Surface(
        onClick = onClick,
        modifier = modifier.size(size).semantics { contentDescription = description },
        enabled = enabled,
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}

/** Haritada yüzen 56 dp yuvarlak düğme (`e1`); [selected] iken tonal. */
@Composable
fun Fab(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = WeemSize.ride,
    content: (@Composable () -> Unit)? = null,
) {
    val p = palette()
    Surface(
        onClick = onClick,
        modifier = modifier.size(size).floating(CircleShape).semantics { contentDescription = description },
        shape = CircleShape,
        color = (if (selected) p.accentContainer else p.surface).color(),
        contentColor = (if (selected) p.onAccentContainer else p.text).color(),
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            if (content != null) content() else Icon(icon, contentDescription = null, modifier = Modifier.size(WeemSize.iconRide))
        }
    }
}
