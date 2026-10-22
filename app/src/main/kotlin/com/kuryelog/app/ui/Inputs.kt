package com.kuryelog.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Süzgeç çipi (`docs/TASARIM.md` 11): 36 dp görünür / 48 dp hedef, hap; seçili `accentContainer` + ✓, seçili değilken 1,5 dp çerçeve.
 * [dot] seçili değilken solda 8 dp renk noktası (platform süzgeci).
 */
@Composable
fun WChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    dot: Color? = null,
) {
    val p = palette()
    val haptics = LocalHapticFeedback.current
    FilterChip(
        selected = selected,
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onClick() },
        enabled = enabled,
        modifier = modifier.height(36.dp),
        label = { Text(text, style = WeemType.labelM, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = when {
            selected -> ({ Icon(WeemIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) })
            icon != null -> ({ Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) })
            dot != null -> ({ Dot(dot) })
            else -> null
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = p.text.color(),
            iconColor = p.text.color(),
            selectedContainerColor = p.accentContainer.color(),
            selectedLabelColor = p.onAccentContainer.color(),
            selectedLeadingIconColor = p.onAccentContainer.color(),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled, selected = selected, borderColor = p.outlineStrong.color(), selectedBorderColor = Color.Transparent,
            borderWidth = 1.5.dp, selectedBorderWidth = 0.dp,
        ),
    )
}

/**
 * Bölümlü seçici: `raised` hap içinde 40 dp seçenekler (toplam 48 dp); seçili `surface` + `e1`. Seçenekler `Role.Tab`; [tags] testler için.
 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, tags: List<String>? = null) {
    val p = palette()
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth().background(p.raised.color(), CircleShape).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) p.surface.color() else Color.Transparent, tween(WeemMotion.STATE), label = "seg-bg")
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp)
                    .let { if (on) it.floating(CircleShape) else it }
                    .clip(CircleShape).background(bg)
                    .let { m -> tags?.getOrNull(i)?.let { m.testTag(it) } ?: m }
                    .selectable(selected = on, role = Role.Tab, onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(i) })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    color = (if (on) p.text else p.muted).color(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontFamily = Inter,
                )
            }
        }
    }
}
