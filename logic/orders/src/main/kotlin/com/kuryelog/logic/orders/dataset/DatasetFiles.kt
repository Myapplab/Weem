package com.kuryelog.logic.orders.dataset

import org.json.JSONObject
import java.io.File

/** Veri seti gün klasörü ve JSONL ekleme (saf; `files/TeklifVeriSeti/<gün>_<ad>/`, MAP.md 13.17). Çağıran tek kilit altında çalışır. */
object DatasetFiles {
    /** Günün klasörü (yoksa yaratılır). */
    fun day(root: File, day: String): File = File(root, DatasetContract.folder(day)).also { it.mkdirs() }

    /** [row]'u günün [name] dosyasına bir satır olarak ekler. */
    fun append(root: File, day: String, name: String, row: JSONObject) {
        File(day(root, day), name).appendText(row.toString() + "\n", Charsets.UTF_8)
    }
}
