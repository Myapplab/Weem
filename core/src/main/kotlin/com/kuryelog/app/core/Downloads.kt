package com.kuryelog.app.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

/**
 * İndirilenler'e ZIP yazma (R17; RS `MainActivity.saveAndShare` `:1612-1624`, `DailyPack.write` `:39-56`): satır `IS_PENDING = 1` ile açılır,
 * [write] akışı doldurup kapatır, sonra `IS_PENDING = 0`; yazma hatasında satır silinir (yarım dosya kalmaz). İzin gerekmez (API 29+,
 * yalnız uygulamanın kendi satırları). Ağ kodu yok.
 */
object Downloads {
    /** `Download/<folder>/` ([folder] örn. `WEEM`, `WEEM/gunluk`). */
    fun relative(folder: String) = Environment.DIRECTORY_DOWNLOADS + "/" + folder + "/"

    /** [replace] true ise aynı klasördeki aynı adlı önceki dosyamız önce silinir (günlük paket: günün paketi yenilenir). */
    fun save(ctx: Context, folder: String, name: String, replace: Boolean = false, write: (OutputStream) -> Unit): Uri {
        val resolver = ctx.contentResolver
        val relative = relative(folder)
        if (replace) resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf(name, relative))
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative); put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: error("insert")
        try {
            write(resolver.openOutputStream(uri) ?: error("open"))
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }
}
