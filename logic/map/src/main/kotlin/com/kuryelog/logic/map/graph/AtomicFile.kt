package com.kuryelog.logic.map.graph

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** `writeFileAtomically` (MAP.md 2.9): önce `<hedef>.tmp`, sonra yeniden adlandırma; hata olursa eski dosyaya dokunulmaz. */
object AtomicFile {
    @Throws(IOException::class)
    fun write(target: File, bytes: ByteArray) {
        val temp = File(target.path + ".tmp")
        try {
            FileOutputStream(temp).use {
                it.write(bytes)
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
