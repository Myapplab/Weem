package com.kuryelog.logic.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `I18nCoverageTest` karşılığı. Eski regresyon: Azərbaycanca seçiliyken arayüzde ~100 metin Türkçe kalmıştı.
 * Ekrana giden her metin `Text` girişi olmalı (iki dil birlikte); `:app` kaynağında düz metin olmamalı.
 */
class TextCoverageTest {
    @Test fun everyEntryHasBothLanguages() {
        for (t in Text.entries) {
            assertTrue("${t.name} tr boş", t.tr.isNotBlank())
            assertTrue("${t.name} az boş", t.az.isNotBlank())
        }
    }

    @Test fun placeholdersMatchBetweenLanguages() {
        val spec = Regex("%[sd]")
        for (t in Text.entries) {
            assertEquals("${t.name} biçim işaretleri", spec.findAll(t.tr).map { it.value }.toList(), spec.findAll(t.az).map { it.value }.toList())
        }
    }

    @Test fun azerbaijaniLettersAppearWhereTextIsTranslated() {
        // Çeviri unutulmuşsa (tr == az) yalnız bilinçli aynı olanlar geçer: marka/dil adları ve ortak kelimeler.
        val same = setOf("TAB_SCOOTER", "SETTINGS_TITLE", "SETTINGS_LANGUAGE", "SETTINGS_THEME", "LANG_TR", "LANG_AZ", "THEME_LIGHT")
        val identical = Text.entries.filter { it.tr == it.az }.map { it.name }.toSet()
        assertEquals(emptySet<String>(), identical - same)
    }

    private fun appSources(): List<File> {
        val root = listOf("../../app/src/main/kotlin", "app/src/main/kotlin").map(::File).firstOrNull { it.isDirectory }
            ?: error("app kaynak klasörü bulunamadı: ${File(".").absolutePath}")
        return root.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    @Test fun appSourcesHaveNoUntranslatedLiteralText() {
        val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val localLetters = Regex("[çğıöşüəÇĞİÖŞÜƏ]")
        val missing = mutableListOf<String>()
        for (file in appSources()) {
            file.readLines().forEachIndexed { i, line ->
                if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) return@forEachIndexed
                for (m in literal.findAll(line)) {
                    if (localLetters.containsMatchIn(m.groupValues[1])) missing += "${file.name}:${i + 1}: ${m.groupValues[1]}"
                }
            }
        }
        assertEquals(emptyList<String>(), missing)
    }
}
