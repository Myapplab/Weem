package com.kuryelog.logic.map.graph

import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** YolGo `test/map_data_update_test.dart` test 2 (atomik yazma); `MAP.md` 2.13. */
class AtomicFileTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `atomik yazma eski dosyayı tamamlanana kadar korur ve geçici dosya bırakmaz`() {
        val dir = tmp.newFolder("weem-map")
        val target = File(dir, "graph_wide.bin")
        target.writeBytes(byteArrayOf(1, 2, 3))
        writeFileAtomically(target, byteArrayOf(9, 8, 7, 6))
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), target.readBytes())
        assertFalse(File(target.path + ".tmp").exists())

        // Hedef klasör yoksa yazma başarısız olur; mevcut dosyalara dokunulmaz.
        val missing = File(dir, "yok/graph_wide.bin")
        var failed = false
        try {
            writeFileAtomically(missing, byteArrayOf(1))
        } catch (_: IOException) {
            failed = true
        }
        org.junit.Assert.assertTrue(failed)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), target.readBytes())
    }
}
