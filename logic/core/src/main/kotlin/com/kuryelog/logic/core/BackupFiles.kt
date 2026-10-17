package com.kuryelog.logic.core

import java.time.LocalDate

/** Gün klasöründeki tanınan kayıt dosyaları ve yedek/içe aktarma yolu denetimi (CORE.md 5.3). */
object BackupFiles {
    /** Uygulamanın gün klasörüne yazdığı her dosya. Burada olmayan dosya dışa/içe aktarmada ve gün silmede sessizce yok sayılır. */
    val logNames = setOf("screens.jsonl", "notifs.jsonl", "events.jsonl", "orders.jsonl", "gps.csv", "scooter.jsonl", "scooter_events.jsonl", "scooter_averages.jsonl", "platforms.jsonl", "errors.jsonl",
        "economics-waits.jsonl", "economics-exposure.jsonl", "online-sessions.jsonl",
        // SPEC 2.1.0 section 17.2 (EconomicsLogs.NEW_FILES)
        "economics-offers.jsonl", "economics-legs.jsonl", "economics-idle.jsonl", "economics-penalties.jsonl",
        // K36 platform earnings lists (PlatformHistory.FILE)
        "economics-platform-history.jsonl",
        // F8.3 the platforms' own totals (PlatformTotals.FILE)
        "economics-platform-totals.jsonl",
        // K32 cash received per cash order (CashTips.FILE)
        "economics-cash-tips.jsonl",
        // M15 cash in the pocket at the shift start (PocketCash.FILE)
        "economics-pocket-cash.jsonl",
        // M17 Wolt Finances cash pending, offloads, shift start/end values and order snapshots (CashPending.FILE)
        "economics-cash-pending.jsonl",
        // K27 boost changes and Bolt 30-minute slots (BoostLog.FILE)
        "economics-boosts.jsonl",
        // energy schema rows per leg / idle spell (EnergySegments.FILE)
        "economics-energy.jsonl",
        // 1 Hz scooter values of a shift from the merged Sürüş Paneli (ScooterSamples.FILE)
        "economics-scooter.jsonl",
        // E4 "Teklif puanı": the app's 1–10 per offer (OfferScore.FILE) and the courier's own (OfferScore.RATINGS)
        "economics-offer-scores.jsonl", "economics-offer-ratings.jsonl")
    /** Yalnız `restaurants.json` ya da `YYYY-MM-DD/<logNames>`; başka ad `IllegalArgumentException`. */
    fun checkedPath(name: String): String {
        require(!name.contains('\\') && !name.startsWith('/')) { "Invalid backup path" }
        if (name == "restaurants.json") return name
        val parts = name.split('/')
        require(parts.size == 2 && parts[1] in logNames) { "Invalid backup entry" }
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(parts[0])) { "Invalid date" }
        LocalDate.parse(parts[0])
        return name
    }

    /** Aynı dışa aktarmayı yeniden içe aktarmak sipariş/GPS istatistiğini ikiye katlamaz. */
    fun mergeLines(existing: String, incoming: String): String {
        val lines = LinkedHashSet<String>()
        existing.lineSequence().filter { it.isNotBlank() }.forEach { lines.add(it) }
        incoming.lineSequence().filter { it.isNotBlank() }.forEach { lines.add(it) }
        return if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n")
    }
}
