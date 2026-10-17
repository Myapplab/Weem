package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** G3 (logic/app `TextCoverageTest` gibi): `:core` metinleri iki dilde; Android `:core` kaynağında çevrilmemiş düz metin yok. */
class CoreTextTest {
    @Test fun everyEntryHasBothLanguages() {
        for (t in CoreText.entries) {
            assertTrue("${t.name} tr", t.tr.isNotBlank())
            assertTrue("${t.name} az", t.az.isNotBlank())
            assertNotEquals(t.name, t.tr, t.az)
        }
        assertEquals("Qeyd yazılmadı: ", CoreText.WRITE_FAILED.get(Lang.AZ))
        assertEquals("Kayıt yazılamadı: ", CoreText.WRITE_FAILED.get(Lang.TR))
    }

    @Test fun androidCoreSourcesHaveNoUntranslatedLiteralText() {
        val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val localLetters = Regex("[çğıöşüəÇĞİÖŞÜƏ]")
        val missing = mutableListOf<String>()
        File(RepoSource.root, "core/src/main").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//").trimStart()
                if (code.startsWith("*")) return@forEachIndexed
                for (m in literal.findAll(code)) if (localLetters.containsMatchIn(m.groupValues[1])) missing += "${file.name}:${i + 1}: ${m.groupValues[1]}"
            }
        }
        assertEquals(emptyList<String>(), missing)
    }
}
