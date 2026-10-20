package com.kuryelog.logic.orders.dataset

import java.time.Instant
import java.time.ZoneId

/** Vardiyanın değdiği takvim günleri (`datasetDays`, MAP.md 13.5): gece yarısını geçen vardiya iki gün; gün sınırı [zone]'da. */
object DatasetDays {
    fun days(startMs: Long, endMs: Long, zone: ZoneId): List<String> {
        require(endMs >= startMs) { "Bitiş başlangıçtan önce" }
        val last = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate()
        var d = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
        val out = ArrayList<String>()
        while (!d.isAfter(last)) {
            out += d.toString()
            d = d.plusDays(1)
        }
        return out
    }
}
