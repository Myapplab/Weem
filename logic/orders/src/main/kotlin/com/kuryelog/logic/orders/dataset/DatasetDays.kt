package com.kuryelog.logic.orders.dataset

import java.time.ZoneId

/** Vardiyanın değdiği takvim günleri (`datasetDays`, MAP.md 13.5): gece yarısını geçen vardiya iki gün; gün sınırı [zone]'da. İskelet (A6.2b). */
object DatasetDays {
    fun days(startMs: Long, endMs: Long, zone: ZoneId): List<String> = TODO("A6.9")
}
