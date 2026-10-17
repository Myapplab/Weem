package com.kuryelog.logic.capture

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * 16 Oct ("Rawstats opens and closes at once", 0.25.0): `NavDistance` built `Regex("(?U)" + …)`. The JVM reads `(?U)`, but
 * Android hands every pattern to ICU, which has no such flag: on the phone the object failed to load, and the first
 * notification (NotifService reads `NavDistance.packages` for each one) closed the app. JVM tests cannot compile a pattern
 * with ICU, so the constructs Android rejects are kept out of the source here.
 * (16 Oct check: every pattern the whole test suite compiles, 202, went through ICU 74; this one was the only failure.)
 */
class IcuRegexTest {
    /** WEEM: every module's main sources (logic/<modül>/src/main, Android modules, app); tests and build output excluded (CAPTURE.md 7.5). */
    private fun sources(): List<File> {
        val files = RepoSource.mainKotlin()
        assertTrue(files.map { it.invariantSeparatorsPath.substringBefore("/src/main/") }.distinct().size >= 2)
        return files
    }
    /** Code lines only: comment lines may name the constructs. */
    private fun code(f: File) = f.readLines().filterNot { val t = it.trimStart(); t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }
        .joinToString("\n")

    @Test fun noRegexConstructAndroidRejects() {
        val files = sources()
        assertTrue(files.any { it.name == "NavDistance.kt" })
        // (?U) and its UNICODE_CHARACTER_CLASS flag, CANON_EQ (Android's Pattern throws "Unsupported flags"), Java-only classes.
        val inlineU = Regex("""\(\?[idmsuxw-]*U[idmsuxwU-]*[):]""")
        val bad = files.flatMap { f ->
            val c = code(f)
            listOfNotNull(
                inlineU.find(c)?.let { "${f.name}: inline flag ${it.value}" },
                "UNICODE_CHARACTER_CLASS".takeIf { c.contains(it) }?.let { "${f.name}: $it" },
                "CANON_EQ".takeIf { c.contains(it) }?.let { "${f.name}: $it" },
                Regex("""\\\\p\{java""").find(c)?.let { "${f.name}: \\p{java…}" })
        }
        assertEquals(emptyList<String>(), bad)
    }

    @Test fun navigationDistanceStillReadsCyrillicAndKeepsWordEdges() {
        assertEquals(1200.0, NavDistance.metres("3 мин · 1,2 км")!!, 1e-9)
        assertEquals(450.0, NavDistance.metres("12 мин · 450 м")!!, 1e-9)
        assertEquals(2100.0, NavDistance.metres("8 min · 2,1 km")!!, 1e-9)
        assertEquals(300.0, NavDistance.metres("1 saat 5 dk · 300 m")!!, 1e-9)
        // A unit glued to a word is no duration ("5 minutos" in Spanish, "x3 min" inside a code).
        assertNull(NavDistance.metres("5 minutos · 2 km"))
        assertNull(NavDistance.metres("x3 min · 2 km"))
        assertNull(NavDistance.metres("Turn left in 300 m"))
    }
}
