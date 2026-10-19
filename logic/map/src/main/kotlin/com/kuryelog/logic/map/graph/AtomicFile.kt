package com.kuryelog.logic.map.graph

import java.io.File

/** `writeFileAtomically` (MAP.md 2.9): önce `<hedef>.tmp`, sonra yeniden adlandırma; hata olursa eski dosyaya dokunulmaz. */
object AtomicFile {
    @Throws(java.io.IOException::class)
    fun write(target: File, bytes: ByteArray): Unit = TODO("A6.3")
}
