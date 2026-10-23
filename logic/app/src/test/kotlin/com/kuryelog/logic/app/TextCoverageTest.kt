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
    /** Kabuk ve kurulum (G5, A7.2) tabloları. */
    private val all: List<Enum<*>> = Text.entries + SetupText.entries
    private val Enum<*>.tr: String get() = (this as com.kuryelog.logic.core.Bilingual).tr
    private val Enum<*>.az: String get() = (this as com.kuryelog.logic.core.Bilingual).az

    @Test fun everyEntryHasBothLanguages() {
        for (t in all) {
            assertTrue("${t.name} tr boş", t.tr.isNotBlank())
            assertTrue("${t.name} az boş", t.az.isNotBlank())
        }
    }

    @Test fun placeholdersMatchBetweenLanguages() {
        val spec = Regex("%[sd]")
        for (t in all) {
            assertEquals("${t.name} biçim işaretleri", spec.findAll(t.tr).map { it.value }.toList(), spec.findAll(t.az).map { it.value }.toList())
        }
    }

    @Test fun azerbaijaniLettersAppearWhereTextIsTranslated() {
        // Çeviri unutulmuşsa (tr == az) yalnız bilinçli aynı olanlar geçer: marka/dil adları ve ortak kelimeler.
        val same = setOf("TAB_SCOOTER", "SETTINGS_TITLE", "SETTINGS_LANGUAGE", "SETTINGS_THEME", "LANG_TR", "LANG_AZ", "THEME_LIGHT", "BACK", "HOME_DELETE",
            "STEP_LANGUAGE", "PREV", "GUIDE_XIAOMI_TITLE", "GUIDE_PIXEL_TITLE", "LATER", "SHIFT_START_SHORT")
        val identical = all.filter { it.tr == it.az }.map { it.name }.toSet()
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
                // Yorum satırları (`//`, KDoc `/**` açılışı ve `*` gövdesi) ekrana gitmez (A4.3: A5.3'ün tek satırlık KDoc'u testi düşürüyordu).
                if (line.trimStart().let { it.startsWith("//") || it.startsWith("/*") || it.startsWith("*") }) return@forEachIndexed
                for (m in literal.findAll(line)) {
                    if (localLetters.containsMatchIn(m.groupValues[1])) missing += "${file.name}:${i + 1}: ${m.groupValues[1]}"
                }
            }
        }
        assertEquals(emptyList<String>(), missing)
    }
}
