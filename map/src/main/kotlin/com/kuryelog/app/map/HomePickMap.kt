package com.kuryelog.app.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kuryelog.logic.map.ui.BaseMap

/**
 * Ev seçicinin harita yüzeyi (MAP.md 6.11): MapLibre vektör altlık (S39: gömülü karolarla ağsız; önceden OSM raster), yakınlık 17, döndürme kapalı; seçili nokta = kamera merkezi (kamera durunca
 * bildirilir), uzun basış kamerayı noktaya taşır; 50 m daire.
 */
@Composable
fun HomePickMap(state: HomePickSurfaceState, userAgent: String, modifier: Modifier = Modifier) {
    // Açılışta bir kez merkeze, sonra yalnız uzun basışta (moveSeq) kamera taşınır; sürükleme kamerayı kendisi taşır.
    val camera = remember(state.moveSeq) { MapCameraCommand(seq = state.moveSeq, center = state.center, zoom = if (state.moveSeq == 0) state.zoom else null) }
    MapLibreSurface(
        MapSurfaceModel(base = BaseMap.VECTOR, dark = state.dark, camera = camera, circle = state.center to state.radiusM),
        MapSurfaceEvents(onLongPress = state.onLongPress, onCameraIdle = { c, _, _ -> if (c != state.center) state.onCenterChange(c) }),
        modifier.fillMaxSize(),
        userAgent,
    )
}
