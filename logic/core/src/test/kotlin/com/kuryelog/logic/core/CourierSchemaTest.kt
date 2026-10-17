package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/** CORE.md 5.5: Rawstats'ın üstüne kurulan WEEM aynı `courier.db`'yi açar; ad, sürüm ve DDL birebir (RS `CourierDb.kt:10-19`). */
class CourierSchemaTest {
    @Test fun nameVersionAndDdlAreRawstatsExactly() {
        assertEquals("courier.db", CourierSchema.NAME)
        assertEquals(1, CourierSchema.VERSION)
        assertEquals(listOf(
            "CREATE TABLE trips(id TEXT PRIMARY KEY,pkg TEXT NOT NULL,day TEXT NOT NULL,payload TEXT NOT NULL)",
            "CREATE INDEX trips_day ON trips(day)",
            "CREATE TABLE offers(id TEXT PRIMARY KEY,pkg TEXT NOT NULL,day TEXT NOT NULL,result TEXT,payload TEXT NOT NULL)",
            "CREATE INDEX offers_day ON offers(day)",
            "CREATE TABLE session(k TEXT PRIMARY KEY,payload TEXT NOT NULL)",
        ), CourierSchema.CREATE)
        assertEquals(listOf("trips", "offers", "session"), CourierSchema.TABLES)
        assertEquals("SELECT COUNT(*) FROM offers WHERE day=? AND result=?", CourierSchema.OFFER_COUNT_SQL)
        assertEquals("SELECT payload FROM session WHERE k='active'", CourierSchema.SESSION_SQL)
        assertTrue(CourierSchema.SESSION_SQL.contains("'${CourierSchema.SESSION_KEY}'"))
    }
}
