package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.day.OrderRec
import com.kuryelog.logic.map.geo.GeoPoint

/**
 * Sipariş değişimi (YolGo `NavController.build` :214-228, MAP.md 7.3): günün açık siparişinin numarası değişince (önceki boş değilse) hedef, restorandan
 * çıkılmadıysa restoran, çıkıldıysa müşteri; hedef yoksa rota silinir ([NavController.onOrderSwitch]). S39: A6.6'da denetleyici yazıldı ama `NavRuntime`
 * sipariş numarasını hep boş geçiyordu, davranış hiç çalışmıyordu.
 */
object OrderSwitch {
    data class Action(val order: Int?, val target: GeoPoint?, val toRestaurant: Boolean, val restaurantName: String?)

    /** [prev] numaradan [current] siparişe geçiş; değişim yoksa ya da önceki boşsa null (ilk sipariş rota açmaz). */
    fun on(prev: Int?, current: OrderRec?): Action? {
        val next = current?.n
        if (prev == null || prev == next) return null
        val toRestaurant = current?.restLeftAtMs == null
        val target = if (toRestaurant) current?.restTarget else current?.customerTarget
        return Action(next, target, toRestaurant, if (toRestaurant) current?.restName else null)
    }
}
