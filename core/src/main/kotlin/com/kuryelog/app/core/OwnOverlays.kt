package com.kuryelog.app.core

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import com.kuryelog.logic.core.OverlayPalette
import java.util.concurrent.CopyOnWriteArrayList

/**
 * WEEM'in kurye uygulamaları üstündeki pencereleri (CAPTURE.md 3.5): modüller ekrandaki dikdörtgenlerini veren işlevi kaydeder
 * (rozet `:orders`, yüzen panel `:scooter`); `:capture` tüm ekran görüntüsünde hepsini siyaha boyar (RS `WatchService.maskOverlays`:
 * rozet + `SurusPaneli.floatingBounds`). Ana iş parçacığında çağrılır.
 */
object OwnOverlays {
    private val sources = CopyOnWriteArrayList<() -> Rect?>()

    fun add(source: () -> Rect?) { sources += source }
    fun remove(source: () -> Rect?) { sources.remove(source) }
    /** Bir kaynak hata verirse atlanır (RS `runCatching { SurusPaneli.floatingBounds() }`). */
    fun rects(): List<Rect> = sources.mapNotNull { runCatching { it() }.getOrNull() }

    /** Pencerelerin renkleri (gece/gündüz; RS `SurusPaneli.palette(dark)`). */
    fun palette(ctx: Context): OverlayPalette = OverlayPalette.forMode(dark(ctx))
    fun dark(ctx: Context): Boolean = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
