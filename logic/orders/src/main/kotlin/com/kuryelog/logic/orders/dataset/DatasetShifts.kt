package com.kuryelog.logic.orders.dataset

import org.json.JSONObject
import java.time.ZoneId

/** Vardiya satırları (`shifts.jsonl`; MAP.md 13.5): vardiyanın değdiği her güne bir satır; açıkken bitiş null, kapanışta aynı başlangıçla yeni satır (özet son satırı alır). */
object DatasetShifts {
    const val FILE = "shifts.jsonl"

    /** `start > 0`, `end ≥ start` değilse null. */
    fun row(startMs: Long, endMs: Long?, wallMs: Long, monoNs: Long): JSONObject? {
        if (startMs <= 0 || (endMs != null && endMs < startMs)) return null
        return DatasetContract.common(wallMs, monoNs).put("shiftStartMs", startMs).put("shiftEndMs", endMs ?: JSONObject.NULL)
            .put("shiftStart_source", "yolgo_shift").put("shiftEnd_source", if (endMs == null) "open_shift" else "yolgo_shift")
    }

    /** Satırın yazılacağı günler: açıkta yalnız başlangıç günü, kapanışta vardiyanın değdiği bütün günler. */
    fun days(startMs: Long, endMs: Long?, zone: ZoneId): List<String> =
        if (endMs == null) listOf(DatasetContract.day(startMs, zone)) else DatasetDays.days(startMs, endMs, zone)
}
