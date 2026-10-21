package com.kuryelog.logic.orders

import com.kuryelog.logic.core.*

// A10.4a (S20, WEEM 1.1) — Siparişler → Araçlar. Rawstats `MainActivity` Araçlar kartı: "Wolt + Bolt bölünmüş ekranı aç" (`openSplitScreen`) ve
// "<platform>: iptal gözlemi kaydet" (`OrderTracker.markCancellationObservation`) satırları (ORDERS.md 4.13, 10.3). E5: YolGo'nun "Teklif
// kazancı" düğmesi yok. Karar ve metinler burada (Android'siz test edilir); intent başlatma `:app` `SplitScreen`'dedir.

/** Bölünmüş ekranın hedefleri: iki paket ya da kullanıcıya gösterilecek açıklama ([Missing.message]). */
sealed interface SplitTargets {
    data class Ready(val wolt: String, val bolt: String) : SplitTargets
    data class Missing(val message: String) : SplitTargets
}

/** "<platform>: iptal gözlemi kaydet" satırı; [pkg] o platformun ekranda en son görülen paketi. */
data class CancellationRow(val platform: String, val pkg: String, val label: String)

class OrdersTools(private val src: OrdersSource, lang: Lang) {
    private val az = lang == Lang.AZ
    private fun tl(tr: String, az: String) = if (this.az) az else tr

    val title = tl("Araçlar", "Alətlər")
    val splitLabel = tl("Wolt + Bolt bölünmüş ekranı aç", "Wolt + Bolt üçün bölünmüş ekranı aç")
    val cancelMessage = tl("Bu yalnızca istatistik notudur. Platformda iptal işlemi yapmaz.", "Bu yalnız statistika qeydidir. Platformada ləğv əməliyyatı etmir.")
    val cancelSave = tl("Kaydet", "Yadda saxla")
    val cancelDismiss = tl("Vazgeç", "İmtina")
    val cancelSaved = tl("Gözlem kaydedildi", "Müşahidə qeyd olundu")
    val cancelFailed = tl("Gözlem kaydedilemedi. Vardiya açık olmalı.", "Müşahidə qeyd olunmadı. Növbə açıq olmalıdır.")
    val launchFailed = tl("Kurye uygulaması başlatılamadı.", "Kuryer tətbiqi açılmadı.")
    val splitHint = tl("Son uygulamalar ekranından Bölünmüş ekran seçeneğini kullan.", "Son tətbiqlər ekranından Bölünmüş ekran seçimini istifadə et.")
    val splitUnsupported = tl("Telefon bölünmüş ekranı açmadı. Son uygulamalar → uygulama simgesi → Bölünmüş ekran.", "Telefon bölünmüş ekranı açmadı. Son tətbiqlər → tətbiq ikonu → Bölünmüş ekran.")
    private val pickApps = tl("Ayarlar → Uygulamaları seç bölümünde Wolt Partner ve Bolt Food Courier'ı seç.", "Ayarlar → Tətbiqləri seç bölməsində Wolt Partner və Bolt Food Courier-i seç.")

    /** Araçlar yalnız vardiya açıkken görünür (Rawstats: iptal gözlemi vardiyasız kaydedilemez). */
    val visible get() = src.shiftOn()

    private fun targets() = src.targets().sorted()

    /** Wolt ve Bolt Food paketleri izlenenlerden; ikisi de seçili değilse açıklama. */
    fun splitTargets(): SplitTargets {
        val all = targets()
        val wolt = all.firstOrNull { it.contains("wolt", true) }
        val bolt = all.firstOrNull { Platform.name(it) == "Bolt Food" }
        return if (wolt == null || bolt == null) SplitTargets.Missing(pickApps) else SplitTargets.Ready(wolt, bolt)
    }

    /** İzlenen her platform için bir satır (Rawstats `platforms()`: ada göre sıralı); paket = ekranda en son görülen. */
    fun cancellations(): List<CancellationRow> = targets().groupBy { Platform.name(it) }.map { (name, pkgs) ->
        val pkg = pkgs.maxByOrNull { src.lastSeen(it) } ?: pkgs.first()
        CancellationRow(name, pkg, tl("$name: iptal gözlemi kaydet", "$name: ləğv müşahidəsini qeyd et"))
    }

    /** Onaydan sonra: gözlemi yazar; dönen metin toast olarak gösterilir. */
    fun markCancellation(pkg: String): String = if (src.markCancellationObservation(pkg)) cancelSaved else cancelFailed
}
