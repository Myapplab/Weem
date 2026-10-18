package com.kuryelog.logic.economics

import com.kuryelog.logic.orders.*
import java.util.Locale

// A4.2 — gün sonu raporunun metni (Siparişler "Bugün" ve gün ayrıntısı kartı, vardiya sonu bildirimi). Saf: Android'siz test edilir.

object DayReportCard {
    private fun f1(n: Double) = String.format(Locale.US, "%.1f", n)
    private fun f2(n: Double) = String.format(Locale.US, "%.2f", n)
    private fun wh(n: Double) = Math.round(n).toString() + " Wh"

    /**
     * Rapor kartı: büyük net, brüt / bahşiş / komisyon / enerji karoları, enerji ve komisyon notları, hedef satırı.
     * [today] true = gün sürüyor (başlık "şimdiye dek"). Ayarlar yolu notu: "Daha fazla → Siparişler ayarları".
     */
    fun card(r: DayReport, today: Boolean, tl: (String, String) -> String): Card {
        val b = mutableListOf<Block>(
            Block.Line(if (today) tl("Günün özeti (şimdiye dek)", "Günün xülasəsi (indiyədək)") else tl("Gün sonu raporu", "Gün sonu hesabatı"), Style.TITLE),
            Block.Line(tl("Net kazanç (enerji maliyeti düşülmüş)", "Xalis qazanc (enerji xərci çıxılmış)"), Style.MUTED),
            Block.Big(f2(r.net) + " ₼"),
            Block.Tiles(listOf(Tile(f2(r.gross) + "₼", tl("brüt", "ümumi")), Tile("+" + f2(r.tips) + "₼", tl("bahşiş", "çaypulu")))),
            Block.Tiles(listOf(Tile("−" + f2(r.commission) + "₼", tl("komisyon", "komissiya")), Tile("−" + f2(r.energyCost) + "₼", tl("enerji maliyeti", "enerji xərci")))),
        )
        b += Block.Line(energyNote(r, tl), Style.MUTED)
        if (r.commissionMissing) b += Block.Line(tl("Filo komisyonu girilmemiş: komisyon 0 sayıldı, net = brüt + bahşiş (Daha fazla → Siparişler ayarları).",
            "Filo komissiyası daxil edilməyib: komissiya 0 sayıldı, xalis = ümumi + çaypulu (Daha çox → Sifariş ayarları)."), Style.MUTED)
        if (r.unpriced > 0) b += Block.Line(tl("Fiyatı okunamayan ${r.unpriced} sipariş brüt ve net toplamına eklenmedi.",
            "Qiyməti oxunmayan ${r.unpriced} sifariş ümumi və xalis cəmə əlavə edilmədi."), Style.MUTED)
        goalLine(r, tl)?.let { b += Block.Divider; b += it }
        return Card(b, hero = false, outlined = true)
    }

    /** Enerji satırı: neden bu maliyet (ölçülmediyse ya da fiyat yoksa açıkça yazar). */
    fun energyNote(r: DayReport, tl: (String, String) -> String): String = when (r.energyState) {
        EnergyState.NOT_MEASURED -> tl("Enerji ölçülmedi (scooter bağlı değildi ya da okunamadı): enerji maliyeti hesaba katılmadı.",
            "Enerji ölçülmədi (skuter qoşulmamışdı və ya oxunmadı): enerji xərci hesaba alınmadı.")
        EnergyState.NO_PRICE -> tl("Enerji ${wh(r.energyWh)} ölçüldü ama kWh fiyatı girilmediği için maliyet hesaba katılmadı (Daha fazla → Siparişler ayarları).",
            "Enerji ${wh(r.energyWh)} ölçüldü, amma kWh qiyməti daxil edilmədiyi üçün xərc hesaba alınmadı (Daha çox → Sifariş ayarları).")
        EnergyState.COSTED -> tl(
            "Enerji ${wh(r.energyWh)} (siparişler ${wh(r.orderWh)}, boşta ${wh(r.idleWh)}) × ${f2(r.prices.kwhPrice ?: 0.0)} ₼/kWh ÷ %${f1(r.prices.chargeEfficiencyPct)} şarj verimi; " +
                "${r.measuredTrips}/${r.deliveredTrips} siparişte ölçüldü.",
            "Enerji ${wh(r.energyWh)} (sifarişlər ${wh(r.orderWh)}, boşda ${wh(r.idleWh)}) × ${f2(r.prices.kwhPrice ?: 0.0)} ₼/kWh ÷ %${f1(r.prices.chargeEfficiencyPct)} şarj səmərəsi; " +
                "${r.measuredTrips}/${r.deliveredTrips} sifarişdə ölçüldü.")
    }

    /** Hedef (N8) satırı; hedef girilmemişse null. */
    fun goalLine(r: DayReport, tl: (String, String) -> String): Block? {
        val goal = r.goal ?: return null
        val text = if (r.goalReached)
            tl("Günlük hedef ${f2(goal)}₼ · net ${f2(r.net)}₼ · hedefe ulaşıldı (+${f2(r.net - goal)}₼)",
                "Günlük hədəf ${f2(goal)}₼ · xalis ${f2(r.net)}₼ · hədəfə çatıldı (+${f2(r.net - goal)}₼)")
        else
            tl("Günlük hedef ${f2(goal)}₼ · net ${f2(r.net)}₼ (%${r.goalPct}) · hedefe kalan ${f2(r.remaining ?: goal)}₼",
                "Günlük hədəf ${f2(goal)}₼ · xalis ${f2(r.net)}₼ (%${r.goalPct}) · hədəfə qalan ${f2(r.remaining ?: goal)}₼")
        return Block.Line(text, if (r.goalReached) Style.ACCENT_BOLD else Style.BOLD)
    }

    /** Vardiya sonu bildiriminin başlığı ve gövdesi. */
    fun notification(r: DayReport, tl: (String, String) -> String): Pair<String, String> {
        val title = tl("Gün sonu raporu · net ${f2(r.net)}₼", "Gün sonu hesabatı · xalis ${f2(r.net)}₼")
        val body = StringBuilder(tl("Brüt ${f2(r.gross)} + bahşiş ${f2(r.tips)} − komisyon ${f2(r.commission)} − enerji ${f2(r.energyCost)}",
            "Ümumi ${f2(r.gross)} + çaypulu ${f2(r.tips)} − komissiya ${f2(r.commission)} − enerji ${f2(r.energyCost)}"))
        when (r.energyState) {
            EnergyState.NOT_MEASURED -> body.append("\n").append(tl("Enerji ölçülmedi: maliyet hesaba katılmadı.", "Enerji ölçülmədi: xərc hesaba alınmadı."))
            EnergyState.NO_PRICE -> body.append("\n").append(tl("kWh fiyatı girilmedi: enerji maliyeti hesaba katılmadı.", "kWh qiyməti daxil edilməyib: enerji xərci hesaba alınmadı."))
            EnergyState.COSTED -> {}
        }
        goalLine(r, tl)?.let { body.append("\n").append((it as Block.Line).text) }
        return title to body.toString()
    }
}
