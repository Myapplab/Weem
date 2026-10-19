package com.kuryelog.logic.map.graph

import java.io.File

/** `<hedef>.tmp`'ye yazıp yeniden adlandırır; hata olursa `.tmp` silinir, hedef dokunulmaz (`MAP.md` 2.8). İskelet (A6.2a). */
fun writeFileAtomically(target: File, bytes: ByteArray): Unit = TODO("A6.3")
