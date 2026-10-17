package com.kuryelog.logic.capture

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * Plan 4.2, 5; CAPTURE.md 7.3: screen reading never touches the network. `:capture` (pure `logic/capture` and Android `capture`)
 * names no network class in code, its Android dependencies are an allow-list, and ML Kit's usage telemetry has no backend.
 * Whether OCR still works on the phone with the backend unregistered: A3.7 (telefonda denenmedi).
 */
class NoNetworkTest {
    private fun sources(): List<File> = listOf("logic/capture/src/main", "capture/src/main").map { File(RepoSource.root, it) }
        .flatMap { d -> d.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java" || it.extension == "kts") }.toList() }
    private fun code(f: File) = f.readLines().filterNot { val t = it.trimStart(); t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }

    @Test fun noNetworkClassInCaptureCode() {
        val files = sources()
        assertTrue(files.any { it.name == "CourierParser.kt" } && files.any { it.name == "WatchService.kt" })
        val banned = Regex("""\bjava\.net\.|\bjavax\.net\.|\bokhttp3?\b|\bandroid\.net\.http\b|\bio\.ktor\b|\bretrofit2?\b|\bWebView\b|""" +
            """\bHttpURLConnection\b|\bHttpsURLConnection\b|\bURLConnection\b|\bSocket\b|\bDatagramSocket\b|\bInetAddress\b|\bURL\(|\bCronet""")
        val bad = files.flatMap { f -> code(f).withIndex().filter { banned.containsMatchIn(it.value) }.map { "${f.name}:${it.index + 1}: ${it.value.trim()}" } }
        assertEquals(emptyList<String>(), bad)
    }

    /** The Android module's own dependencies: the logic modules and ML Kit text recognition, nothing else. A new one must be
     *  added here on purpose (and checked for network use). */
    @Test fun androidCaptureDependenciesAreAllowListed() {
        val gradle = File(RepoSource.root, "capture/build.gradle.kts").readLines().map { it.trim() }
            .filter { !it.startsWith("//") && Regex("""^(implementation|api|runtimeOnly|compileOnly)\(""").containsMatchIn(it) }
        assertEquals(listOf("implementation(libs.mlkit.text.recognition)"), gradle)
        val toml = File(RepoSource.root, "gradle/libs.versions.toml").readText()
        assertTrue(toml.contains("mlkit-text-recognition = { module = \"com.google.mlkit:text-recognition\""))
        assertTrue(toml.contains("mlkit-text-recognition = \"16.0.1\""))
        // The transitive allow-list (checked by `:capture:verifyRuntimeAllowlist` before every Android build) holds no HTTP stack.
        val allow = File(RepoSource.root, "capture/runtime-allowlist.txt").readLines().filterNot { it.startsWith("#") || it.isBlank() }
        assertTrue(allow.contains("com.google.mlkit:text-recognition") && allow.contains("com.kuryelog.logic:capture"))
        assertEquals(emptyList<String>(), allow.filter { Regex("okhttp|okio|grpc|cronet|ktor|retrofit|volley|firebase-messaging|play-services-measurement").containsMatchIn(it) })
        assertTrue(File(RepoSource.root, "capture/build.gradle.kts").readText().contains("tasks.named(\"preBuild\") { dependsOn(verifyRuntimeAllowlist) }"))
    }

    /** S17: ML Kit hands usage logs to Google's data transport; its only sending backend (CCT) is unregistered in the merged
     *  manifest, so the transport finds no backend and deletes the events unsent. The classes stay: ML Kit reads
     *  `CCTDestination` directly (mlkit common `zzsp`, vision-common `zzmp`), so excluding the library would break OCR. */
    @Test fun mlKitTelemetryHasNoBackend() {
        val m = File(RepoSource.root, "capture/src/main/AndroidManifest.xml").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        val service = Regex("""<service\s+android:name="com\.google\.android\.datatransport\.runtime\.backends\.TransportBackendDiscovery"[^>]*>(.*?)</service>""",
            RegexOption.DOT_MATCHES_ALL).find(m)
        assertNotNull(service)
        assertTrue(Regex("""<meta-data\s+android:name="backend:com\.google\.android\.datatransport\.cct\.CctBackendFactory"\s+tools:node="remove"\s*/>""")
            .containsMatchIn(service!!.groupValues[1]))
        assertFalse(File(RepoSource.root, "capture/build.gradle.kts").readText().contains("transport-backend-cct\""))
    }
}
