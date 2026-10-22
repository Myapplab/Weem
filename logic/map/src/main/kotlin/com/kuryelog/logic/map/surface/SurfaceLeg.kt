package com.kuryelog.logic.map.surface

import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.OrderPhase
import com.kuryelog.logic.map.nav.Stop
import com.kuryelog.logic.map.nav.StopPurpose

/**
 * Yüzey ölçümünün "gerçek etabı" (YolGo `SurfaceController._actualLeg`): üç bağımsız durumun birleşimi — gerçek navigasyon etabı, sipariş aşaması
 * ve hareket. Gün kapalıysa null (ölçüm yok). 1.7.2 (kullanıcı: her geçiş ölçülsün): rotasız, siparişsiz ya da başka uygulamanın navigasyonuyla
 * sürüş de ölçülür; ayrı "serbest" etapta tutulur ki gecikmeli paketler bir siparişin etabına yazılmasın. Yürüme/araç [MotionGate]'te elenir.
 */
object SurfaceLeg {
    fun actual(day: DayState, activeStop: Stop?, hasRoute: Boolean): String? {
        if (!day.active) return null
        val dayId = "yolgo-${day.startedAtMs}"
        val free = "$dayId:free"
        val current = day.current
        if (current == null || activeStop == null || !hasRoute || activeStop.purpose != StopPurpose.ORDER || activeStop.orderId != current.n) return free
        val phase = day.phase
        if (phase != OrderPhase.TO_RESTAURANT && phase != OrderPhase.TO_CUSTOMER) return free
        val restaurant = phase == OrderPhase.TO_RESTAURANT
        if (activeStop.restaurant != restaurant) return free
        val target = (if (restaurant) current.restTarget else current.customerTarget) ?: return free
        if (activeStop.point.distanceTo(target) > 10) return free
        val start = if (restaurant) current.startedAtMs else current.customerDepartedAtMs
        return "$dayId:${current.n}:${phase.jsonName}:$start"
    }
}
