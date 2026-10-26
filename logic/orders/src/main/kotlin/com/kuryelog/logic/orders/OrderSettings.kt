package com.kuryelog.logic.orders

import com.kuryelog.logic.core.*
import java.util.Locale

/**
 * A4.2 (S20 (b)) — Siparişler ayarları (Rawstats `MainActivity.renderSettings` "Özel ayarlar", `askPlatformCommission`; ORDERS.md 10.3):
 * filo komisyonu platform başına, ekran tercihleri. Tercih anahtarları ve varsayılanlar Rawstats'la aynı (VERI_SOZLESMESI 5.1).
 * Saf: ekran (`:app`) yalnız okur/yazar. Kaldırılan: "YolGo paylaşımı" (S7). Boyut/genişlik seçenekleri [RestaurantBubble], [CustomerCard].
 */
object OrderSettings {
    /** Bool (true): ekran üstü panel (rozet). */
    const val OVERLAY = "overlay"
    /** Bool (true): görünür ekran OCR yedeği. */
    const val OCR = "ocr"
    /** Bool (true): müşteri kartı. */
    const val CUSTOMER_CARD = "customerCard"
    const val COMMISSION = "commission"
    const val MAX_COMMISSION = 99.99

    fun commissionKey(pkg: String) = "$COMMISSION:$pkg"

    /** `commission:<pkg>`, else the global `commission`; 0 when neither is set (RS `OrderTracker.commission`). */
    fun commissionPct(p: KuryelogPrefs.Values, pkg: String): Double {
        val v = p.getFloat(commissionKey(pkg), p.getFloat(COMMISSION, 0f))
        return if (v.isFinite()) v.toString().toDouble() else 0.0
    }

    /** "12,5" / "12.5" → 12.5; 0–99,99 dışı ya da sayı değilse null (RS `askPlatformCommission`). */
    fun parseCommission(text: String): Double? =
        text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..MAX_COMMISSION.toFloat() }?.toString()?.toDouble()

    /** Bir platformun bütün paketlerine yazılacak tercihler. */
    fun commissionWrite(pkgs: List<String>, pct: Double): Map<String, Any?> = pkgs.associate { commissionKey(it) to pct.toFloat() }

    /** Rawstats'taki gibi: izlenen paketler platform adına göre (yalnız Wolt ve Bolt Food kurye komisyonu alır). */
    fun platformPackages(targets: Set<String>): Map<String, List<String>> =
        targets.sorted().groupBy { Platform.name(it) }.filterKeys { it in OrderHistory.PLATFORMS }

    fun pctText(v: Double) = String.format(Locale.US, "%.1f", v)
}
