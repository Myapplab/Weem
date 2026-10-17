package com.kuryelog.logic.capture

import com.kuryelog.logic.core.Lang
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** G3, CAPTURE.md 7.2 (logic/core `CoreTextTest` gibi): `:capture` metinleri iki dilde; Android `:capture` kaynağında düz metin yok. */
class CaptureTextTest {
    @Test fun everyEntryHasBothLanguages() {
        for (t in CaptureText.entries) {
            assertTrue("${t.name} tr", t.tr.isNotBlank())
            assertTrue("${t.name} az", t.az.isNotBlank())
            assertNotEquals(t.name, t.tr, t.az)
        }
        assertEquals("Ekran oxuma bağlıdır — qeyd aparılmır", CaptureText.SCREEN_READING_OFF_TITLE.get(Lang.AZ))
    }

    @Test fun placeholdersMatchBetweenLanguages() {
        val spec = Regex("%[sd]")
        for (t in CaptureText.entries) assertEquals(t.name, spec.findAll(t.tr).map { it.value }.toList(), spec.findAll(t.az).map { it.value }.toList())
        assertEquals("OCR ekranı okunamadı (kod 5); erişilebilirlik okumaları sürüyor.", CaptureText.OCR_UNAVAILABLE.tr.format(5))
    }

    @Test fun androidCaptureSourcesHaveNoUntranslatedLiteralText() {
        val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val localLetters = Regex("[çğıöşüəÇĞİÖŞÜƏ]")
        val missing = mutableListOf<String>()
        File(RepoSource.root, "capture/src/main").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//").trimStart()
                if (code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                for (m in literal.findAll(code)) if (localLetters.containsMatchIn(m.groupValues[1])) missing += "${file.name}:${i + 1}: ${m.groupValues[1]}"
            }
        }
        assertEquals(emptyList<String>(), missing)
    }
}
