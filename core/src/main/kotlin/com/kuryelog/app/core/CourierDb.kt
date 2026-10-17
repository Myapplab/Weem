package com.kuryelog.app.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.kuryelog.logic.core.CourierSchema
import org.json.JSONObject

/**
 * Yapılandırılmış yolculuk/teklif kayıtları (CORE.md 5.5, RS `CourierDb.kt`). Ad, sürüm ve DDL [CourierSchema]'dan: Rawstats'ın
 * üstüne kurulunca aynı veritabanı açılır. Satırları `:orders` yazar (A3). Her işlem tek kilitte.
 */
class CourierDb(ctx: Context) : SQLiteOpenHelper(ctx, CourierSchema.NAME, null, CourierSchema.VERSION) {
    override fun onCreate(db: SQLiteDatabase) { CourierSchema.CREATE.forEach(db::execSQL) }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Synchronized fun saveTrip(j: JSONObject) { writableDatabase.insertWithOnConflict("trips", null, ContentValues().apply {
        put("id", j.getString("id")); put("pkg", j.getString("pkg")); put("day", j.getString("day")); put("payload", j.toString())
    }, SQLiteDatabase.CONFLICT_REPLACE) }
    @Synchronized fun saveOffer(j: JSONObject, result: String? = null) { writableDatabase.insertWithOnConflict("offers", null, ContentValues().apply {
        put("id", j.getString("id")); put("pkg", j.getString("pkg")); put("day", j.getString("day")); put("result", result); put("payload", j.toString())
    }, SQLiteDatabase.CONFLICT_REPLACE) }
    @Synchronized fun trips(day: String? = null): List<JSONObject> = readableDatabase.query("trips", arrayOf("payload"), if (day != null) "day=?" else null,
        day?.let { arrayOf(it) }, null, null, "id").use { c -> buildList { while (c.moveToNext()) add(JSONObject(c.getString(0))) } }
    @Synchronized fun offers(day: String): List<Pair<JSONObject, String?>> = readableDatabase.query("offers", arrayOf("payload", "result"), "day=?", arrayOf(day), null, null, "id").use { c ->
        buildList { while (c.moveToNext()) add(JSONObject(c.getString(0)) to c.getString(1)) }
    }
    @Synchronized fun offer(id: String): JSONObject? = readableDatabase.query("offers", arrayOf("payload"), "id=?", arrayOf(id), null, null, null).use { c ->
        if (c.moveToFirst()) JSONObject(c.getString(0)) else null
    }
    /** SQL'de sayılır: rozet 500 ms'de bir sorar; yükler ayrıştırılmaz. */
    @Synchronized fun offerCount(day: String, result: String): Int =
        readableDatabase.rawQuery(CourierSchema.OFFER_COUNT_SQL, arrayOf(day, result)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    @Synchronized fun session(): JSONObject? = readableDatabase.rawQuery(CourierSchema.SESSION_SQL, null).use { c -> if (c.moveToFirst()) JSONObject(c.getString(0)) else null }
    @Synchronized fun session(j: JSONObject) { writableDatabase.insertWithOnConflict("session", null, ContentValues().apply {
        put("k", CourierSchema.SESSION_KEY); put("payload", j.toString())
    }, SQLiteDatabase.CONFLICT_REPLACE) }
    /** [block] tek işlemde: çok satır tek günlük kaydı paylaşır. */
    @Synchronized fun <T> batch(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try { return block().also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
    @Synchronized fun clear() {
        val db = writableDatabase
        db.beginTransaction()
        try { CourierSchema.TABLES.forEach { db.delete(it, null, null) }; db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
}
