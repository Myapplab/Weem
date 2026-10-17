package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Rawstats `CrashGuardTest` (16 Eki: "uygulama açılır açılmaz kapanıyor", telefonda neden yazmıyordu) — A2.3'ün iki testi WEEM kaynağını
 * tarar (CORE.md 6.1): `everyProcessStartInstallsTheCrashRecorderFirst`, `pagesAndTapsDoNotCloseTheApp`; A2.4'ünkiler vardiya servisini
 * (adımların sırası ve korumaları `ShiftMachineTest`'te davranış olarak). Ekran okumanınkiler A3'te. `guarded`'ın kendisi `GuardTest`'te.
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
        assertTrue(RepoSource.text(app + "MainActivity.kt").contains("safely(\"start\") { Store.ensureManualMode(this)"))
        // İçe aktarma iş parçacığı: bellek hatası da yakalanır (RS 16 Eki), bayrak her durumda ana iş parçacığında iner.
        val import = RepoSource.text(core + "LogImport.kt")
        assertTrue(import.contains("} catch (e: Throwable) {"))
        assertTrue(import.contains("main.post { Store.importing = false; done(outcome) }"))
        // RS `safely("import") { OrderTracker.reloadImportedStats(this) }`: the orders' reload after an import is guarded too.
        assertTrue(import.contains("afterImport?.let { hook -> Store.guarded(app, \"import\") { hook(app) } }"))
        assertTrue(RepoSource.text(app + "RecordsScreen.kt").contains("if (uri != null) ctx.safely(\"import\") {"))
    }

    @Test fun gpsServiceStartFixAndStopAreGuarded() {
        val service = RepoSource.text(core + "ShiftService.kt")
        // Every fix goes through the machine's guard (ShiftMachine.onLocation → guarded("gps")); the service decides nothing.
        assertTrue(service.contains("override fun onLocationChanged(loc: Location) = machine.onLocation(Shift.fix(loc))"))
        assertEquals(1, Regex("""machine\.onStartCommand\(""").findAll(service).count())
        val machine = RepoSource.text("logic/core/src/main/kotlin/com/kuryelog/logic/core/ShiftMachine.kt")
        assertTrue(machine.contains("fun onLocation(fix: GpsFix) { guarded(\"gps\", log) { onFix(fix) } }"))
        for (step in listOf("hooks.onNewShift(now, reason)", "hooks.onRunning()", "hooks.onEnd(start, clock.wallMs(), reason)", "hooks.onFix(f, speed, now)"))
            assertTrue(step, Regex("""guarded\("[a-z_]+", log\) \{ ${Regex.escape(step)} \}""").containsMatchIn(machine))
        // The stop always reaches "shift off": every step before it is guarded.
        val stop = RepoSource.body(machine, "fun stop(reason: String) {", "/** Tercihlerden")
        assertTrue(stop.indexOf("KuryelogPrefs.SHIFT to false") > stop.indexOf("hooks.onEnd("))
        val watch = RepoSource.text(core + "HomeWatch.kt")
        assertTrue(watch.contains("override fun onLocationChanged(loc: Location) {\n            Store.guarded(ctx, \"home_watch\") {"))
    }

    /** Android closes the whole app when a service started with startForegroundService stops before startForeground. */
    @Test fun refusedForegroundStartStillReachesStartForeground() {
        val machine = RepoSource.text("logic/core/src/main/kotlin/com/kuryelog/logic/core/ShiftMachine.kt")
        val refuse = RepoSource.body(machine, "private fun refuse(", "private fun requestGps(")
        assertTrue(refuse.contains("if (action in FOREGROUND_ACTIONS) runCatching { platform.startForeground(); platform.stopForeground() }"))
        assertTrue(machine.contains("val FOREGROUND_ACTIONS = setOf(ACTION_START, ACTION_HOME_START)"))
        // Every foreground start of the shift service in WEEM uses one of those actions.
        var calls = 0
        for (f in RepoSource.mainKotlin()) {
            val code = f.readText()
            Regex("""(startForegroundService|getForegroundService)\(""").findAll(code).forEach { m ->
                val line = code.substring(code.lastIndexOf('\n', m.range.first) + 1).trimStart()
                if (line.startsWith("*") || line.startsWith("/*") || line.startsWith("//")) return@forEach
                val call = code.substring(m.range.first, minOf(code.length, m.range.first + 200))
                calls++
                assertTrue("${f.name}: $call", call.contains("ACTION_START") || call.contains("Home.startIntent("))
            }
        }
        assertTrue(calls >= 4)
        assertTrue(RepoSource.text(core + "Home.kt").contains("Intent(ctx, ShiftService::class.java).setAction(ShiftMachine.ACTION_HOME_START)"))
    }
}
