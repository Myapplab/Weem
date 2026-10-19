package com.kuryelog.logic.map.graph

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

/**
 * YolGo `map_data_update_test.dart` "atomik yazma eski dosyayı tamamlanana kadar korur ve geçici dosya bırakmaz" (A6.2a,
 * MAP.md 2.13). Saf `java.io.File`; uygulama: A6.3.
 */
class AtomicFileTest {
    @Test
    fun `atomik yazma eski dosyayı tamamlanana kadar korur ve geçici dosya bırakmaz`() {
        val dir = Files.createTempDirectory("yolgo-map-").toFile()
        try {
            val target = File(dir, "graph_wide.bin")
            target.writeBytes(byteArrayOf(1, 2, 3))
            AtomicFile.write(target, byteArrayOf(9, 8, 7, 6))
            assertArrayEquals(byteArrayOf(9, 8, 7, 6), target.readBytes())
            assertFalse(File(target.path + ".tmp").exists())

            // Hedef klasör yoksa yazma başarısız olur; mevcut dosyalara dokunulmaz.
            val missing = File(dir, "yok/graph_wide.bin")
            try {
                AtomicFile.write(missing, byteArrayOf(1))
                fail("IOException bekleniyordu")
            } catch (_: IOException) {
            }
            assertArrayEquals(byteArrayOf(9, 8, 7, 6), target.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }
}
