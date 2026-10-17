package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.CourierParser
import com.kuryelog.logic.capture.DeliveryDetails
import com.kuryelog.logic.capture.RepoSource
import com.kuryelog.logic.core.BackupExport
import com.kuryelog.logic.core.BackupFiles
import com.kuryelog.logic.core.DailyPack
import com.kuryelog.logic.core.LogImporter
import com.kuryelog.logic.core.LogTime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * A4.3 (R17, R18): WEEM'in iki ZIP'i Rawstats'ın analiz aracıyla okunur ve yalnız tam yedek geri yüklenir.
 * - Tam yedek ([BackupExport]) ve anonim analiz ([AnalysisExport], günlük paket de bu) `tools/analysis/kl_export.py` (Rawstats @ 6388c04)
 *   ile açılır: aynı günler, dosyalar, siparişler; anonimlik işareti yalnız analiz ZIP'inde. `python3` yoksa bu test atlanır (CI'da var).
 * - Anonim ZIP [LogImporter]'a verilirse reddedilir, kayıtlar değişmez (R_economics §R17 "doğrulanmadı" notu artık testli).
 */
class AnalysisToolsTest {
    @get:Rule val temp = TemporaryFolder()
    private val baku = ZoneId.of("Asia/Baku")
    private val day = "2026-10-06"
    private val t0 = 1_791_273_600_000L // 2026-10-06 12:00 Bakü
    private val wolt = "com.wolt.courierapp"

    private val offerObs = CourierParser.parse(wolt, listOf("Ready in 3 min", "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Izmir Doner Tantuni No1",
        "9RQW+344 Baku, Baku, AZ1000", "Customer drop-off", "Mirzəağa Əliyev 249, Bakı, 1014", "Accept"), listOf("Accept", "Decline"), t0)
    private val offer = OfferState("offer-$t0-1", wolt, t0, offerObs, 64, 40.3779871, 49.8462345, 12.5, 3200L).also { it.sessionId = "wolt-$t0" }
    private val trip = TripRecord("trip-${t0 + 12_000}-2", wolt, offer, t0 + 12_000, TripPhase.FINISHED, completedAt = t0 + 2_400_000, distanceM = 5321.7,
        tip = 1.0, commissionPct = 20.0).also { t ->
        t.sessionId = "wolt-$t0"
        t.pickups.add(PickupRecord("Izmir Doner Tantuni No1", "9RQW+344 Baku, Baku, AZ1000", t0 + 180_000, t0 + 600_000, t0 + 900_000, "screen"))
        t.customers["name:aysel testova"] = CustomerRecord("name:aysel testova", DeliveryDetails(name = "Aysel Testova", address = "Sintetik kuce 81, Bakı",
            phone = "0551234567"), deliveredAt = t0 + 2_390_000, lat = 40.4093123, lon = 49.8671456)
    }
    private fun ts(at: Long) = LogTime.ts(at, baku)
    private fun row(type: String, at: Long, withTrip: Boolean): JSONObject = JSONObject().put("t", ts(at)).put("type", type).put("pkg", wolt)
        .put("offer_id", offer.id).put("session_id", offer.sessionId).put("offer", RecordJson.offer(offer))
        .also { j -> if (withTrip) j.put("id", trip.id).put("record", RecordJson.trip(trip)).put("gross", trip.gross).put("net", trip.net).put("km", trip.distanceM / 1000) }
        .put("loc", JSONObject().put("lat", 40.3951234).put("lon", 49.8523456).put("acc", 7.5).put("age_s", 2))

    /** A phone with one day of records, as the app writes them. */
    private fun phone(): Pair<File, File> {
        val files = temp.newFolder("files"); val logs = File(files, "logs/$day").apply { mkdirs() }.parentFile
        File(logs, "$day/orders.jsonl").writeText(listOf(row("offer", t0, false), row("accepted", t0 + 12_000, true), row("delivered", t0 + 2_400_000, true))
            .joinToString("\n", postfix = "\n"))
        File(logs, "$day/gps.csv").writeText("${LogImporter.GPS_HEADER}\n${ts(t0)},40.40931234,49.86714561,4.5,3.2,182.0,12.0\n")
        File(logs, "$day/events.jsonl").writeText(JSONObject().put("t", ts(t0 - 600_000)).put("type", "shift_start").put("auto", false).toString() + "\n")
        File(logs, "$day/economics-cash-tips.jsonl").writeText("{\"schemaVersion\":1,\"trip_id\":\"${trip.id}\",\"received_azn\":10.0}\n")
        File(files, "restaurants.json").writeText(JSONObject().put("izmir doner tantuni no1", JSONObject().put("n", 3).put("name", "Izmir Doner Tantuni No1")).toString())
        return files to logs
    }

    private fun zips(): Pair<File, File> {
        val (files, logs) = phone()
        val staging = temp.newFolder("staging")
        val entries = BackupExport.stage(logs, files, null, null, staging)
        val full = File(temp.root, BackupExport.fileName(null, null, t0, baku)).also { f -> f.outputStream().use { BackupExport.zip(entries, it) } }
        // The daily pack is this same anonymous ZIP of one day (DailyPack.stage + AnalysisExport.zip).
        val daily = DailyPack.stage(logs, files, day, temp.newFolder("daily"))!!
        val anon = File(temp.root, DailyPack.fileName(day)).also { f -> f.outputStream().use { AnalysisExport().zip(daily, it, "1.0", t0 + 4_000_000) } }
        return full to anon
    }

    private fun python(): String? = runCatching {
        val p = ProcessBuilder("python3", "--version").redirectErrorStream(true).start()
        if (p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0) "python3" else null
    }.getOrNull()

    /** `kl_export.load` + `trips` + `offers`, the way `tools/analysis/run.py` reads an input. */
    private fun read(py: String, zip: File): JSONObject {
        val script = """
            import json, sys
            sys.dont_write_bytecode = True
            sys.path.insert(0, sys.argv[1])
            import kl_export as K
            exp = K.load(sys.argv[2])
            ts = K.trips(exp)
            print(json.dumps({"anonymous": exp.anonymous, "days": exp.days, "names": exp.names(), "ignored": exp.ignored,
                "stats": dict(exp.stats), "restaurants": "restaurants.json" in exp.root_files,
                "trips": [{"id": t["id"], "gross": t["gross"], "net": t["net"], "km": t["km"], "phase": t["phase"]} for t in ts],
                "offers": [o["id"] for o in K.offers(exp, trip_list=ts)]}))
        """.trimIndent()
        val p = ProcessBuilder(py, "-I", "-B", "-c", script, RepoSource.file("tools/analysis/kl_export.py").parent, zip.absolutePath).start()
        val out = p.inputStream.bufferedReader().readText(); val err = p.errorStream.bufferedReader().readText()
        assertTrue("python timeout", p.waitFor(120, TimeUnit.SECONDS))
        assertEquals(err, 0, p.exitValue())
        return JSONObject(out.trim().lines().last())
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    @Test fun rawstatsAnalysisToolsReadBothWeemZips() {
        val py = python()
        assumeTrue("python3 yok: kl_export denetimi atlandı", py != null)
        val (full, anon) = zips()
        val a = read(py!!, full); val b = read(py, anon)
        assertFalse(a.getBoolean("anonymous")); assertTrue(b.getBoolean("anonymous"))
        for (r in listOf(a, b)) {
            assertEquals(listOf(day), r.getJSONArray("days").strings())
            assertEquals(listOf("economics-cash-tips.jsonl", "events.jsonl", "gps.csv", "orders.jsonl"), r.getJSONArray("names").strings())
            assertEquals(r.toString(), 0, r.getJSONArray("ignored").length())
            assertEquals(r.toString(), 0, r.getJSONObject("stats").length())
            assertTrue(r.getBoolean("restaurants"))
            assertEquals(listOf(offer.id), r.getJSONArray("offers").strings())
            val t = r.getJSONArray("trips").getJSONObject(0)
            assertEquals(1, r.getJSONArray("trips").length())
            assertEquals(trip.id, t.getString("id")); assertEquals("FINISHED", t.getString("phase"))
            assertEquals(2.31, t.getDouble("gross"), 1e-9); assertEquals(2.31 * 0.8 + 1.0, t.getDouble("net"), 1e-9); assertEquals(5.3217, t.getDouble("km"), 1e-9)
        }
    }

    @Test fun theAnonymousZipIsNotARestorableBackup() {
        val (_, anon) = zips()
        val names = ArrayList<String>()
        ZipInputStream(anon.inputStream()).use { z -> while (true) { names += (z.nextEntry ?: break).name } }
        assertEquals(AnalysisExport.MARKER, names.last())
        assertFalse(AnalysisExport.MARKER in BackupFiles.logNames)
        val files = temp.newFolder("other"); val logs = File(files, "logs").apply { mkdirs() }
        File(logs, "$day").mkdirs(); File(logs, "$day/orders.jsonl").writeText("{\"id\":\"x\"}\n")
        assertThrows(IllegalArgumentException::class.java) { LogImporter.restore(anon.inputStream(), files, logs, temp.newFolder("cache")) }
        assertEquals("{\"id\":\"x\"}\n", File(logs, "$day/orders.jsonl").readText())
        assertEquals(listOf("$day/orders.jsonl"), logs.walkTopDown().filter { it.isFile }.map { it.relativeTo(logs).invariantSeparatorsPath }.toList())
    }

    @Test fun everyDayFileHasAnAnonymousExportRule() {
        // WEEM guard: a new day file joins BackupFiles.logNames (DailyRecordsTest) and must also be known to the anonymous export
        // (AnalysisExport.kindOf), else the daily pack and "Analiz için paylaş" silently leave it out.
        for (name in BackupFiles.logNames) assertNotNull(name, AnalysisExport.kindOf("$day/$name"))
        assertEquals(AnalysisExport.Kind.RESTAURANTS, AnalysisExport.kindOf("restaurants.json"))
    }
}
