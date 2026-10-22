package com.kuryelog.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Alt menü öğesi: simge (seçiliyken dolu) + etiket. */
class NavItem(val key: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val tag: String? = null)

/**
 * Alt menü (`docs/TASARIM.md` 11): 64 dp + sistem boşluğu, üstte 1 dp çizgi; seçili öğe 56×32 `accentContainer` hapı ve dolu simge,
 * etiket `labelS`. Öğeler `Role.Tab`.
 */
@Composable
fun WeemNavBar(items: List<NavItem>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = palette()
    Column(modifier.fillMaxWidth().background(p.surface.color())) {
        HorizontalDivider(thickness = 1.dp, color = p.outline.color())
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(NavigationBarDefaults.windowInsets).height(68.dp).padding(horizontal = 4.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (item in items) {
                val on = item.key == selected
                val pill by animateColorAsState(if (on) p.accentContainer.color() else Color.Transparent, tween(WeemMotion.STATE), label = "nav-pill")
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .let { if (item.tag != null) it.testTag(item.tag) else it }
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(item.key) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(width = 56.dp, height = 32.dp).background(pill, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(
                            if (on) item.selectedIcon else item.icon, contentDescription = null,
                            tint = (if (on) p.onAccentContainer else p.muted).color(), modifier = Modifier.size(WeemSize.icon),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label, style = WeemType.labelS, color = (if (on) p.text else p.muted).color(),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Alt ekran üst çubuğu: 64 dp; geri oku (48 dp, [backLabel] içerik açıklaması) + başlık `titleL` (tek satır) + en çok 2 simge düğmesi.
 * Sistem geri tuşu da [onBack]'i çağırır. [divider] kaydırınca alt çizgi.
 */
@Composable
fun WeemTopBar(
    title: String,
    onBack: (() -> Unit)?,
    backLabel: String,
    modifier: Modifier = Modifier,
    divider: Boolean = false,
    backTag: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val p = palette()
    if (onBack != null) BackHandler(onBack = onBack)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) WIconButton(WeemIcons.ArrowBack, backLabel, onBack, if (backTag != null) Modifier.testTag(backTag) else Modifier)
            Text(
                title, style = WeemType.titleL, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 12.dp, end = 8.dp).semantics { heading() },
            )
            actions()
        }
        HorizontalDivider(thickness = 1.dp, color = if (divider) p.outline.color() else Color.Transparent)
    }
}

/**
 * Alt ekran iskeleti: üst çubuk + kaydırılan içerik (yanlarda 16 dp, altta 24 dp). Ekran zemini `bg`; geri tuşu [onBack].
 */
@Composable
fun SubScreen(
    title: String,
    backLabel: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scroll: ScrollState? = rememberScrollState(),
    spacing: Dp = WeemSpace.m,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette()
    Column(modifier.fillMaxSize().background(p.bg.color())) {
        WeemTopBar(title, onBack, backLabel, divider = (scroll?.value ?: 0) > 0, actions = actions)
        Column(
            Modifier.fillMaxSize().let { if (scroll != null) it.verticalScroll(scroll) else it }
                .padding(start = WeemSpace.l, end = WeemSpace.l, bottom = WeemSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** Sekme başlığı (`headline`) ve sağında isteğe bağlı durum hapı. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = WeemSpace.s, bottom = WeemSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(WeemSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = WeemType.headline, modifier = Modifier.weight(1f, fill = false).semantics { heading() })
        trailing?.invoke(this)
    }
}
