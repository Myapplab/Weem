package com.kuryelog.logic.map.ui

/** Harita bildirim şeridi verisi (MAP.md 7: metin, renk, süre, "Geri al" eylemi). */
enum class ToastTone { GREEN, PURPLE, ORANGE, RED, BIKEWAY }

class MapToast(
    val text: String,
    val tone: ToastTone,
    val seconds: Int,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/** Tek bildirim şeridi: son gelen kalır. */
class Toasts {
    var current: MapToast? = null
        private set

    fun show(toast: MapToast) {
        current = toast
    }

    fun hide() {
        current = null
    }
}
