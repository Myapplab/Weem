package com.kuryelog.app.scooter

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import com.kuryelog.logic.scooter.LogExport
import org.json.JSONObject
import java.io.OutputStream

/**
 * Sürüş kaydı deposu `ninebot-logger.db` (SCOOTER.md 7.3, 11.2; NB `LogStore.java` satır satır): sürüm 1, WAL, `sessions` ve `events`.
 * Ad, şema ve durum değerleri değişmez (VERI_SOZLESMESI 7.2; Pixel'de Rawstats'ın üstüne kurulumda eski kayıtlar okunur).
 * Dışa aktarma ZIP'inin biçimi ve izin listesi saf [LogExport]'ta; burası yalnız satırları verir.
 */
class LogStore internal constructor(context: Context, database: String) : SQLiteOpenHelper(context, database, null, 1) {
    constructor(context: Context) : this(context, NAME)

    private var insertEvent: SQLiteStatement? = null

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=ON")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY, started_ms INTEGER NOT NULL, ended_ms INTEGER, state TEXT NOT NULL, device TEXT)")
        db.execSQL(
            "CREATE TABLE events(id INTEGER PRIMARY KEY, session_id INTEGER NOT NULL REFERENCES sessions(id), wall_ms INTEGER NOT NULL, " +
                "mono_ns INTEGER NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX event_session ON events(session_id,id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        throw IllegalStateException("Migration required")
    }

    /** Açık kalmış `recording` oturumları `interrupted` olur; yeni oturum `recording`. */
    fun start(device: String?): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val interrupted = ContentValues()
            interrupted.put("state", "interrupted")
            db.update("sessions", interrupted, "state=?", arrayOf("recording"))
            val v = ContentValues()
            v.put("started_ms", System.currentTimeMillis())
            v.put("state", "recording")
            v.put("device", device)
            val id = db.insertOrThrow("sessions", null, v)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun end(id: Long, state: String) {
        val v = ContentValues()
        v.put("ended_ms", System.currentTimeMillis())
        v.put("state", state)
        writableDatabase.update("sessions", v, "id=?", arrayOf(id.toString()))
    }

    fun event(id: Long, wall: Long, mono: Long, kind: String, data: JSONObject) {
        val st = insertEvent ?: writableDatabase.compileStatement("INSERT INTO events(session_id,wall_ms,mono_ns,kind,payload) VALUES(?,?,?,?,?)").also { insertEvent = it }
        st.bindLong(1, id)
        st.bindLong(2, wall)
        st.bindLong(3, mono)
        st.bindString(4, kind)
        st.bindString(5, data.toString())
        st.executeInsert()
    }

    @Synchronized
    override fun close() {
        insertEvent?.close()
        insertEvent = null
        super.close()
    }

    /** Yalnız tamamlanmış ve kullanıcının seçtiği tarihten eski oturumlar; kendiliğinden hiç çağrılmaz. */
    fun deleteCompletedBefore(cutoff: Long): Int {
        val db = writableDatabase
        val removed: Int
        db.beginTransaction()
        try {
            val where = "state<>? AND started_ms<?"
            val args = arrayOf("recording", cutoff.toString())
            db.delete("events", "session_id IN (SELECT id FROM sessions WHERE $where)", args)
            removed = db.delete("sessions", where, args)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // Called only from the idle maintenance UI; reclaim pages after explicitly confirmed deletion.
        db.execSQL("VACUUM")
        return removed
    }

    /** `NinebotLogger-<ms>.zip` içeriği ([LogExport.write]). */
    fun export(output: OutputStream, options: LogExport.Options = LogExport.Options(), nowMs: Long = System.currentTimeMillis()) {
        LogExport.write(output, Rows(readableDatabase), options, nowMs)
    }

    private class Rows(private val db: SQLiteDatabase) : LogExport.Source {
        override fun <T> consistent(block: () -> T): T {
            db.beginTransactionNonExclusive()
            try {
                val result = block()
                db.setTransactionSuccessful()
                return result
            } finally {
                db.endTransaction()
            }
        }

        override fun rows(table: String, latestOnly: Boolean, each: (Map<String, Any?>) -> Unit) {
            val where = if (latestOnly) " WHERE " + (if (table == "sessions") "id" else "session_id") + "=(SELECT MAX(id) FROM sessions)" else ""
            db.rawQuery("SELECT * FROM $table$where ORDER BY id", null).use { c ->
                while (c.moveToNext()) {
                    val row = LinkedHashMap<String, Any?>()
                    for (i in 0 until c.columnCount) {
                        row[c.getColumnName(i)] = if (c.isNull(i)) null else if (c.getType(i) == Cursor.FIELD_TYPE_INTEGER) c.getLong(i) else c.getString(i)
                    }
                    each(row)
                }
            }
        }
    }

    companion object {
        /** Veritabanı adı (VERI_SOZLESMESI 7.2; değişmez). */
        const val NAME = "ninebot-logger.db"
    }
}
