package com.kuryelog.logic.orders

import kotlin.math.abs
import kotlin.math.ceil
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (CustomerBubble.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.6'da.
object CustomerBubble {
    const val BUBBLE_DP = 56
    const val NOTES_MAX_SHARE = 0.25
    const val WIDTH_KEY = RestaurantBubble.WIDTH_KEY
    const val DISMISS_SHARE = 0.25
    const val TOGGLE_DP = 48
    enum class Swipe { NONE, TOGGLE, DISMISS }
    fun line(d: DeliveryDetails, name: String?, tl: (String, String) -> String): String = TODO("A3.6")
    fun minutes(deadlineAt: Long?, now: Long, tl: (String, String) -> String): String? = TODO("A3.6")
    fun orders(count: Int, tl: (String, String) -> String): String? = TODO("A3.6")
    fun notes(d: DeliveryDetails, tl: (String, String) -> String): List<String> = TODO("A3.6")
    fun swipe(dx: Float, dy: Float, widthPx: Int, density: Float): Swipe = TODO("A3.6")
}

