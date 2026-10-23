package com.kuryelog.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.floating
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.nav.NavState
import com.kuryelog.logic.map.ui.CameraMode
import com.kuryelog.logic.map.ui.HudLayout
import com.kuryelog.logic.map.ui.MapText

/** Harita sekmesinin görünümü (MAP.md 6.1–6.12): gün, navigasyon, sol göstergeler, hava; [arrivalFocus] varış odağı (hedefe ≤100 m). */
data class MapTabState(
    val day: DayView,
    val nav: NavState = NavState(),
    val metrics: MapMetricsState = MapMetricsState(),
    val weather: WeatherView = WeatherView(),
    val weatherStrip: Boolean = true,
    val arrivalFocus: Boolean = false,
    val cameraMode: CameraMode = CameraMode.FOLLOW_HEADING,
    val nowMs: Long = 0,
    /** Klavye açık: sol göstergeler ve alt panel yok. */
    val keyboard: Boolean = false,
    /** Rota paneli açık (`route-fab` ✕ gösterir). */
    val routeOpen: Boolean = false,
)

/** Harita sekmesinin eylemleri; ekranı kuran (`MapScreen`) bağlar. Dolmayan yuvalar (A6.6–A6.9) boş kalır. */
class MapTabActions(
    val onLocate: () -> Unit = {},
    val onRouteFab: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onWeatherRefresh: () -> Unit = {},
)

/**
 * Harita sekmesi (YolGo `nav_screen.dart:57-153`; MAP.md 6.3): harita yüzeyi + HUD (arama çubuğu `map-search`, hava şeridi, sol göstergeler, gün eylemleri, konum düğmesi,
 * alt yer düğmesi); sekmede menü, ayar düğmesi ve kayıt noktası yok. Varış odağında yalnız harita, bildirim şeridi (`toast-banner`), hatırlatıcı yuvası (`reminder-slot`), görev
 * uyarıları (`task-alerts`), ters yön kartı ve alttaki `arrival-address` kartı. Haritaya dokunmak panelleri gizlemez (S37; eski "yalnız harita" kipi kaldırıldı).
 * Dışarı dokunuş açık gün kartını katlar. Yuvalar ([toast], [reminder], [taskAlerts], [wrongWay], [routePanel]) sahip görevlerde dolar; [metrics] sol göstergeler (A6.7),
 * [topCard] arama çubuğunun üstünde (vardiya kapalıyken `ShiftCard`).
 */
@Composable
fun MapTab(
    state: MapTabState,
    lang: Lang,
    modifier: Modifier = Modifier,
    mapSurface: @Composable () -> Unit = {},
    onHome: () -> Unit = {},
    onStats: () -> Unit = {},
    onEnd: () -> Unit = {},
    actions: MapTabActions = MapTabActions(),
    toast: @Composable () -> Unit = {},
    reminder: @Composable () -> Unit = {},
    taskAlerts: @Composable () -> Unit = {},
    wrongWay: @Composable () -> Unit = {},
    routePanel: @Composable () -> Unit = {},
    metrics: @Composable () -> Unit = { MapMetrics(state.metrics, lang) },
    topCard: @Composable () -> Unit = {},
) {
    // Harita görünümü kipler arasında taşınır, yeniden kurulmaz (MapView/Google haritası pahalı; kamera korunur).
    val surface by rememberUpdatedState(mapSurface)
    val map = remember { movableContentOf { surface() } }
    val alerts = @Composable { Box(Modifier.fillMaxSize().testTag("task-alerts")) { taskAlerts() } }
    val toastSlot = @Composable { Box(Modifier.fillMaxWidth().testTag("toast-banner")) { toast() } }
    val reminderSlot = @Composable { Box(Modifier.fillMaxWidth().testTag("reminder-slot")) { reminder() } }
    val keyboard = state.keyboard
    if (state.arrivalFocus && !keyboard) {
        MapHudLayout(
            keyboard = false, map = map, modifier = modifier, alerts = alerts,
            header = { Column(Modifier.fillMaxWidth()) { toastSlot(); reminderSlot() } },
            footer = {
                Column(Modifier.fillMaxWidth()) {
                    wrongWay()
                    state.nav.activeStop?.let { ArrivalAddressCard(it.label, it.unitNote) }
                }
            },
        )
        return
    }
    var collapseSignal by remember { mutableIntStateOf(0) }
    var weatherOpen by remember { mutableStateOf(false) }
    var shiftCollapsed by remember { mutableStateOf(true) }
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    val hasRoute = state.nav.hasRoute || state.nav.busy || state.nav.pending != null
    // Gün kartı katlı/açık yerleşimde farklı yerde durur; durumu (katlı mı, 20 sn sayacı) korumak için aynı örnek taşınır (movableContentOf), girdileri güncel tutulur.
    val barDay by rememberUpdatedState(state.day)
    val barHasRoute by rememberUpdatedState(hasRoute)
    val barLang by rememberUpdatedState(lang)
    val barHome by rememberUpdatedState(onHome)
    val barStats by rememberUpdatedState(onStats)
    val barEnd by rememberUpdatedState(onEnd)
    val barSignal by rememberUpdatedState(collapseSignal)
    val bar = remember {
        movableContentOf {
            MapShiftActions(
                barDay, barHasRoute, barLang, barHome, barStats, barEnd,
                modifier = Modifier.onGloballyPositioned { cardBounds = it.boundsInRoot() },
                collapseSignal = barSignal, onCollapsedChange = { shiftCollapsed = it },
            )
        }
    }
    val locate = @Composable { SmartLocateButton(state.cameraMode, actions.onLocate, lang = lang) }
    Box(
        modifier.fillMaxSize().onGloballyPositioned { rootCoords = it }.pointerInput(Unit) {
            // Kartın dışına dokunuş (harita, başka düğme) açık gün kartını katlar; olay tüketilmez.
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.type != PointerEventType.Press) continue
                    val origin = rootCoords?.positionInRoot() ?: continue
                    val p = e.changes.first().position + origin
                    if (!shiftCollapsed && !cardBounds.contains(p)) collapseSignal++
                }
            }
        },
    ) {
        MapHudLayout(
            keyboard = keyboard, map = map, alerts = alerts,
            header = {
                Column(Modifier.fillMaxWidth()) {
                    topCard()
                    // A10.4d (K4): arama hapı ve sağında hava hapı; hava açılınca şerit aramanın altına iner.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                        SearchBar(lang, actions.onSearch, Modifier.weight(1f))
                        if (state.weatherStrip && !weatherOpen) {
                            WeatherChip(state.weather, lang, state.nowMs, onRefresh = actions.onWeatherRefresh, expanded = false, onExpandedChange = { weatherOpen = it })
                        }
                    }
                    if (state.weatherStrip && weatherOpen) {
                        Spacer(Modifier.height(WeemSpace.s))
                        WeatherChip(state.weather, lang, state.nowMs, onRefresh = actions.onWeatherRefresh, expanded = true, onExpandedChange = { weatherOpen = it })
                    }
                    Spacer(Modifier.height(WeemSpace.m))
                    if (!keyboard) metrics()
                    Spacer(Modifier.height(WeemSpace.s))
                    toastSlot()
                    reminderSlot()
                }
            },
            footer = {
                // 1.7.1: gün kartı katlıyken konum ve rota düğmeleri kartın yanına iner; açılınca yerlerine döner.
                // A10.4d (K4): konum düğmesi rota kartının üstünde; gün kartı katlıyken rota düğmesi onun sağında.
                if (shiftCollapsed) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { locate() }
                        wrongWay()
                        routePanel()
                        Row(verticalAlignment = Alignment.Bottom) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.BottomStart) { bar() }
                            Spacer(Modifier.width(WeemSpace.s))
                            RouteFab(actions.onRouteFab, lang = lang, open = state.routeOpen, busy = state.nav.busy, error = state.nav.error != null)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        locate()
                        wrongWay()
                        routePanel()
                        RouteFab(actions.onRouteFab, lang = lang, open = state.routeOpen, busy = state.nav.busy, error = state.nav.error != null)
                        bar()
                    }
                }
            },
        )
    }
}

/** Arama hapı (`map-search`; MAP.md 8.8): 56 dp, solda arama simgesi, ipucu "Adres veya mekân ara"; arama akışı A6.6. */
@Composable
private fun SearchBar(lang: Lang, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = palette()
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = p.surface.color(),
        contentColor = p.text.color(),
        modifier = modifier.fillMaxWidth().height(HudLayout.LOCATE_BUTTON_DP.dp).floating(CircleShape).testTag("map-search"),
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(WeemIcons.Search, contentDescription = null)
            Spacer(Modifier.width(WeemSpace.m))
            Text(MapText.SEARCH_HINT.get(lang), style = WeemType.bodyL, color = p.muted.color(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Varış odağında alttaki adres kartı (`arrival-address`; MAP.md 6.3, 7.10): adres büyük, kapı notu turuncu bantta 24 sp. */
@Composable
private fun ArrivalAddressCard(label: String, unitNote: String?) {
    val p = palette()
    val shape = RoundedCornerShape(WeemRadius.panel)
    Surface(
        shape = shape,
        color = p.surface.color(),
        contentColor = p.text.color(),
        modifier = Modifier.fillMaxWidth().floating(shape).testTag("arrival-address"),
    ) {
        Column(Modifier.padding(WeemSpace.xl), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
            Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
                IconBox(WeemIcons.LocationOnFilled, tone = WTone.CUSTOMER)
                Text(label, style = WeemType.titleL, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            if (!unitNote.isNullOrBlank()) Row(
                Modifier.fillMaxWidth().background(p.warnBg.color(), RoundedCornerShape(WeemRadius.card)).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(WeemIcons.DoorFront, contentDescription = null, tint = p.warn.color())
                Text(unitNote, color = p.warn.color(), style = WeemType.titleL.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold))
            }
        }
    }
}
