package com.kuryelog.logic.map.logs

import java.io.File
import java.io.FileOutputStream
import java.io.Reader
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

/** İçe aktarma sonucu (A7.1): eklenen adlar (artan), var olduğu ya da etkin olduğu için atlanan sayısı, geçersiz öğe sayısı. */
class JournalImportResult(val added: List<String>, val existing: Int, val invalid: Int) {
    /** Eklenen kayıtların günleri (artan). */
    val days: List<String> get() = added.map { it.substring(0, 10) }.distinct().sorted()
}

/** Dosya `duzyol-journal-v1` değil, bozuk ya da yarım: klasöre hiçbir şey yazılmadı. */
class JournalFormatException(message: String) : Exception(message)

/**
 * YolGo kayıtlarını içe aktarma (A7.1, MAP.md 12.15, 12.17): `RideLogStore.export` biçimi `{"format": "duzyol-journal-v1", "day", "files": [{name, day, kind, content}]}`.
 * Dosya akışla okunur ("tüm kayıtlar" dosyası çok büyük olabilir; her `content` doğrudan diske yazılır). Önce [dir] içinde geçici klasöre açılır;
 * biçim doğru ve belge sonuna kadar okunduysa her kayıt ham metniyle aynı adla [dir]'e taşınır. Ad kalıba uymayan, `content`'i metin olmayan öğe geçersizdir;
 * aynı adlı dosya varsa ya da [skip] (etkin kayıt) doğruysa atlanır, var olan dosya ezilmez. `kind`/`day` alanları okunmaz (ad belirler).
 * Aynı anda tek içe aktarma (çağıran sıralar).
 */
object JournalImport {
    const val FORMAT = "duzyol-journal-v1"
    private const val STAGING = ".import-"

    fun import(input: Reader, dir: File, skip: (String) -> Boolean = { false }): JournalImportResult {
        dir.mkdirs()
        dir.listFiles()?.filter { it.isDirectory && it.name.startsWith(STAGING) }?.forEach { it.deleteRecursively() }
        val staging = File(dir, STAGING + System.nanoTime())
        check(staging.mkdirs()) { "Staging directory could not be created" }
        try {
            val staged = LinkedHashSet<String>()
            var duplicate = 0
            var invalid = 0
            var format: String? = null
            val json = JsonPull(input.buffered())
            json.obj { key ->
                when (key) {
                    "format" -> format = json.smallStringOrSkip(64)
                    "files" -> {
                        if (json.peek() != '[') throw JournalFormatException("files is not an array")
                        var index = 0
                        json.array {
                            when (val name = stageFile(json, staging, index++)) {
                                null -> invalid++
                                in staged -> duplicate++
                                else -> staged += name
                            }
                        }
                    }
                    else -> json.skipValue()
                }
            }
            if (format != FORMAT) throw JournalFormatException("Not a $FORMAT file")
            val added = ArrayList<String>()
            var existing = duplicate
            for (name in staged.sorted()) {
                val target = File(dir, name)
                if (skip(name) || Files.exists(target.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    existing++
                    continue
                }
                try {
                    Files.move(File(staging, name).toPath(), target.toPath())
                    added += name
                } catch (_: FileAlreadyExistsException) {
                    existing++
                }
            }
            return JournalImportResult(added, existing, invalid)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Bir `files[]` öğesi: geçerliyse geçici klasörde adıyla durur ve ad döner; değilse null (öğe yine sonuna kadar okunur). */
    private fun stageFile(json: JsonPull, staging: File, index: Int): String? {
        if (json.peek() != '{') {
            json.skipValue()
            return null
        }
        val part = File(staging, "$index.part")
        var name: String? = null
        var content = false
        var bad = false
        json.obj { key ->
            when (key) {
                "name" -> json.smallStringOrSkip(128).let { if (it == null) bad = true else name = it }
                "content" -> when {
                    json.peek() != '"' -> { json.skipValue(); bad = true }
                    content -> json.skipValue()
                    else -> {
                        FileOutputStream(part).bufferedWriter(Charsets.UTF_8).use { json.string(it, Long.MAX_VALUE) }
                        content = true
                    }
                }
                else -> json.skipValue()
            }
        }
        val n = name
        if (bad || !content || n == null || !RideLogStore.isLogName(n)) {
            part.delete()
            return null
        }
        if (File(staging, n).exists()) {
            part.delete()
            return n
        }
        if (!part.renameTo(File(staging, n))) throw java.io.IOException("Staged file could not be renamed")
        return n
    }
}

/** En küçük akış JSON okuyucu (yalnız içe aktarma için): nesne/dizi gezme, metni bir [Appendable]'a çözme, değer atlama. Söz dizimi hatası ve erken bitiş [JournalFormatException]. */
internal class JsonPull(private val input: Reader) {
    private var next = -2

    private fun read(): Int {
        if (next != -2) return next.also { next = -2 }
        return input.read()
    }

    private fun readChar(): Char {
        val c = read()
        if (c < 0) throw JournalFormatException("Unexpected end of file")
        return c.toChar()
    }

    private fun clean(): Char {
        while (true) {
            val c = readChar()
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') return c
        }
    }

    /** Boşluktan sonraki karakter (tüketmez). */
    fun peek(): Char = clean().also { next = it.code }

    private fun expect(c: Char) {
        val got = clean()
        if (got != c) throw JournalFormatException("Expected '$c', got '$got'")
    }

    /** `{ … }`: her anahtar için [onKey]; [onKey] değeri sonuna kadar okumalı. */
    fun obj(onKey: (String) -> Unit) {
        expect('{')
        if (peek() == '}') { clean(); return }
        while (true) {
            if (peek() != '"') throw JournalFormatException("Expected key")
            val key = StringBuilder()
            string(key, 1024)
            expect(':')
            onKey(key.toString())
            when (val c = clean()) {
                ',' -> continue
                '}' -> return
                else -> throw JournalFormatException("Expected ',' or '}', got '$c'")
            }
        }
    }

    /** `[ … ]`: her öğe için [onItem]; [onItem] öğeyi sonuna kadar okumalı. */
    fun array(onItem: () -> Unit) {
        expect('[')
        if (peek() == ']') { clean(); return }
        while (true) {
            onItem()
            when (val c = clean()) {
                ',' -> continue
                ']' -> return
                else -> throw JournalFormatException("Expected ',' or ']', got '$c'")
            }
        }
    }

    /** Metin değeri en çok [limit] karakterse döner; metin değil ya da uzunsa değer atlanır, null. */
    fun smallStringOrSkip(limit: Long): String? {
        if (peek() != '"') {
            skipValue()
            return null
        }
        val sb = StringBuilder()
        return if (string(sb, limit)) sb.toString() else null
    }

    /** Metni çözüp [out]'a yazar (en çok [limit] karakter; fazlası okunur, yazılmaz). Sığdıysa true. */
    fun string(out: Appendable, limit: Long): Boolean {
        expect('"')
        var count = 0L
        fun put(c: Char) {
            if (count++ < limit) out.append(c)
        }
        while (true) {
            when (val c = readChar()) {
                '"' -> return count <= limit
                '\\' -> when (val e = readChar()) {
                    '"', '\\', '/' -> put(e)
                    'b' -> put('\b')
                    'f' -> put('\u000C')
                    'n' -> put('\n')
                    'r' -> put('\r')
                    't' -> put('\t')
                    'u' -> {
                        var v = 0
                        repeat(4) { v = v * 16 + (Character.digit(readChar(), 16).takeIf { it >= 0 } ?: throw JournalFormatException("Bad \\u escape")) }
                        put(v.toChar())
                    }
                    else -> throw JournalFormatException("Bad escape \\$e")
                }
                else -> put(c)
            }
        }
    }

    /** Herhangi bir değeri okuyup atar. */
    fun skipValue() {
        when (val c = peek()) {
            '{' -> obj { skipValue() }
            '[' -> array { skipValue() }
            '"' -> string(NULL_SINK, 0)
            else -> {
                if (c != '-' && !c.isLetterOrDigit()) throw JournalFormatException("Unexpected '$c'")
                clean()
                while (true) {
                    val d = read()
                    if (d < 0) return
                    val ch = d.toChar()
                    if (ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '+') continue
                    next = d
                    return
                }
            }
        }
    }

    private companion object {
        val NULL_SINK = object : Appendable {
            override fun append(csq: CharSequence?) = this
            override fun append(csq: CharSequence?, start: Int, end: Int) = this
            override fun append(c: Char) = this
        }
    }
}
