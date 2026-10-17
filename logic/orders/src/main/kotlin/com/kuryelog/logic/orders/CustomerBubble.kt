package com.kuryelog.logic.orders

import kotlin.math.abs
import kotlin.math.ceil
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats CustomerBubble.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * F7.2 (Ekim planı madde 12, SORU-11/16): the customer card as a thin bubble like the restaurant's ([RestaurantBubble]).
 * Left [line]: Building · Floor · Flat · Door code, only the known ones, as the customer wrote them; none known → the name.
 * Right: only [minutes] of the platform's drop-off countdown (K29 [CustomerRecord.deadlineAt]); unknown → empty circle.
 * Below: [notes] in the customer's own words, at most [NOTES_MAX_SHARE] of the screen. A vertical swipe folds/unfolds the
 * notes, a horizontal one removes the card ([swipe]). When the card opens is still [CustomerCard]'s rule. Pure.
 */
object CustomerBubble {
    const val BUBBLE_DP = 56
    const val NOTES_MAX_SHARE = 0.25
    const val WIDTH_KEY = RestaurantBubble.WIDTH_KEY
    /** A horizontal swipe longer than this share of the bubble's width removes it; a vertical one past [TOGGLE_DP] folds it. */
    const val DISMISS_SHARE = 0.25
    const val TOGGLE_DP = 48

    enum class Swipe { NONE, TOGGLE, DISMISS }

    fun line(d: DeliveryDetails, name: String?, tl: (String, String) -> String): String {
        val parts = listOfNotNull(
            d.building?.trim()?.takeIf { it.isNotEmpty() }?.let { b -> if (b.length <= 4) tl("Blok $b", "Blok $b") else b },
            d.floor?.trim()?.takeIf { it.isNotEmpty() }?.let { f -> tl("$f. kat", "$f-cu mərtəbə") },
            d.apartment?.trim()?.takeIf { it.isNotEmpty() }?.let { a ->
                if (a.startsWith("Oda ")) tl(a, "Otaq " + a.removePrefix("Oda ")) else tl("D $a", "M $a") },
            d.doorCode?.trim()?.takeIf { it.isNotEmpty() }?.let { "Kod $it" })
        return if (parts.isNotEmpty()) parts.joinToString(" · ") else name?.takeIf { it.isNotBlank() } ?: tl("Müşteri", "Müştəri")
    }

    /** Whole minutes left (rounded up; negative when late, like the platform's own label). */
    fun minutes(deadlineAt: Long?, now: Long, tl: (String, String) -> String): String? {
        val at = deadlineAt ?: return null
        val m = ceil((at - now) / 60_000.0).toInt()
        return tl("$m dk", "$m dəq")
    }

    /** "2 sipariş" when one customer gets several orders (R1's one card), else null. */
    fun orders(count: Int, tl: (String, String) -> String): String? = if (count >= 2) tl("$count sipariş", "$count sifariş") else null

    /** The notes under the bubble: identity check, address details, note and instructions unchanged (instructions only when
     *  they differ from the note). */
    fun notes(d: DeliveryDetails, tl: (String, String) -> String): List<String> = listOfNotNull(
        tl("Kimlik kontrolü gerekli", "Şəxsiyyət yoxlaması lazımdır").takeIf { d.checkId },
        d.addressDetails?.takeIf { it.isNotBlank() }?.let { tl("Adres detayı: ", "Ünvan detalları: ") + it },
        d.note?.takeIf { it.isNotBlank() }?.let { tl("Not: ", "Qeyd: ") + it },
        d.instructions?.takeIf { it.isNotBlank() && it != d.note }?.let { tl("Talimat: ", "Təlimat: ") + it })

    /** [dx]/[dy]: the finger's travel in px; [widthPx] the bubble's width, [density] px per dp. */
    fun swipe(dx: Float, dy: Float, widthPx: Int, density: Float): Swipe = when {
        abs(dx) > abs(dy) && abs(dx) > widthPx * DISMISS_SHARE -> Swipe.DISMISS
        abs(dy) > abs(dx) && abs(dy) > TOGGLE_DP * density -> Swipe.TOGGLE
        else -> Swipe.NONE
    }
}
