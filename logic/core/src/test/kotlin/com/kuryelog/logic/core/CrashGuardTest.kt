package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Rawstats `CrashGuardTest` (16 Eki: "uygulama açılır açılmaz kapanıyor", telefonda neden yazmıyordu) — A2.3'ün iki testi WEEM kaynağını
 * tarar (CORE.md 6.1): `everyProcessStartInstallsTheCrashRecorderFirst`, `pagesAndTapsDoNotCloseTheApp`. Vardiya servisininkiler A2.4'te,
 * ekran okumanınkiler A3'te. `guarded`'ın kendisi `GuardTest`'te.
 */
class CrashGuardTest {
    private val app = "app/src/main/kotlin/com/kuryelog/app/"
    private val core = "core/src/main/kotlin/com/kuryelog/app/core/"

    @Test fun everyProcessStartInstallsTheCrashRecorderFirst() {
        assertTrue(RepoSource.text("app/src/main/AndroidManifest.xml").contains("android:name=\".WeemApplication\""))
        val application = RepoSource.text(app + "WeemApplication.kt")
        // attachBaseContext servislerden ve Activity'den önce çalışır.
        assertTrue(application.contains("override fun attachBaseContext(base: Context) {\n        super.attachBaseContext(base)\n        CrashLog.install(this)"))
        val log = RepoSource.text(core + "CrashLog.kt")
        // Android'in kendi işleyicisi ardından uygulamayı yine kapatır (yakalanmamış istisnadan sonra süreç durumuna güvenilmez).
        assertTrue(log.contains("if (previous != null) previous.uncaughtException(thread, e)"))
        assertTrue(log.indexOf("runCatching { CrashFile(app.filesDir).save(") in 0 until log.indexOf("previous.uncaughtException"))
        // Çöken iş parçacığı Store'un bir kilidini tutuyor olabilir: kaydedici hiçbirini almaz.
        assertFalse(RepoSource.body(log, "Thread.setDefaultUncaughtExceptionHandler", "fun version(").contains("Store."))
        val file = RepoSource.text("logic/core/src/main/kotlin/com/kuryelog/logic/core/CrashFile.kt")
        assertFalse(file.contains("LogStore(") || file.contains("LogStore.") || file.contains("@Synchronized"))
    }

    @Test fun pagesAndTapsDoNotCloseTheApp() {
        val shell = RepoSource.text(app + "WeemApp.kt")
        // Her sekme kendi hata sınırında; son çökmenin kartı korumalı okunur.
        assertTrue(shell.contains("holder.SaveableStateProvider(selected.key) {\n                GuardedPage(selected.key, lang, build = {}) {"))
        assertTrue(shell.contains("Store.guarded(ctx, \"crash_card\") { CrashLog.unseen(ctx) }"))
        val guard = RepoSource.text(app + "Guarded.kt")
        assertTrue(guard.contains("remember(page, key) { attempt(\"page_\$page\", Store.sink(ctx), build) }"))
        assertTrue(guard.contains("fun Context.safely(source: String = \"tap\", block: () -> Unit) {\n    attempt(source, Store.sink(this), block)"))
        // Kart düğmeleri de korumalı.
        assertEquals(3, Regex("""onClick = \{ ctx\.safely \{""").findAll(guard).count())
        assertTrue(RepoSource.text(app + "MainActivity.kt").contains("safely(\"start\") { Store.ensureManualMode(this) }"))
        // İçe aktarma iş parçacığı: bellek hatası da yakalanır (RS 16 Eki), bayrak her durumda ana iş parçacığında iner.
        val import = RepoSource.text(core + "LogImport.kt")
        assertTrue(import.contains("} catch (e: Throwable) {"))
        assertTrue(import.contains("main.post { Store.importing = false; done(outcome) }"))
        assertTrue(RepoSource.text(app + "RecordsScreen.kt").contains("if (uri != null) ctx.safely(\"import\") {"))
    }
}
