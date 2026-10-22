package com.kuryelog.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Kart (`docs/TASARIM.md` 11): `surface`, köşe 16 (büyük 24), iç 16 (büyük 20), gündüz düz, gece 1 dp çizgi. [tone] verilirse zemin
 * ton rengi (Wolt, Bolt, uyarı…); [live] canlı kart (2 dp vurgu kenarı).
 */
@Composable
fun WCard(
    modifier: Modifier = Modifier,
    tone: WTone? = null,
    live: Boolean = false,
    large: Boolean = false,
    onClick: (() -> Unit)? = null,
    padding: Dp = if (large) WeemSpace.xl else WeemSpace.l,
    spacing: Dp = WeemSpace.s,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette()
    val shape = RoundedCornerShape(if (large) WeemRadius.panel else WeemRadius.card)
    val color = (if (tone == null) p.surface else p.container(tone)).color()
    val border = when {
        live -> BorderStroke(2.dp, p.accent.color())
        p.isDark && tone == null -> BorderStroke(1.dp, p.outline.color())
        else -> null
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, contentColor = p.text.color(), border = border, content = body)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, contentColor = p.text.color(), border = border, content = body)
    }
}

/** 8 dp durum noktası. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/**
 * Durum hapı: 28 dp, nokta (ya da simge) + `labelM`; ton rengi yumuşak geçer. "Yolda" (`MOVE`) nötr zeminde rota mavisi nokta.
 */
@Composable
fun StatusPill(
    text: String,
    tone: WTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dot: Boolean = icon == null,
) {
    val p = palette()
    val bg by animateColorAsState(p.container(tone).color(), tween(WeemMotion.STATE), label = "pill-bg")
    val fg by animateColorAsState(p.ink(tone).color(), tween(WeemMotion.STATE), label = "pill-fg")
    val mark by animateColorAsState(p.mark(tone).color(), tween(WeemMotion.STATE), label = "pill-mark")
    Row(
        modifier.heightIn(min = 28.dp).background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = mark, modifier = Modifier.size(16.dp))
        else if (dot) Dot(mark)
        Text(text, style = WeemType.labelM, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Sayı rozeti (24 dp, vurgu): sıradaki durak sayısı, "+1". */
@Composable
fun CountBadge(text: String, modifier: Modifier = Modifier, tone: WTone = WTone.ACCENT) {
    val p = palette()
    val (bg, fg) = if (tone == WTone.ACCENT) p.accent to p.accentInk else p.container(tone) to p.ink(tone)
    Box(modifier.heightIn(min = 24.dp).background(bg.color(), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, style = WeemType.labelS.copy(fontSize = 12.sp), color = fg.color(), maxLines = 1)
    }
}

/**
 * Satır içi banner (`docs/TASARIM.md` 10): ton zemini + ton simgesi + metin; dokunulursa sağda ›. Kırmızı düz satır ve "  !" yerine.
 */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    tone: WTone = WTone.WARN,
    icon: ImageVector? = defaultIcon(tone),
    title: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = palette()
    val ink = p.ink(tone).color()
    val body: @Composable () -> Unit = {
        Row(
            Modifier.heightIn(min = WeemSize.touch).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(WeemSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = p.mark(tone).color(), modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                if (title != null) Text(title, style = WeemType.titleS, color = ink)
                Text(text, style = WeemType.bodyM.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = ink)
            }
            trailing?.invoke()
            if (onClick != null) Icon(WeemIcons.ChevronRight, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        }
    }
    val shape = RoundedCornerShape(WeemRadius.card)
    if (onClick != null) Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = p.container(tone).color(), contentColor = ink, content = body)
    else Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = p.container(tone).color(), contentColor = ink, content = body)
}

fun defaultIcon(tone: WTone): ImageVector? = when (tone) {
    WTone.DANGER -> WeemIcons.Error
    WTone.WARN -> WeemIcons.Warning
    WTone.POSITIVE -> WeemIcons.CheckCircle
    WTone.NEUTRAL, WTone.ACCENT -> WeemIcons.Info
    else -> null
}

/** Büyük sayı: etiket üstte (`bodyS`), değer `displayM`; sığmazsa küçülür, kesilmez. */
@Composable
fun HeroNumber(value: String, modifier: Modifier = Modifier, label: String? = null, style: TextStyle = WeemType.displayM, color: Color = Color.Unspecified) {
    Column(modifier) {
        if (label != null) Text(label, style = WeemType.bodyS, color = palette().muted.color())
        FitText(value, style = style, color = color)
    }
}

/** Tek satır sayı: sığmazsa en çok yarı boyuna dek küçülür (sayı asla kesilmez; `docs/TASARIM.md` 9). */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, textAlign: TextAlign? = null) {
    val max = style.fontSize
    Text(
        text, modifier = modifier, style = style, color = color, maxLines = 1, softWrap = false, textAlign = textAlign,
        autoSize = TextAutoSize.StepBased(minFontSize = max * 0.5f, maxFontSize = max, stepSize = 1.sp),
    )
}

/**
 * Gösterge karosu: değer `numM` + etiket `labelS`; zemin `raised`, köşe 16. Dokunulabilirse (ortalama listesi) en az 56 dp ve sağ üstte ›.
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: WTone? = null,
    onClick: (() -> Unit)? = null,
    onRaised: Boolean = false,
) {
    val p = palette()
    val shape = RoundedCornerShape(WeemRadius.card)
    val bg = (if (onRaised) p.surface else p.raised).color()
    val body: @Composable () -> Unit = {
        Row(Modifier.heightIn(min = if (onClick != null) 56.dp else 0.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FitText(value, style = WeemType.numM, color = if (tone == null) p.text.color() else p.onSurface(tone).color())
                Text(label, style = WeemType.labelS, color = p.muted.color(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (onClick != null) Icon(WeemIcons.ChevronRight, contentDescription = null, tint = p.muted.color(), modifier = Modifier.size(18.dp))
        }
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier, shape = shape, color = bg, content = body)
    else Surface(modifier = modifier, shape = shape, color = bg, content = body)
}

/** Bölüm başlığı (`labelM`, ikincil renk); ekran okuyucuya başlık. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = WeemType.labelM,
        color = palette().muted.color(),
        modifier = modifier.padding(start = WeemSpace.xs, top = WeemSpace.xxl, bottom = WeemSpace.s).semantics { heading() },
    )
}

/** Boş durum (`docs/TASARIM.md` 10): 72 dp daire içinde simge + başlık + açıklama + isteğe bağlı düğme. */
@Composable
fun EmptyState(title: String, modifier: Modifier = Modifier, icon: ImageVector = WeemIcons.Info, body: String? = null, action: (@Composable () -> Unit)? = null) {
    val p = palette()
    Column(modifier.fillMaxWidth().padding(vertical = WeemSpace.xxl, horizontal = WeemSpace.l), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(p.raised.color(), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = p.muted.color(), modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(WeemSpace.m))
        Text(title, style = WeemType.titleS, textAlign = TextAlign.Center)
        if (body != null) Text(body, style = WeemType.bodyS, color = p.muted.color(), textAlign = TextAlign.Center, modifier = Modifier.padding(top = WeemSpace.xs))
        if (action != null) Box(Modifier.padding(top = WeemSpace.l)) { action() }
    }
}

/** Yükleniyor iskeleti: `high` blok, hafif parıltı (animasyon kapalıysa sabit). */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f, targetValue = .55f, animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "skeleton-alpha",
    )
    Box(modifier.fillMaxWidth().height(height).alpha(pulse).background(palette().high.color(), RoundedCornerShape(WeemRadius.badge)))
}

private val leadingGlyph = Regex("^[▶■⚠✕⌕●✔⏹↻✎⇅⌂▥◉◎›‹⤓]️?\\s+")

/** Metnin başındaki işaret önekini ("▶  ", "⚠ " …) atar; yerine simge çizilir (K4 #6). Metnin kendisi aynı kalır. */
fun String.withoutGlyph(): String = replace(leadingGlyph, "")
