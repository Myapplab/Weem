package com.kuryelog.logic.core

/**
 * `databases/courier.db` şeması (CORE.md 5.5, RS `CourierDb.kt:10-19`). Ad, sürüm ve DDL Rawstats ile birebir: Rawstats'ın
 * üstüne kurulan WEEM aynı veritabanını açar. Yeni tablo gerekirse sürüm 2 ve gerçek göç yazılır (boş `onUpgrade` kalmaz).
 */
object CourierSchema {
    const val NAME = "courier.db"
    const val VERSION = 1

    /** `onCreate` sırasıyla. */
    val CREATE: List<String> = listOf(
        "CREATE TABLE trips(id TEXT PRIMARY KEY,pkg TEXT NOT NULL,day TEXT NOT NULL,payload TEXT NOT NULL)",
        "CREATE INDEX trips_day ON trips(day)",
        "CREATE TABLE offers(id TEXT PRIMARY KEY,pkg TEXT NOT NULL,day TEXT NOT NULL,result TEXT,payload TEXT NOT NULL)",
        "CREATE INDEX offers_day ON offers(day)",
        "CREATE TABLE session(k TEXT PRIMARY KEY,payload TEXT NOT NULL)",
    )
    val TABLES: List<String> = listOf("trips", "offers", "session")
    /** `session` tablosunun tek satırı. */
    const val SESSION_KEY = "active"
    const val OFFER_COUNT_SQL = "SELECT COUNT(*) FROM offers WHERE day=? AND result=?"
    const val SESSION_SQL = "SELECT payload FROM session WHERE k='active'"
}
