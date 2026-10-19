package com.kuryelog.app

import android.content.Context
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.app.orders.Orders
import com.kuryelog.logic.core.OrderEvents
import com.kuryelog.logic.core.TripStages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * `OrderEvents.stages` (plan 4.3) tek telefonda: `:orders` akışının aşama görüntüsünü ([Orders.flow] `stages()`) `StateFlow` yapar; `:map` gün motoru izler (MAP.md 11.7, 11.14).
 * Vardiya sürerken 1 sn'de bir, kapalıyken 5 sn'de bir okunur; yalnız değişim yayılır. [start] ilk görüntüyü eşzamanlı okur: `Orders.bind`'dan sonra çağrılır, böylece süreç
 * başındaki boş `TripStages` eksik okuma sayılmaz. Teklif ve gün özeti bu köprüde yok (`:map` kullanmaz).
 */
object OrderStagesBridge : OrderEvents {
    private val flow = MutableStateFlow(TripStages.EMPTY)
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val stages: StateFlow<TripStages> = flow.asStateFlow()
    override val offers = OrderEvents.NONE.offers
    override val day = OrderEvents.NONE.day

    fun start(context: Context) {
        val ctx = context.applicationContext
        synchronized(this) { if (started) return; started = true }
        flow.value = Orders.flow(ctx).stages()
        scope.launch {
            while (true) {
                delay(if (ShiftHub.state.value.active) 1_000L else 5_000L)
                runCatching { Orders.flow(ctx).stages() }.getOrNull()?.let { if (it != flow.value) flow.value = it }
            }
        }
    }
}
