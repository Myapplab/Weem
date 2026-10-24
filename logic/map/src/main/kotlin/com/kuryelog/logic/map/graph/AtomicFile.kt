package com.kuryelog.logic.map.graph

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** `writeFileAtomically` (MAP.md 2.9): önce `<hedef>.tmp`, sonra yeniden adlandırma; hata olursa eski dosyaya dokunulmaz. */
object AtomicFile {
    @Throws(IOException::class)
    fun write(target: File, bytes: ByteArray) = writeWith(target) { it.write(bytes) }

    /**
     * Akıştan yazar (S39): büyük gömülü dosyalar (graf, karolar) belleğe bütün alınmadan sabit tamponla kopyalanır. Akışı kapatmak çağıranın işi.
     */
    @Throws(IOException::class)
    fun write(target: File, input: InputStream) = writeWith(target) { input.copyTo(it, BUFFER) }

    private const val BUFFER = 64 * 1024

    private inline fun writeWith(target: File, body: (FileOutputStream) -> Unit) {
        val temp = File(target.path + ".tmp")
        try {
            FileOutputStream(temp).use {
                body(it)
                it.fd.sync()
            }
            if (!temp.renameTo(target)) {
                // Bazı dosya sistemlerinde hedef varken renameTo başarısız olur: atomik taşıma ile değiştir.
                java.nio.file.Files.move(
                    temp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            }
        } catch (e: Exception) {
            runCatching { if (temp.exists()) temp.delete() }
            throw if (e is IOException) e else IOException(e)
        }
    }
}
