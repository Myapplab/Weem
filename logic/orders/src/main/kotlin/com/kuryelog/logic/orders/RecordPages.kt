package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats RecordPages.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/** UI page order: newest first, so a left swipe advances to an older recorded day. */
internal object RecordPages {
    fun dates(orders: List<String>, logs: List<String>) = (orders + logs).distinct().sortedDescending()
    fun index(dates: List<String>, selected: String?, today: String): Int =
        dates.indexOf(selected).takeIf { it >= 0 } ?: dates.indexOf(today).takeIf { it >= 0 } ?: if (dates.isEmpty()) -1 else 0
}
