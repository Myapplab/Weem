package com.kuryelog.logic.core

/**
 * WEEM'in tek bildirim numarası tablosu (G4, CORE.md 3.3). Her numara sabit ya da kendine ayrılmış aralık; `NotificationIdsTest`
 * çakışmayı reddeder ve kaynakta `notify(`/`cancel(` çağrısına düz sayı yazılmasını yakalar. Kanal kimlikleri eski uygulamalarla aynı.
 * Eskiden değişenler (kullanıcıya görünmez): ekran okuma uyarısı 5103 → [SCREEN_READING_OFF] 5104, nakit bahşiş 4000+ → 10000+.
 */
object NotificationIds {
    /** `:core` vardiya ön plan servisi (kanal `shift`, LOW, ongoing). */
    const val SHIFT_FGS = 1
    /** `:scooter` kayıt servisi (kanal `recording`). */
    const val SCOOTER_FGS = 7
    /** `:scooter` eşleşme uyarısı (kanal `pairing-attention`). */
    const val PAIRING = 8
    /** `:orders` teslimat notu (kanal `delivery_notes`); vardiya sonunda iptal. */
    const val DELIVERY_NOTE = 12
    /** `:orders` cepteki nakit sorusu (kanal `pocket_cash`, 10 sn). */
    const val POCKET_CASH = 4901
    /** `:scooter` scooter bağlandı (kanal `scooter_link`, 15 sn). */
    const val SCOOTER_LINK = 4902
    /** `:core` evden çıkış görüldü ama vardiya kendiliğinden başlayamadı (kanal `auto_start`, 15 dk). */
    const val HOME_AUTO_START = 5101
    /** `:scooter` scooter bağlantısı reddedildi (kanal `auto_start`, 15 dk). */
    const val SCOOTER_AUTO_START = 5102
    /** `:core` H4: Android vardiya servisini yeniden başlatmayı reddetti (kanal `auto_start`, zaman aşımı yok). */
    const val SHIFT_INTERRUPTED = 5103
    /** `:orders`/R19 ekran okuma kapalı uyarısı (kanal `screen_reading`, ongoing). Eskiden 5103. */
    const val SCREEN_READING_OFF = 5104
    /** `:map`/Y20 hatırlatıcılar: 7100 + tür sırası (kanal `hatirlatici`, 30 sn). */
    val REMINDER = 7100..7102
    /** `:map`/Y20 yakındaki görev (kanal `hatirlatici`, 30 sn). */
    const val TASK_NEARBY = 7201
    /** `:map`/Y10 hava özeti (kanal `duzyol_weather`, 30 sn, günde bir). */
    const val WEATHER = 7301
    /** `:map`/Y19 veri seti kaydı (kanal `yolgo_offer_dataset`). */
    const val DATASET_FGS = 7401
    /** Y14 yol kalibrasyonu (kanal `yolgo_calibration`). */
    const val CALIBRATION_FGS = 7435
    /** `:orders` nakit bahşiş onayı, yolculuk başına (kanal `cash_tips`, 15 sn). Eskiden 4000 + aynı ifade. */
    val CASH_TIP = 10_000..14_095

    enum class ReminderKind { WATER, CHECK, FOOD }
    fun reminder(kind: ReminderKind): Int = REMINDER.first + kind.ordinal
    fun cashTip(tripId: String): Int = CASH_TIP.first + (tripId.hashCode() and 0xFFF)

    /** Vardiya bitince iptal edilenler: aralık değil açık liste (birleşik uygulamada aralık başka bildirime denk gelebilir). */
    val SHIFT_END_CANCEL: List<Int> = listOf(DELIVERY_NOTE) + REMINDER.toList() + listOf(TASK_NEARBY, WEATHER)

    /** Tablonun bütün girdileri (ad → aralık); test ayrıklığı buradan denetler. */
    val TABLE: Map<String, IntRange> = linkedMapOf(
        "SHIFT_FGS" to SHIFT_FGS..SHIFT_FGS, "SCOOTER_FGS" to SCOOTER_FGS..SCOOTER_FGS, "PAIRING" to PAIRING..PAIRING,
        "DELIVERY_NOTE" to DELIVERY_NOTE..DELIVERY_NOTE, "POCKET_CASH" to POCKET_CASH..POCKET_CASH, "SCOOTER_LINK" to SCOOTER_LINK..SCOOTER_LINK,
        "HOME_AUTO_START" to HOME_AUTO_START..HOME_AUTO_START, "SCOOTER_AUTO_START" to SCOOTER_AUTO_START..SCOOTER_AUTO_START,
        "SHIFT_INTERRUPTED" to SHIFT_INTERRUPTED..SHIFT_INTERRUPTED, "SCREEN_READING_OFF" to SCREEN_READING_OFF..SCREEN_READING_OFF,
        "REMINDER" to REMINDER, "TASK_NEARBY" to TASK_NEARBY..TASK_NEARBY, "WEATHER" to WEATHER..WEATHER,
        "DATASET_FGS" to DATASET_FGS..DATASET_FGS, "CALIBRATION_FGS" to CALIBRATION_FGS..CALIBRATION_FGS, "CASH_TIP" to CASH_TIP,
    )

    /** Kanal kimlikleri (eski uygulamalarla aynı; kullanıcının kanal ayarları kalır). */
    const val CHANNEL_SHIFT = "shift"
    const val CHANNEL_AUTO_START = "auto_start"
}
