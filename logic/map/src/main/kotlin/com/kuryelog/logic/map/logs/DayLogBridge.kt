package com.kuryelog.logic.map.logs

import com.kuryelog.logic.map.day.DayStep
import org.json.JSONObject

/**
 * Gün motoru adımlarını sürüş günlüğüne yazar (Dart `ShiftController._apply`, MAP.md 12.2, 11.14): gün açıkken `setDay(dayId)`, her olay `shift_<tür>` (gün kimliğiyle),
 * `light_answer` ve `stop_surface_override` öneksiz; `stop` olayının duruş kaydı düz alanlara açılır; gün kapanınca olaylardan sonra `setDay(null)`.
 */
class DayLogBridge(private val writer: RideLogWriter) {
    fun onStep(step: DayStep) {
        val s = step.view.state
        if (s.active) s.startedAtMs?.let { writer.setDay("yolgo-$it") }
        for (e in step.events) {
            val name = if (e.type in UNPREFIXED) e.type else "shift_${e.type}"
            writer.event(name, flatten(e.type, e.data))
        }
        if (!s.active) writer.setDay(null)
    }

    private fun flatten(type: String, data: Map<String, Any?>): Map<String, Any?> {
        val stop = data["stop"]
        if (type != "stop" || stop !is JSONObject) return data
        return stop.keys().asSequence().associateWith { stop.opt(it) }
    }

    companion object {
        private val UNPREFIXED = setOf("light_answer", "stop_surface_override")
    }
}
